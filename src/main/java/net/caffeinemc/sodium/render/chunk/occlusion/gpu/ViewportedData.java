package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

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
    public ViewportedData(int viewport) {
        this.device = SodiumClientMod.DEVICE;
    }
}
