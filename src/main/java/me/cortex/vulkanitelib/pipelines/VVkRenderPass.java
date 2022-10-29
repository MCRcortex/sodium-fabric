package me.cortex.vulkanitelib.pipelines;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import org.lwjgl.vulkan.VkDevice;

public class VVkRenderPass extends VVkObject {
    public long renderpass;
    public int attachments;

    public VVkRenderPass(VVkDevice device, long renderPass, int attachmentCounts) {
        super(device);
        this.renderpass = renderPass;
        this.attachments = attachmentCounts;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
