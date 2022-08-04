package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.chunk.ViewportInstancedData;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.SceneStruct;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class ViewportedData {
    public static final ViewportInstancedData<ViewportedData> DATA = new ViewportInstancedData<>(ViewportedData::new);

    private final RenderDevice device;
    public final ObjectAVLTreeSet<RenderRegion> visible_regions = new ObjectAVLTreeSet<>(Comparator.comparingDouble(a -> a.regionSortDistance));

    public final SceneStruct scene = new SceneStruct();
    public final MappedBuffer sceneBuffer;

    public final MappedBuffer frustumRegionArray;

    //NOTE: this is different from regionVisibilityArray, visibleRegionArray is the output/culled list of visible regions
    //while regionVisibilityArray is the marker array for rastering the regions
    public final Buffer visibleRegionArray;

    public final Buffer regionVisibilityArray;

    public final Buffer sectionCommandBuffer;

    public final Buffer computeDispatchCommandBuffer;

    public final Buffer sectionVisibilityBuffer;

    //TODO: FIGURE OUT A MORE compact and efficent WAY TO DO sectionVisibilityBuffer rather than having
    // every region have its own visibility buffer cause this wastes alot of vram

    //scene, regionArray, regionVisibilityArray, sectionCommandBuff, sectionVisibilityBuff
    //NOTE: could merge regionArray and regionVisibilityArray with a bit or
    //regionLUT?
    public ViewportedData(int viewport) {
        this.device = SodiumClientMod.DEVICE;
        sceneBuffer = device.createMappedBuffer(SceneStruct.SIZE,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        //TODO: Instead of just putting in a random "max region in frustum" count, calculate it based on render distance
        frustumRegionArray = device.createMappedBuffer(4*OcclusionEngine.MAX_REGIONS,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH)
        );
        regionVisibilityArray = device.createBuffer(4*OcclusionEngine.MAX_REGIONS,
                Set.of()
        );
        sectionCommandBuffer = device.createBuffer(OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE*OcclusionEngine.MAX_REGIONS, Set.of());
        computeDispatchCommandBuffer = device.createBuffer(4*3, Set.of());
        visibleRegionArray = device.createBuffer(4*OcclusionEngine.MAX_REGIONS,Set.of());
        sectionVisibilityBuffer = device.createBuffer(4*OcclusionEngine.MAX_REGIONS*RenderRegion.REGION_SIZE,Set.of());
    }
}
