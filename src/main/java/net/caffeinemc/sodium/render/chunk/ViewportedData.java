package net.caffeinemc.sodium.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Comparator;
import java.util.TreeSet;

public class ViewportedData {
    private ViewportedData() {
    }

    public TreeSet<RenderRegion> visible_regions = new TreeSet<>(Comparator.comparingDouble(a->a.weight));
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
            cdat = viewports.computeIfAbsent(ViewportInterface.CURRENT_VIEWPORT, k->new ViewportedData());
            lastViewIndex = ViewportInterface.CURRENT_VIEWPORT;
        }
        return cdat;
    }
}
