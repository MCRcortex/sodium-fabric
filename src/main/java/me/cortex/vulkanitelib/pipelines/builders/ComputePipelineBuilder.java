package me.cortex.vulkanitelib.pipelines.builders;

import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.pipelines.VVkShader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.LinkedList;
import java.util.List;

public class ComputePipelineBuilder extends PipelineBuilder {
    List<VVkDescriptorSetLayout> descriptorSetLayouts = new LinkedList<>();
    public ComputePipelineBuilder clearDescriptorSetsLayouts() {
        descriptorSetLayouts.clear();
        return this;
    }
    public ComputePipelineBuilder add(VVkDescriptorSetLayout descriptorSetLayout) {
        descriptorSetLayouts.add(descriptorSetLayout);
        return this;
    }

    VVkShader shader;
    String entry;
    public ComputePipelineBuilder set(VVkShader shader, String entry) {
        this.entry = entry;
        this.shader = shader;
        return this;
    }
    public ComputePipelineBuilder set(VVkShader shader) {
        return set(shader, "main");
    }

    record PushEntry(int stages, int offset, int range){}
    List<PushEntry> pushEntries = new LinkedList<>();
    public ComputePipelineBuilder clearPushConstants() {
        pushEntries.clear();
        return this;
    }
    public ComputePipelineBuilder addPushConstant(int stages, int offset, int range) {
        pushEntries.add(new PushEntry(stages, offset, range));
        return this;
    }

    public VkPipelineLayoutCreateInfo generateLayout(MemoryStack stack) {
        LongBuffer setLayoutPtrs = stack.callocLong(descriptorSetLayouts.size());
        descriptorSetLayouts.forEach(a->setLayoutPtrs.put(a.layout));
        setLayoutPtrs.rewind();
        VkPushConstantRange.Buffer pushConsts = VkPushConstantRange.calloc(pushEntries.size(), stack);
        pushEntries.forEach(a->pushConsts.get().offset(a.offset).stageFlags(a.stages).size(a.range));
        pushConsts.rewind();

        return VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pSetLayouts(setLayoutPtrs)
                .pPushConstantRanges(pushConsts);
    }

    public VkComputePipelineCreateInfo generatePipeline(MemoryStack stack, long layout) {
        VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack);
        shaderStage.sType$Default().pName(stack.UTF8(entry)).module(shader.module).stage(shader.stages);
        return VkComputePipelineCreateInfo.calloc(stack)
                .sType$Default()
                .layout(layout)
                .stage(shaderStage);
    }
}
