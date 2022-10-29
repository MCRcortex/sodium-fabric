package me.cortex.vulkanitelib.descriptors;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorSetLayoutBuilder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.nio.LongBuffer;
import java.util.List;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static me.cortex.vulkanitelib.utils.VVkUtils.repeat;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;

//TODO: make a system that given multiple VVkDescriptorSetLayout can create the specified VVkDescriptorSetsPooled
public class VVkDescriptorSetLayout extends VVkObject {
    public long layout;
    private final List<DescriptorSetLayoutBuilder.Entry> entries;
    public VVkDescriptorSetLayout(VVkDevice device, long layout, List<DescriptorSetLayoutBuilder.Entry> descriptorEntries) {
        super(device);
        this.layout = layout;
        entries = descriptorEntries;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public VVkDescriptorSetsPooled createDescriptorSetsAndPool(int count) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer typeCounts = VkDescriptorPoolSize.calloc(entries.size());
            for(int i = 0; i < entries.size(); i++) {
                typeCounts.get(i)
                        .descriptorCount(count)
                        .type(entries.get(i).type());
            }
            VkDescriptorPoolCreateInfo dpci = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .maxSets(count)
                    .pPoolSizes(typeCounts);
            LongBuffer pPool = stack.mallocLong(1);
            _CHECK_(vkCreateDescriptorPool(device.device, dpci, null, pPool));


            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pPool.get(0))
                    .pSetLayouts(repeat(stack, layout, count));
            long[] pDescs = new long[count];
            _CHECK_(vkAllocateDescriptorSets(device.device, allocInfo, pDescs));

            return new VVkDescriptorSetsPooled(device, pDescs, pPool.get(0), new VVkDescriptorSetLayout[]{this});
        }
    }
}
