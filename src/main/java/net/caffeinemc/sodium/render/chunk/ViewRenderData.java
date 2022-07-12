package net.caffeinemc.sodium.render.chunk;

public class ViewRenderData {
    private ViewRenderData(int viewport) {

    }

    private static final ViewportInstancedData<ViewRenderData> HOLDER = new ViewportInstancedData<>(ViewRenderData::new);
    public static ViewRenderData get() {
        return HOLDER.get();
    }
}
