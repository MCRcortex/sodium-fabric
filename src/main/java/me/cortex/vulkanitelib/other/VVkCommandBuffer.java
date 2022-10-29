package me.cortex.vulkanitelib.other;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetsPooled;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.memory.image.VVkFramebuffer;
import me.cortex.vulkanitelib.pipelines.VVkPipeline;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.vulkan.VK10.*;

public class VVkCommandBuffer extends VVkObject {
    public final VVkCommandPool pool;
    public final VkCommandBuffer buffer;

    public VVkCommandBuffer(VVkDevice device, VVkCommandPool pool, VkCommandBuffer buffer) {
        super(device);
        this.pool = pool;
        this.buffer = buffer;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public VVkCommandBuffer begin() {
        return begin(0);
    }
    public VVkCommandBuffer begin(int flags) {//TODO: need to add option for buffer inheratance
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(flags);
            _CHECK_(vkBeginCommandBuffer(buffer, beginInfo));
        }
        return this;
    }

    public VVkCommandBuffer beginRenderPass(VVkFramebuffer framebuffer) {//Maybe return an object to use or something
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer vkClearValues = VkClearValue.calloc(framebuffer.renderPass.attachments, stack);//TODO: THIS  do like , float depthClear, float[]... colourClear
            // and have the renderPass have the index of the depth attachment
            vkClearValues.get(0).color().float32(stack.floats(1.0f,0.5f,0.4f,1f));
            vkClearValues.get(1).depthStencil().depth(1.0f).stencil(0);

            vkClearValues.rewind();
            VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .framebuffer(framebuffer.framebuffer)
                    .renderPass(framebuffer.renderPass.renderpass)
                    .pClearValues(vkClearValues);

            beginInfo.renderArea().offset().set(0, 0);
            beginInfo.renderArea().extent().set(framebuffer.width, framebuffer.height);
            vkCmdBeginRenderPass(buffer, beginInfo, VK_SUBPASS_CONTENTS_INLINE);//TODO: move VK_SUBPASS_CONTENTS_INLINE somewhere else
        }
        return this;
    }

    private VVkPipeline currentPipeline;
    public VVkCommandBuffer bind(VVkPipeline pipeline) {
        vkCmdBindPipeline(buffer, pipeline.bindingpoint, pipeline.pipeline);
        currentPipeline = pipeline;
        return this;
    }

    public VVkCommandBuffer bind(VVkDescriptorSetsPooled descriptorSets, int index) {//TODO: make method to bind multiple descriptor sets and indexs
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(buffer, currentPipeline.bindingpoint, currentPipeline.layout, 0, stack.longs(descriptorSets.sets[index]), null);
        }
        return this;
    }

    public VVkCommandBuffer bindIndex(VVkBuffer buffer, int type) {
        throw new IllegalStateException();
    }

    public VVkCommandBuffer bindVertexs(VVkBuffer... buffers) {
        return bindVertexs(buffers, null);
    }
    public VVkCommandBuffer bindVertexs(VVkBuffer buffer, long offset) {
        return bindVertexs(new VVkBuffer[]{buffer}, new long[]{offset});
    }

    public VVkCommandBuffer bindVertexs(VVkBuffer[] buffers, long[] offsets) {
        if (offsets == null) {
            offsets = new long[buffers.length];
        }
        long[] buffs = new long[buffers.length];
        for (int i = 0; i < buffers.length; i++)
            buffs[i] = buffers[i].buffer;
        vkCmdBindVertexBuffers(buffer, 0, buffs, offsets);
        return this;
    }

    public VVkCommandBuffer endRenderPass() {
        vkCmdEndRenderPass(buffer);
        return this;
    }

    public VVkCommandBuffer dispatch(int x, int y, int z) {
        throw new IllegalStateException();
    }

    public VVkCommandBuffer end() {
        _CHECK_(vkEndCommandBuffer(buffer));
        return this;
    }
}
