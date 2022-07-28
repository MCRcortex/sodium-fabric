package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.chunk.ViewportInstancedData;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.TreeSet;

public class ViewportedData {
    public static final ViewportInstancedData<ViewportedData> DATA = new ViewportInstancedData<>(ViewportedData::new);

    private final RenderDevice device;
    public final TreeSet<RenderRegion> visible_regions = new TreeSet<>();

    public final Buffer scene;
    public final Buffer frustumRegionArray;

    public final Buffer visibleRegionArray;

    public final Buffer regionVisibilityArray;

    public final Buffer sectionCommandBuffer;

    public final Buffer computeDispatchCommandBuffer;

    //TODO: FIGURE OUT A MORE compact and efficent WAY TO DO sectionVisibilityBuffer rather than having
    // every region have its own visibility buffer cause this wastes alot of vram
    public final Buffer sectionVisibilityBuffer;

    //scene, regionArray, regionVisibilityArray, sectionCommandBuff, sectionVisibilityBuff
    //NOTE: could merge regionArray and regionVisibilityArray with a bit or
    //regionLUT?
    public ViewportedData(int viewport) {
        this.device = SodiumClientMod.DEVICE;
        scene = null;
        frustumRegionArray = null;
        regionVisibilityArray = null;
        sectionCommandBuffer = null;
        computeDispatchCommandBuffer = null;
        visibleRegionArray = null;
        sectionVisibilityBuffer = null;
    }
}
