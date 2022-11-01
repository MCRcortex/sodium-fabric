package me.cortex.vulkanitelib;

import me.cortex.testbed.RayQueryTriangle;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorSetLayoutBuilder;
import me.cortex.vulkanitelib.memory.VVkAllocator;
import me.cortex.vulkanitelib.memory.image.VVkExportedAllocator;
import me.cortex.vulkanitelib.memory.image.VVkFramebuffer;
import me.cortex.vulkanitelib.memory.image.VVkImageView;
import me.cortex.vulkanitelib.memory.image.VVkSampler;
import me.cortex.vulkanitelib.other.VVkCommandBuffer;
import me.cortex.vulkanitelib.other.VVkCommandPool;
import me.cortex.vulkanitelib.other.VVkQueue;
import me.cortex.vulkanitelib.pipelines.*;
import me.cortex.vulkanitelib.pipelines.builders.ComputePipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.GraphicsPipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.RenderPassBuilder;
import me.cortex.vulkanitelib.raytracing.VAccelerationMethods;
import me.cortex.vulkanitelib.sync.VGlVkSemaphore;
import me.cortex.vulkanitelib.sync.VVkFence;
import me.cortex.vulkanitelib.sync.VVkSemaphore;
import me.cortex.vulkanitelib.utils.ShaderUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.function.Consumer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.opengl.EXTSemaphore.glGenSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphore.glIsSemaphoreEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.KHRRobustness.GL_NO_ERROR;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class VVkDevice {
    public VkDevice device;
    VVkContext context;
    public VVkAllocator allocator;
    public VVkExportedAllocator exportedAllocator;//TODO: FIX THIS ENTIRE MESS OF A SYSTEM FOR THE LOVE OF GOD
    public final VVkCommandPool transientPool;

    public VAccelerationMethods accelerator;

    public VVkDevice(VkDevice device, VVkContext vVkContext) {
        this.device = device;
        this.context = vVkContext;
        this.allocator = new VVkAllocator(this, 0);//VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT//TODO: make this dependent on extensions
        this.exportedAllocator = new VVkExportedAllocator(this);
        transientPool = createCommandPool(0, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);//TODO: pass in transient family

        accelerator = new VAccelerationMethods(this);//TODO: DONT PUT THIS IN THE DEVICE CLASS
    }

    public VVkRenderPass build(RenderPassBuilder rpb) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderPassCreateInfo createInfo = rpb.generate(stack);
            LongBuffer pBuffer = stack.mallocLong(1);
            _CHECK_(vkCreateRenderPass(device, createInfo, null, pBuffer));
            return new VVkRenderPass(this, pBuffer.get(0), rpb.attachmentCount());
        }
    }

    public VVkDescriptorSetLayout build(DescriptorSetLayoutBuilder dsb) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutCreateInfo createInfo = dsb.generate(stack);
            LongBuffer pBuffer = stack.mallocLong(1);
            _CHECK_(vkCreateDescriptorSetLayout(device, createInfo, null, pBuffer));
            return new VVkDescriptorSetLayout(this, pBuffer.get(0), dsb.getDescriptors());
        }
    }

    public VVkShader compileShader(String source, int shaderStage) {
        ByteBuffer shaderSource = ShaderUtils.compileShader("shader", source, shaderStage);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(shaderSource);
            LongBuffer pShaderModule = stack.mallocLong(1);
            _CHECK_(vkCreateShaderModule(device, createInfo, null, pShaderModule));
            return new VVkShader(this, shaderSource, shaderStage, pShaderModule.get(0));
        }
    }

    public VVkPipeline build(GraphicsPipelineBuilder dsb) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo layoutCreateInfo = dsb.generateLayout(stack);
            LongBuffer pLayout = stack.mallocLong(1);
            _CHECK_(vkCreatePipelineLayout(device, layoutCreateInfo, null, pLayout));

            VkGraphicsPipelineCreateInfo pipelineCreateInfo = dsb.generatePipeline(stack, pLayout.get(0));
            LongBuffer pPipeline = stack.mallocLong(1);
            _CHECK_(vkCreateGraphicsPipelines(device, 0, VkGraphicsPipelineCreateInfo.create(pipelineCreateInfo.address(), 1), null, pPipeline));
            return new VVkGraphicsPipeline(this, pPipeline.get(0), pLayout.get(0));
        }
    }

    public VVkComputePipeline build(ComputePipelineBuilder csp) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo layoutCreateInfo = csp.generateLayout(stack);
            LongBuffer pLayout = stack.mallocLong(1);
            _CHECK_(vkCreatePipelineLayout(device, layoutCreateInfo, null, pLayout));

            VkComputePipelineCreateInfo pipelineCreateInfo = csp.generatePipeline(stack, pLayout.get(0));
            LongBuffer pPipeline = stack.mallocLong(1);
            _CHECK_(vkCreateComputePipelines(device, 0, VkComputePipelineCreateInfo.create(pipelineCreateInfo.address(), 1), null, pPipeline));
            return new VVkComputePipeline(this, pPipeline.get(0), pLayout.get(0));
        }
    }

    public VVkFramebuffer createFramebuffer(VVkRenderPass renderPass, VVkImageView... attachments) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int width = attachments[0].image.width;
            int height = attachments[0].image.height;
            int layers = attachments[0].layers;
            LongBuffer pAttachments = stack.mallocLong(attachments.length);
            for (VVkImageView attachment : attachments) {
                pAttachments.put(attachment.view);
                if (attachment.image.width != width || attachment.image.height != height || attachment.layers != layers)
                    throw new IllegalStateException();
            }
            pAttachments.rewind();
            VkFramebufferCreateInfo fci = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(pAttachments)
                    .height(height)
                    .width(width)
                    .layers(layers)
                    .renderPass(renderPass.renderpass);
            LongBuffer pFramebuffer = stack.mallocLong(1);
            _CHECK_(vkCreateFramebuffer(device, fci, null, pFramebuffer));
            return new VVkFramebuffer(this, renderPass, attachments, pFramebuffer.get(0), width, height, layers);
        }
    }

    public VVkCommandPool createCommandPool(int family, int flags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(family)
                    .flags(flags);
            LongBuffer pCmdPool = stack.mallocLong(1);
            _CHECK_(vkCreateCommandPool(device, cmdPoolInfo, null, pCmdPool));
            return new VVkCommandPool(this, pCmdPool.get(0));
        }
    }

    private final HashMap<Integer, VVkQueue> queues = new HashMap<>();
    public VVkQueue fetchQueue(int family, int index) {
        return queues.computeIfAbsent((family<<16)|index, (a)->{
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pQueue = stack.callocPointer(1);
                vkGetDeviceQueue(device, family, index, pQueue);
                long queue = pQueue.get(0);
                return new VVkQueue(this, new VkQueue(queue, device));
            }
        });
    }
    public VVkQueue fetchQueue() {
        return fetchQueue(0,0);//TODO: replace default family from 0 to a queue
    }




    public VVkSemaphore createSemaphore() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo sci = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default();
            long[] out = new long[1];
            vkCreateSemaphore(device, sci, null, out);
            return new VVkSemaphore(this, out[0]);
        }
    }

    public VGlVkSemaphore createSharedSemaphore() {

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExportSemaphoreCreateInfo esci = VkExportSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            VkSemaphoreCreateInfo sci = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(esci);
            long[] out = new long[1];
            _CHECK_(vkCreateSemaphore(device, sci, null, out));
            PointerBuffer pb = stack.callocPointer(1);
            VkSemaphoreGetWin32HandleInfoKHR sgwhi = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .semaphore(out[0])
                    .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            _CHECK_(vkGetSemaphoreWin32HandleKHR(device, sgwhi, pb));
            if (pb.get(0)== 0) {
                throw new IllegalStateException();
            }
            int glSemaphore = glGenSemaphoresEXT();
            glImportSemaphoreWin32HandleEXT(glSemaphore, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, pb.get(0));
            if (!glIsSemaphoreEXT(glSemaphore))
                throw new IllegalStateException();
            if (glGetError() != GL_NO_ERROR)
                throw new IllegalStateException();

            return new VGlVkSemaphore(this, glSemaphore, pb.get(0), out[0]);
        }
    }

    public VVkSampler createSampler() {
        return createSampler(1);
    }
    public VVkSampler createSampler(int maxLod) {//TODO: Add configurable options
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSampler = stack.mallocLong(1);
            VkSamplerCreateInfo sci = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .maxLod(maxLod)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .maxAnisotropy(1.0f);
            _CHECK_(vkCreateSampler(device, sci, null, pSampler));
            return new VVkSampler(this, pSampler.get(0));
        }
    }

    public VVkDevice singleTimeCommand(Consumer<VVkCommandBuffer> acceptor, Runnable postFence) {
        VVkCommandBuffer commandBuffer = transientPool.createCommandBuffer();
        commandBuffer.begin();
        acceptor.accept(commandBuffer);
        commandBuffer.end();
        fetchQueue().submit(commandBuffer, postFence);
        return this;
    }

    public VVkFence createFence(boolean addToWatch) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            LongBuffer pFence = stack.mallocLong(1);
            _CHECK_(vkCreateFence(device, VkFenceCreateInfo
                            .calloc(stack)
                            .sType$Default(), null, pFence),
                    "Failed to create fence");
            VVkFence fence = new VVkFence(this, pFence.get(0));
            if (addToWatch)
                addFenceWatch(fence);
            return fence;
        }
    }


    //TODO: maybe move this from here somewhere else
    private final Set<VVkFence> fences = new HashSet<>();
    public VVkDevice addFenceWatch(VVkFence fence) {
        fences.add(fence);
        return this;
    }
    //TODO: maybe move this from here somewhere else
    public VVkDevice tickFences() {
        Iterator<VVkFence> it = fences.iterator();
        while (it.hasNext()) {
            VVkFence e = it.next();
            if (vkGetFenceStatus(device, e.fence) == VK_SUCCESS) {
                it.remove();
                e.free();
                e.onFenced();
            }
        }
        return this;
    }

}
