package net.caffeinemc.sodium.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static net.caffeinemc.sodium.render.chunk.region.RenderRegionInstancedRenderData.preSetupCommandBuffer;
import static net.caffeinemc.sodium.render.chunk.region.RenderRegionInstancedRenderData.set0Buffer;

public class ViewportedData {
    private static final int MAX_VISIBLE_REGIONS = 500;
    public final MappedBuffer visibleRegionIds;

    public final MappedBuffer cpuCommandCount;
    public final ImmutableBuffer counterBuffer;

    public final ImmutableBuffer cmd0buff;//just for testing will be moved
    public final ImmutableBuffer cmd1buff;//just for testing will be moved
    public final ImmutableBuffer cmd2buff;//just for testing will be moved

    public final ImmutableBuffer instanceBuffer;//just for testing will be moved

    private ViewportedData(RenderDevice device) {
        this.counterBuffer = device.createBuffer(5*4, Set.of());
        this.cpuCommandCount = device.createMappedBuffer(5*4, Set.of(MappedBufferFlags.READ));//, MappedBufferFlags.CLIENT_STORAGE

        this.visibleRegionIds = device.createMappedBuffer(MAX_VISIBLE_REGIONS*(96), Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));//, MappedBufferFlags.CLIENT_STORAGE

        this.cmd0buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6*30), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd1buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6*30), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd2buff = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6*30), Set.of());//FIXME: TUNE BUFFER SIZE

        this.instanceBuffer = device.createBuffer(ByteBuffer.allocateDirect(RenderRegion.REGION_SIZE*5*4*6*30), Set.of());//FIXME: TUNE BUFFER SIZE

        set0Buffer(counterBuffer);
        set0Buffer(cpuCommandCount);
        set0Buffer(visibleRegionIds);
        set0Buffer(instanceBuffer);
        preSetupCommandBuffer(cmd0buff, RenderRegion.REGION_SIZE*30);
        preSetupCommandBuffer(cmd1buff, RenderRegion.REGION_SIZE*30);
        preSetupCommandBuffer(cmd2buff, RenderRegion.REGION_SIZE*30);
    }

    public Set<RenderRegion> visible_regions = new TreeSet<>(Comparator.comparingDouble(a->a.weight));
    public long cameraRenderRegion;
    public int cameraRenderRegionInner;
    public double lastCameraX, lastCameraY, lastCameraZ;
    public double cameraX, cameraY, cameraZ;
    public Frustum frustum;

    private static int lastViewIndex = -1;
    private static ViewportedData cdat;
    private static final Int2ObjectOpenHashMap<ViewportedData> viewports = new Int2ObjectOpenHashMap<>(2);
    public static ViewportedData get() {
        if (lastViewIndex != ViewportInterface.CURRENT_VIEWPORT) {
            cdat = viewports.computeIfAbsent(ViewportInterface.CURRENT_VIEWPORT, k->new ViewportedData(SodiumClientMod.DEVICE));
            lastViewIndex = ViewportInterface.CURRENT_VIEWPORT;
        }
        return cdat;
    }
}
