package me.cortex.vulkanitelib.pipelines;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

public abstract class VVkPipeline extends VVkObject {
    public final long pipeline;
    public final long layout;
    public final int bindingpoint;

    protected VVkPipeline(VVkDevice device, long pipeline, long layout, int bindingpoint) {
        super(device);
        this.pipeline = pipeline;
        this.layout = layout;
        this.bindingpoint = bindingpoint;
    }
}
