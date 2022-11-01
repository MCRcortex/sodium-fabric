package me.cortex.vulkanitelib.pipelines;

import me.cortex.vulkanitelib.VVkDevice;

import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;

public class VVkComputePipeline extends VVkPipeline {
    public VVkComputePipeline(VVkDevice device, long pipeline, long layout) {
        super(device, pipeline, layout, VK_PIPELINE_BIND_POINT_COMPUTE);
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
