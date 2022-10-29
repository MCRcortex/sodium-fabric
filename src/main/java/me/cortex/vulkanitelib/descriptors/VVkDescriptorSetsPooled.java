package me.cortex.vulkanitelib.descriptors;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorUpdateBuilder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

public class VVkDescriptorSetsPooled extends VVkObject {
    public final VVkDescriptorSetLayout[] layouts;
    public final long[] sets;
    public final long pool;
    protected VVkDescriptorSetsPooled(VVkDevice device, long[] descriptorSet, long pool, VVkDescriptorSetLayout[] descriptorSetLayouts) {
        super(device);
        layouts = descriptorSetLayouts;
        this.sets = descriptorSet;
        this.pool = pool;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public VVkDescriptorSetsPooled update(DescriptorUpdateBuilder writer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writers = VkWriteDescriptorSet.calloc(writer.updates.size(), stack);
            for (var updater : writer.updates) {
                updater.getLeft().prep(stack, writers.get().sType$Default());
            }
            writers.rewind();
            for (int i = 0; i < sets.length; i++) {
                for (var updater : writer.updates) {
                    updater.getRight().write(i, writers.get().dstSet(sets[i]));
                }
                writers.rewind();
                vkUpdateDescriptorSets(device.device, writers, null);
            }
            /*
            VkWriteDescriptorSet.Buffer writers = writer.init(stack);//Maybe dont do this and have stuff malloced instead
            for (int i = 0; i < descriptorSets.length; i++) {
                writer.update(i, descriptorSets[i]);
                vkUpdateDescriptorSets(device.device, writers, null);
            }*/
        }
        return this;
    }
}
