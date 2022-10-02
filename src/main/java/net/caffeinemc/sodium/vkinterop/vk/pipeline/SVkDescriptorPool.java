package net.caffeinemc.sodium.vkinterop.vk.pipeline;

import net.caffeinemc.sodium.vkinterop.vk.SDescriptorDescription;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class SVkDescriptorPool {
    SVkDevice device;
    SVkDescriptorSetLayout description;
    long pool;
    int max;

    public SVkDescriptorPool(SVkDevice device, SVkDescriptorSetLayout layout, int maxCount) {
        this.device = device;
        this.description = layout;
        this.max = maxCount;
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(layout.description.descriptors.size(), stack);
            for (int i = 0; i<layout.description.descriptors.size(); i++) {
                VkDescriptorPoolSize uniformBufferPoolSize = poolSizes.get(i);
                uniformBufferPoolSize.type(layout.description.descriptors.get(i).type);
                uniformBufferPoolSize.descriptorCount(maxCount);
            }

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPoolSizes(poolSizes)
                    .maxSets(maxCount);
            LongBuffer pDescriptorPool = stack.mallocLong(1);

            if(vkCreateDescriptorPool(device.device, poolInfo, null, pDescriptorPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool");
            }

            pool = pDescriptorPool.get(0);
        }
    }

    public SVkDescriptorSet[] allocateMax() {
        SVkDescriptorSet[] sets = new SVkDescriptorSet[max];
        try (MemoryStack stack = stackPush()) {
            LongBuffer layouts = stack.mallocLong(max);
            for(int i = 0;i < layouts.capacity();i++) {
                layouts.put(i, description.layout);
            }

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pool)
                    .pSetLayouts(layouts);

            LongBuffer pDescriptorSets = stack.mallocLong(max);
            if(vkAllocateDescriptorSets(device.device, allocInfo, pDescriptorSets) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate descriptor sets");
            }
            for (int i = 0; i < max; i++) {
                sets[i] = new SVkDescriptorSet(device, this, pDescriptorSets.get(i));
            }
        }
        return sets;
    }
}
