package me.cortex.vulkanitelib.descriptors.builders;

import com.google.common.collect.ImmutableList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DescriptorSetLayoutBuilder {
    public record Entry(
            int binding,
            int type,
            int arrayCount,
            int stageFlags) {
        //pImmutableSamplers can be useful
    }
    private List<Entry> descriptors = new LinkedList<>();
    public DescriptorSetLayoutBuilder binding(int binding, int type, int count, int stages) {
        descriptors.add(new Entry(binding, type, count, stages));
        return this;
    }

    public DescriptorSetLayoutBuilder binding(int binding, int type, int stages) {
        return binding(binding, type, 1, stages);
    }
    public DescriptorSetLayoutBuilder binding(int type, int stages) {
        return binding(descriptors.size(), type, stages);
    }

    int flags;
    public DescriptorSetLayoutBuilder() {
        this(0);
    }
    public DescriptorSetLayoutBuilder(int flags){
        this.flags = flags;
    }

    public VkDescriptorSetLayoutCreateInfo generate(MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer descriptorStructs = VkDescriptorSetLayoutBinding.calloc(descriptors.size(), stack);
        for (Entry entry : this.descriptors) {
            descriptorStructs.apply(struct-> {
                struct.binding(entry.binding)
                        .descriptorType(entry.type)
                        .descriptorCount(entry.arrayCount)
                        .stageFlags(entry.stageFlags);
            });
        }
        descriptorStructs.rewind();
        return VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pBindings(descriptorStructs)
                .flags(flags);
    }

    public List<Entry> getDescriptors() {
        return new ArrayList<>(descriptors);
    }
}
