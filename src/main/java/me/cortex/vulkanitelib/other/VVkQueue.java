package me.cortex.vulkanitelib.other;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorUpdateBuilder;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.sync.VGlVkSemaphore;
import me.cortex.vulkanitelib.sync.VVkFence;
import me.cortex.vulkanitelib.sync.VVkSemaphore;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

public class VVkQueue extends VVkObject {
    public final VkQueue queue;

    public VVkQueue(VVkDevice device, VkQueue queue) {
        super(device);
        this.queue = queue;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public class BatchSubmitter {
        public BatchSubmitter buffer(VVkCommandBuffer buffer) {
            throw new IllegalStateException();
        }

        public BatchSubmitter wait(VVkSemaphore semaphore, int dstStageMsk) {
            throw new IllegalStateException();
        }

        public BatchSubmitter signal(VVkSemaphore signal) {
            throw new IllegalStateException();
        }

        public VVkQueue submit() {
            return VVkQueue.this;
        }
    }
    public BatchSubmitter batchSubmit() {
        return new BatchSubmitter();
    }

    public VVkQueue submit(VVkCommandBuffer cmdBuff, VVkSemaphore wait, int waitDstStageMsk, VVkSemaphore signal, VVkFence fence) {
        return submit(cmdBuff, new VVkSemaphore[]{wait}, new int[]{waitDstStageMsk}, new VVkSemaphore[]{signal}, fence);
    }

    public VVkQueue submit(VVkCommandBuffer cmdBuff, VVkSemaphore[] wait, int[] waitDstStageMsk, VVkSemaphore[] signal, VVkFence fence) {
        if (wait == null)
            wait = new VVkSemaphore[0];
        if (waitDstStageMsk == null)
            waitDstStageMsk = new int[0];
        if (signal == null)
            signal = new VVkSemaphore[0];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer waitSemaphores = stack.callocLong(wait.length);
            LongBuffer sigSemaphores = stack.callocLong(signal.length);
            for (var ws : wait)
                waitSemaphores.put(ws.semaphore);
            for (var ss : signal)
                sigSemaphores.put(ss.semaphore);
            waitSemaphores.rewind();
            sigSemaphores.rewind();
            _CHECK_(vkQueueSubmit(queue, VkSubmitInfo
                            .calloc(stack)
                            .sType$Default()
                            .pWaitSemaphores(waitSemaphores)
                            .pWaitDstStageMask(stack.ints(waitDstStageMsk))
                            .pSignalSemaphores(sigSemaphores)
                            .pCommandBuffers(stack.pointers(cmdBuff.buffer)),
                            fence != null?fence.fence:0),
                    "Failed to submit command buffer");
        }
        return this;
    }

    //TEMPORARY HACK
    public VVkQueue submit(VVkCommandBuffer cmdBuff) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            _CHECK_(vkQueueSubmit(queue, VkSubmitInfo
                            .calloc(stack)
                            .sType$Default()
                            .pCommandBuffers(stack.pointers(cmdBuff.buffer)), 0),
                    "Failed to submit command buffer");
        }
        return this;
    }
}
