package net.caffeinemc.sodium.render.chunk;

public class ViewportInterface {
    public static int CURRENT_VIEWPORT;
    public static void setViewportIndex(int viewport) {
        if (viewport<0)
            throw new IllegalStateException("Viewport index must be a positive");
        CURRENT_VIEWPORT = viewport;
    }
}
