package me.cortex.vulkanitelib.sync;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

import java.util.LinkedList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.vkDestroyFence;

public class VVkFence extends VVkObject {
    public final long fence;
    private List<Runnable> onFenced = new LinkedList<>();

    public VVkFence(VVkDevice device, long fence) {
        super(device);
        this.fence = fence;
    }

    @Override
    public void free() {
        vkDestroyFence(device.device, fence, null);
    }

    public VVkFence add(Runnable fence) {
        onFenced.add(fence);
        return this;
    }

    public void onFenced() {
        onFenced.forEach(Runnable::run);
    }
}
