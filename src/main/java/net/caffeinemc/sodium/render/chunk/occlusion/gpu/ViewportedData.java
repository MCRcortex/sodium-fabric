package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.gfx.util.buffer.streaming.DualStreamingBuffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.chunk.ViewportInstancedData;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.SceneStruct;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.util.MathUtil;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedBufferData;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;

public class ViewportedData {
    public static final ViewportInstancedData<ViewportedData> DATA = new ViewportInstancedData<>(ViewportedData::new);

    private final RenderDevice device;
    public final ObjectAVLTreeSet<RenderRegion> visible_regions = new ObjectAVLTreeSet<>(Comparator.comparingDouble(a -> a.regionSortDistance));

    public final SceneStruct scene = new SceneStruct();
    //TODO: replace with streaming buffer thing
    public final DualStreamingBuffer sceneBuffer;
    public static int SCENE_STRUCT_ALIGNMENT;
    public static int FRUSTUM_REGION_ALIGNMENT;
    public int sceneOffset;

    public final DualStreamingBuffer frustumRegionArray;
    public long frustumRegionOffset;

    //NOTE: this is different from regionVisibilityArray, visibleRegionArray is the output/culled list of visible regions
    //while regionVisibilityArray is the marker array for rastering the regions
    public final Buffer visibleRegionArray;

    public final Buffer regionVisibilityArray;

    public final Buffer sectionCommandBuffer;

    public final Buffer computeDispatchCommandBuffer;

    public final Buffer sectionVisibilityBuffer;



    public final Buffer commandBufferCounter;
    public final MappedBuffer cpuCommandBufferCounter;

    public final Buffer chunkInstancedDataBuffer;
    public final Buffer commandOutputBuffer;

    public boolean isRenderingTemporal;
    public final Buffer temporalSectionData;

    public final Buffer translucencyCountBuffer;
    public final MappedBuffer cpuTranslucencyCountBuffer;
    public final Buffer translucencyCommandBuffer;

    public ChunkRenderMatrices renderMatrices;

    public double currentCameraX;
    public double currentCameraY;
    public double currentCameraZ;

    public double frameDeltaX;
    public double frameDeltaY;
    public double frameDeltaZ;
    public float countMultiplier;


    //TODO: FIGURE OUT A MORE compact and efficent WAY TO DO sectionVisibilityBuffer rather than having
    // every region have its own visibility buffer cause this wastes alot of vram

    //scene, regionArray, regionVisibilityArray, sectionCommandBuff, sectionVisibilityBuff
    //NOTE: could merge regionArray and regionVisibilityArray with a bit or
    //regionLUT?
    public ViewportedData(int viewport) {
        this.device = SodiumClientMod.DEVICE;

        int uboAlignment = device.properties().values.uniformBufferOffsetAlignment;
        int ssboAlignment = device.properties().values.storageBufferOffsetAlignment;
        int maxInFlightFrames = SodiumClientMod.options().advanced.cpuRenderAheadLimit + 1;
        SCENE_STRUCT_ALIGNMENT = MathUtil.align(SceneStruct.SIZE, uboAlignment);
        FRUSTUM_REGION_ALIGNMENT = MathUtil.align(4*OcclusionEngine.MAX_REGIONS, ssboAlignment);
        /*
        sceneBuffer = device.createMappedBuffer((long) SCENE_STRUCT_ALIGNMENT * maxInFlightFrames,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));

        //TODO: Instead of just putting in a random "max region in frustum" count, calculate it based on render distance
        frustumRegionArray = device.createMappedBuffer((long) FRUSTUM_REGION_ALIGNMENT * maxInFlightFrames,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH)
        );*/
        sceneBuffer = new DualStreamingBuffer(
                device,
                uboAlignment,
                SCENE_STRUCT_ALIGNMENT,
                maxInFlightFrames,
                EnumSet.of(MappedBufferFlags.EXPLICIT_FLUSH)
        );
        frustumRegionArray = new DualStreamingBuffer(
                device,
                ssboAlignment,
                FRUSTUM_REGION_ALIGNMENT,
                maxInFlightFrames,
                EnumSet.of(MappedBufferFlags.EXPLICIT_FLUSH)
        );


        regionVisibilityArray = device.createBuffer(4*OcclusionEngine.MAX_REGIONS,
                Set.of()
        );
        sectionCommandBuffer = device.createBuffer(OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE*OcclusionEngine.MAX_REGIONS, Set.of());
        computeDispatchCommandBuffer = device.createBuffer(4*3, Set.of());
        visibleRegionArray = device.createBuffer(4*OcclusionEngine.MAX_REGIONS,Set.of());
        sectionVisibilityBuffer = device.createBuffer(4*OcclusionEngine.MAX_REGIONS*RenderRegion.REGION_SIZE,Set.of());

        //TODO: will need to change this when doing the system that renders with distinct region buffers
        //(normal render layers, then temporal layers)
        commandBufferCounter = device.createBuffer(8*4,Set.of());
        cpuCommandBufferCounter = device.createMappedBuffer(8*4,Set.of(MappedBufferFlags.READ, MappedBufferFlags.CLIENT_STORAGE));//

        chunkInstancedDataBuffer = device.createBuffer(OcclusionEngine.MAX_VISIBLE_SECTIONS*(4*4),Set.of());

        //TODO: properly calculate commandOutputBuffer size
        commandOutputBuffer = device.createBuffer((OcclusionEngine.MAX_RENDER_COMMANDS_PER_LAYER+OcclusionEngine.MAX_TEMPORAL_COMMANDS_PER_LAYER)*OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE*3,Set.of());//3 layers

        //NOTE: can probably just use a slot in SectionMeta or some shiz
        //TODO:FIXME: THIS IS NOT CORRECT this needs to be  the size of max regions in the world * max number of sections in regions
        temporalSectionData = device.createBuffer(OcclusionEngine.MAX_REGIONS*OcclusionEngine.MAX_VISIBLE_SECTIONS*4,Set.of());//3 layers


        //TODO: make these auto size
        translucencyCountBuffer = device.createBuffer(80*4, Set.of());
        cpuTranslucencyCountBuffer = device.createMappedBuffer(80*4, Set.of(MappedBufferFlags.READ, MappedBufferFlags.CLIENT_STORAGE));
        translucencyCommandBuffer = device.createBuffer(5000*OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE, Set.of());




        clearBuff(visibleRegionArray);
        clearBuff(regionVisibilityArray);
        clearBuff(sectionCommandBuffer);
        clearBuff(computeDispatchCommandBuffer);
        clearBuff(sectionVisibilityBuffer);
        clearBuff(commandBufferCounter);
        clearBuff(cpuCommandBufferCounter);
        clearBuff(chunkInstancedDataBuffer);
        clearBuff(commandOutputBuffer);
        clearBuff(temporalSectionData);
        clearBuff(translucencyCountBuffer);
        clearBuff(cpuTranslucencyCountBuffer);
        clearBuff(translucencyCommandBuffer);
    }

    private static void clearBuff(Buffer buffer) {
        glClearNamedBufferData(GlBuffer.getHandle(buffer),
                GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{0});
    }
}
