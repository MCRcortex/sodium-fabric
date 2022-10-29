package me.cortex.vulkanitelib.pipelines;

import me.cortex.vulkanitelib.VVkDevice;

import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;

public class VVkGraphicsPipeline extends VVkPipeline {
    public VVkGraphicsPipeline(VVkDevice device, long pipeline, long layout) {
        super(device, pipeline, layout, VK_PIPELINE_BIND_POINT_GRAPHICS);
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
