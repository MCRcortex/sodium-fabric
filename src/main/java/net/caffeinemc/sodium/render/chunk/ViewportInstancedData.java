package net.caffeinemc.sodium.render.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.caffeinemc.sodium.render.ViewportInterface;

//TODO: need to have a global list of data that can all be reset, e.g. render distance changes
public class ViewportInstancedData <DATA extends IViewportData> {

    public interface IViewportFactory <DATA> { DATA create(int viewport); }

    private final IViewportFactory<DATA> factory;
    private DATA current;
    private int currentViewport = Integer.MIN_VALUE;
    private final Int2ObjectMap<DATA> viewports = new Int2ObjectOpenHashMap<>(2);

    public ViewportInstancedData(IViewportFactory<DATA> factory) {
        this.factory = factory;
    }

    public DATA get() {
        int viewportIndex = ViewportInterface.CURRENT_VIEWPORT;
        if (viewportIndex != currentViewport) {
            current = viewports.computeIfAbsent(viewportIndex, factory::create);
            currentViewport = viewportIndex;
        }
        return current;
    }

    public void deleteAll() {
        for (DATA data : viewports.values()) {
            data.delete();
        }
        viewports.clear();
        current = null;
        currentViewport = -1;
    }
}
