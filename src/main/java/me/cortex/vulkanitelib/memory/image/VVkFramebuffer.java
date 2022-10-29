package me.cortex.vulkanitelib.memory.image;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;

public class VVkFramebuffer extends VVkObject {
    public final long framebuffer;
    public final VVkRenderPass renderPass;
    public final VVkImageView[] attachments;
    public final int width;
    public final int height;
    public final int layers;

    public VVkFramebuffer(VVkDevice device, VVkRenderPass renderPass, VVkImageView[] attachments, long framebuffer, int width, int height, int layers) {
        super(device);
        this.framebuffer = framebuffer;
        this.renderPass = renderPass;
        this.attachments = attachments;
        this.width = width;
        this.height = height;
        this.layers = layers;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
