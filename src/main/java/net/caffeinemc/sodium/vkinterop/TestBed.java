package net.caffeinemc.sodium.vkinterop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;

import static net.caffeinemc.sodium.vkinterop.VkContextTEMP.getCommandPool;
import static net.caffeinemc.sodium.vkinterop.VkMemUtil.createExportedTextureVMA;
import static org.lwjgl.opengl.EXTSemaphore.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestBed {
    private static int commandBuffersCount = 10;//TODO: MAKE THIS DYNAMIC
    private static ArrayList<VkCommandBuffer> commandBuffers = new ArrayList<>();
    private static long framebuffer;
    private static long colourView;
    private static long depthView;

    private static int width = 800;
    private static int height = 800;
    private static VkMemUtil.VkImageInfo depth;
    private static VkMemUtil.VkImageInfo color;

    public static void init() {
        try (MemoryStack stack = stackPush()) {
            {
                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
                allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
                allocInfo.commandPool(getCommandPool());
                allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                allocInfo.commandBufferCount(commandBuffersCount);

                PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

                if (vkAllocateCommandBuffers(VkContextTEMP.getDevice(), allocInfo, pCommandBuffers) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate command buffers");
                }

                for (int i = 0; i < commandBuffersCount; i++) {
                    commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), VkContextTEMP.getDevice()));
                }
            }
            {
                depth = createExportedTextureVMA(width, height, 1,
                        VK_FORMAT_D24_UNORM_S8_UINT,
                        VK_IMAGE_TILING_OPTIMAL,
                        VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

                color = createExportedTextureVMA(width, height, 1,
                        VK_FORMAT_R8G8B8A8_UNORM,
                        VK_IMAGE_TILING_OPTIMAL,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);


                colourView = VkContextTEMP.createImageView(color.id, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
                depthView = VkContextTEMP.createImageView(depth.id, VK_FORMAT_D24_UNORM_S8_UINT, VK_IMAGE_ASPECT_DEPTH_BIT, 1);
            }
            {
                LongBuffer attach = stack.longs(colourView,depthView);
                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(VkContextTEMP.getRenderPass())
                        .width(1)
                        .height(1)
                        .layers(1)
                        .pAttachments(attach);

                LongBuffer pFramebuffer = stack.mallocLong(1);
                if(vkCreateFramebuffer(VkContextTEMP.getDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer");
                }
                framebuffer = pFramebuffer.get(0);
            }
        }
    }

    public static void runRenderPipeline(VKRenderPipeline pipeline, int frameId) {
        frameId %= commandBuffersCount;
        VkCommandBuffer commandBuffer = commandBuffers.get(frameId);
        try (MemoryStack stack = stackPush()) {
            glSignalSemaphoreEXT(pipeline.ready.glSemaphore, new int[]{}, new int[]{}, new int[]{GL_LAYOUT_GENERAL_EXT});

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();
            if(vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer");
            }
            if (true) {

                VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
                clearValues.get(0).color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
                clearValues.get(1).depthStencil().set(1.0f, 0);

                VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(VkContextTEMP.getRenderPass())
                        .framebuffer(framebuffer)
                        .pClearValues(clearValues);

                vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
                {
                    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.graphicsPipeline);
                    /*
                    LongBuffer vertexBuffers = stack.longs(vertexBuffer);
                    LongBuffer offsets = stack.longs(0);
                    vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets);

                    vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0, VK_INDEX_TYPE_UINT32);

                    vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipelineLayout, 0, stack.longs(descriptorSets.get(i)), null);
                    */
                    //vkCmdDrawIndexed(commandBuffer, 3, 1, 0, 0, 0);
                }
                vkCmdEndRenderPass(commandBuffer);
            }

            if(vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to record command buffer");
            }

            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer))
                    .pWaitSemaphores(stack.longs(pipeline.ready.vkSemaphore))
                    .pSignalSemaphores(stack.longs(pipeline.finish.vkSemaphore));

            vkQueueSubmit(VkContextTEMP.getGraphicsQueue(), submitInfo, 0);
            glSignalSemaphoreEXT(pipeline.finish.glSemaphore, new int[]{}, new int[]{}, new int[]{GL_LAYOUT_GENERAL_EXT});
        }
    }
}
