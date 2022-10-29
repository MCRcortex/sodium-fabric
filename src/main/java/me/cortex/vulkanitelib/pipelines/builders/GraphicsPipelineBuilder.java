package me.cortex.vulkanitelib.pipelines.builders;

import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;
import me.cortex.vulkanitelib.pipelines.VVkShader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static me.cortex.testbed.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

public class GraphicsPipelineBuilder extends PipelineBuilder {
    VVkRenderPass renderPass;
    public GraphicsPipelineBuilder set(VVkRenderPass renderPass) {
        this.renderPass = renderPass;
        return this;
    }

    List<VVkDescriptorSetLayout> descriptorSetLayouts = new LinkedList<>();
    public GraphicsPipelineBuilder clearDescriptorSetsLayouts() {
        descriptorSetLayouts.clear();
        return this;
    }
    public GraphicsPipelineBuilder add(VVkDescriptorSetLayout descriptorSetLayout) {
        descriptorSetLayouts.add(descriptorSetLayout);
        return this;
    }

    private record ShaderEntry(VVkShader shader, String entry, int stages) {
        public void populate(MemoryStack stack, VkPipelineShaderStageCreateInfo stageCreateInfo) {
            stageCreateInfo.sType$Default()
                    .pName(stack.UTF8(entry))
                    .stage(stages)
                    .module(shader.module);
        }
    }
    List<ShaderEntry> shaders = new LinkedList<>();
    public GraphicsPipelineBuilder clearShaders() {
        shaders.clear();
        return this;
    }
    public GraphicsPipelineBuilder add(VVkShader shader, String entry, int stages) {
        shaders.add(new ShaderEntry(shader, entry, stages));
        return this;
    }
    public GraphicsPipelineBuilder add(VVkShader shader, int stages) {
        return add(shader, "main", stages);
    }
    public GraphicsPipelineBuilder add(VVkShader shader) {
        return add(shader, shader.stages);
    }

    public final class VertexInputBuilder {
        final int binding;
        final int stride;
        final int inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
        public VertexInputBuilder(int binding, int stride) {
            this.binding = binding;
            this.stride = stride;
        }

        record AttributeEntry(int location, int format, int offset){}
        final List<AttributeEntry> entries = new LinkedList<>();
        public VertexInputBuilder attribute(int location, int format, int offset) {
            entries.add(new AttributeEntry(location, format, offset));
            return this;
        }
        public VertexInputBuilder attribute(int format, int offset) {
            return attribute(entries.size(), format, offset);
        }

        public GraphicsPipelineBuilder end() {
            return GraphicsPipelineBuilder.this;
        }

        private void populate(VkVertexInputBindingDescription bindingDescription) {
            bindingDescription.binding(binding).stride(stride).inputRate(inputRate);
        }

        private void populate(VkVertexInputAttributeDescription.Buffer base) {
            for (AttributeEntry entry : entries) {
                base.get()
                        .binding(binding)
                        .offset(entry.offset)
                        .format(entry.format)
                        .location(entry.location);

            }
        }
    }

    List<VertexInputBuilder> vertexInputs = new LinkedList<>();
    public GraphicsPipelineBuilder clearVertexInputs() {
        vertexInputs.clear();
        return this;
    }
    public VertexInputBuilder getExistingVertexInput(int binding) {
        for (var input : vertexInputs)
            if (input.binding == binding)
                return input;
        return null;
    }
    public VertexInputBuilder addVertexInput(int binding, int stride) {
        VertexInputBuilder builder = new VertexInputBuilder(binding, stride);
        vertexInputs.add(builder);
        return builder;
    }
    public GraphicsPipelineBuilder addVertexInput(int binding, int stride, Consumer<VertexInputBuilder> consumer) {
        VertexInputBuilder builder = new VertexInputBuilder(binding, stride);
        vertexInputs.add(builder);
        consumer.accept(builder);
        return this;
    }

    record PushEntry(int stages, int offset, int range){}
    List<PushEntry> pushEntries = new LinkedList<>();
    public GraphicsPipelineBuilder clearPushConstants() {
        pushEntries.clear();
        return this;
    }
    public GraphicsPipelineBuilder addPushConstant(int stages, int offset, int range) {
        pushEntries.add(new PushEntry(stages, offset, range));
        return this;
    }


    record Rasterizer(boolean depthBiasEnabled, float depthBiasClamp, float depthBiasFactor, float depthBiasSlopeFactor, boolean discard, int polygonMode, int cullMode, int frontFace, float lineWidth){}
    Rasterizer rasterizer;
    public GraphicsPipelineBuilder rasterization(boolean depthBiasEnabled, float depthBiasClamp, float depthBiasFactor, float depthBiasSlopeFactor, boolean discard, int polygonMode, int cullMode, int frontFace, float lineWidth) {
        rasterizer = new Rasterizer(depthBiasEnabled, depthBiasClamp, depthBiasFactor, depthBiasSlopeFactor, discard, polygonMode, cullMode, frontFace, lineWidth);
        return this;
    }
    public GraphicsPipelineBuilder rasterization(boolean depthBiasEnabled, float depthClamp, boolean discard, int polygonMode, int cullMode, int frontFace) {
        return rasterization(depthBiasEnabled, depthClamp, 0.0f, 0.0f, discard, polygonMode, cullMode, frontFace, 1.0f);
    }

    public GraphicsPipelineBuilder rasterization(boolean depthBiasEnabled, boolean discard, int cullMode, int frontFace) {
        return rasterization(depthBiasEnabled, 0.0f, discard, VK_POLYGON_MODE_FILL, cullMode, frontFace);
    }

    public GraphicsPipelineBuilder rasterization(boolean depthBiasEnabled, boolean discard, int cullMode) {
        return rasterization(depthBiasEnabled, discard, cullMode, VK_FRONT_FACE_CLOCKWISE);
    }

    public GraphicsPipelineBuilder rasterization() {
        return rasterization(false, false, VK_CULL_MODE_BACK_BIT);
    }

    record InputAssembly(int topology, boolean restartEnabled){}
    InputAssembly inputAssembly;
    public GraphicsPipelineBuilder inputAssembly(int topology, boolean restartEnabled) {
        inputAssembly = new InputAssembly(topology, restartEnabled);
        return this;
    }

    public GraphicsPipelineBuilder inputAssembly(int topology) {
        return inputAssembly(topology, false);
    }

    record Viewport(float x, float y, float width, float height, float minDepth, float maxDepth){}
    List<Viewport> viewports = new LinkedList<>();
    public GraphicsPipelineBuilder clearViewports() {
        viewports.clear();
        return this;
    }
    public GraphicsPipelineBuilder addViewport() {
        viewports.add(null);
        return this;
    }
    public GraphicsPipelineBuilder addViewport(float x, float y, float width, float height, float minDepth, float maxDepth) {
        viewports.add(new Viewport(x, y, width, height, minDepth, maxDepth));
        return this;
    }

    record Scissor(int ox, int oy, int ex, int ey){}
    List<Scissor> scissors = new LinkedList<>();
    public GraphicsPipelineBuilder clearScissors() {
        viewports.clear();
        return this;
    }
    public GraphicsPipelineBuilder addScissor() {
        scissors.add(null);
        return this;
    }
    public GraphicsPipelineBuilder addScissor(int ox, int oy, int ex, int ey) {
        scissors.add(new Scissor(ox, oy, ex, ey));
        return this;
    }

    record MultisamplingState(boolean enabled, int rasterizationSamples, float minSampleShading, boolean alpha2converge, boolean alpha21, int[] sampleMask){}
    MultisamplingState multisamplingState;
    public GraphicsPipelineBuilder multisampling(boolean enabled, int rasterizationSamples, float minSampleShading, boolean alpha2converge, boolean alpha21, int[] sampleMask) {
        multisamplingState = new MultisamplingState(enabled, rasterizationSamples, minSampleShading, alpha2converge, alpha21, sampleMask);
        return this;
    }

    public GraphicsPipelineBuilder multisampling(boolean enabled, int rasterizationSamples) {
        return multisampling(enabled, rasterizationSamples, 0.0f, false, false, null);
    }

    public GraphicsPipelineBuilder multisampling() {
        return multisampling(false, VK_SAMPLE_COUNT_1_BIT);
    }

    record DepthStencilState(){}
    DepthStencilState depthStencilState;
    public GraphicsPipelineBuilder depthStencil() {
        //throw new IllegalStateException();
        return this;
    }

    public class ColourBlendingBuilder {
        record ColorBlendAttachment(int cs, int cd, int co, int as, int ad, int ao){}
        final int logicalOp;
        final float[] constants;
        final List<ColorBlendAttachment> attachments = new LinkedList<>();
        public ColourBlendingBuilder(int logicalOp, float r, float g, float b, float a) {
            this.logicalOp = logicalOp;
            this.constants = new float[]{r,g,b,a};
        }

        public GraphicsPipelineBuilder end() {
            return GraphicsPipelineBuilder.this;
        }

        public ColourBlendingBuilder attachment() {//Not enabled
            attachments.add(null);
            return this;
        }

        public ColourBlendingBuilder attachment(int srcColor, int dstColor, int colorOp, int srcAlpha, int dstAlpha, int alphaOp) {
            attachments.add(new ColorBlendAttachment(srcColor, dstColor, colorOp, srcAlpha, dstAlpha, alphaOp));
            return this;
        }


        public VkPipelineColorBlendStateCreateInfo generate(MemoryStack stack) {
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments = VkPipelineColorBlendAttachmentState.calloc(attachments.size(), stack);
            attachments.forEach(a-> {
                VkPipelineColorBlendAttachmentState cbas = blendAttachments.get();
                if (a == null) {
                    cbas.blendEnable(false).colorWriteMask(0xF);
                    return;
                }
                cbas.blendEnable(true).srcColorBlendFactor(a.cs).dstColorBlendFactor(a.cd).colorBlendOp(a.co)
                        .srcAlphaBlendFactor(a.as).dstAlphaBlendFactor(a.ad).alphaBlendOp(a.ao)
                        .colorWriteMask(0xF);//RGBA
            });
            blendAttachments.rewind();
            return VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(logicalOp!=-1)
                    .logicOp(logicalOp!=-1?logicalOp:VK_LOGIC_OP_COPY)
                    .blendConstants(stack.floats(constants))
                    .pAttachments(blendAttachments);
        }
    }
    ColourBlendingBuilder colourBlending;
    public ColourBlendingBuilder colourBlending(int logicalOp, float r, float g, float b, float a) {
        colourBlending = new ColourBlendingBuilder(logicalOp, r, g, b, a);
        return colourBlending;
    }
    public ColourBlendingBuilder colourBlending(float r, float g, float b, float a) {
        return colourBlending(-1, r, g, b, a);
    }
    public ColourBlendingBuilder colourBlending() {
        return colourBlending(0,0,0,0);
    }
    public GraphicsPipelineBuilder colourBlending(int logicalOp, float r, float g, float b, float a, Consumer<ColourBlendingBuilder> consumer) {
        colourBlending = new ColourBlendingBuilder(logicalOp, r, g, b, a);
        consumer.accept(colourBlending);
        return this;
    }
    public GraphicsPipelineBuilder colourBlending(float r, float g, float b, float a, Consumer<ColourBlendingBuilder> consumer) {
        return colourBlending(-1, r, g, b, a, consumer);
    }
    public GraphicsPipelineBuilder colourBlending(Consumer<ColourBlendingBuilder> consumer) {
        return colourBlending(0,0,0,0, consumer);
    }

    List<Integer> dynamicStates = new LinkedList<>();
    public GraphicsPipelineBuilder clearDynamicStates() {
        dynamicStates.clear();
        return this;
    }
    public GraphicsPipelineBuilder addDynamicStates(int... states) {
        for (int state : states)
            dynamicStates.add(state);
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

    public VkGraphicsPipelineCreateInfo generatePipeline(MemoryStack stack, long layout) {
        // ===> SHADER STAGE <===

        VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(shaders.size(), stack);
        shaders.forEach(x->x.populate(stack, shaderStages.get()));
        shaderStages.rewind();

        // ===> VERTEX STAGE <===

        VkVertexInputBindingDescription.Buffer bindingDescriptions = VkVertexInputBindingDescription.calloc(vertexInputs.size(), stack);
        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(vertexInputs.stream().mapToInt(a->a.entries.size()).sum(), stack);
        vertexInputs.forEach(x->{x.populate(bindingDescriptions.get());x.populate(attributeDescriptions);});
        bindingDescriptions.rewind();
        attributeDescriptions.rewind();
        VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType$Default()
                .pVertexBindingDescriptions(bindingDescriptions)
                .pVertexAttributeDescriptions(attributeDescriptions);

        // ===> INPUT ASSEMBLY STAGE <===

        VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType$Default()
                .topology(this.inputAssembly.topology)
                .primitiveRestartEnable(this.inputAssembly.restartEnabled);

        // ===> RASTERIZATION STAGE <===

        VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType$Default()
                .rasterizerDiscardEnable(this.rasterizer.discard)
                .polygonMode(this.rasterizer.polygonMode)
                .lineWidth(this.rasterizer.lineWidth)
                .cullMode(this.rasterizer.cullMode)
                .frontFace(this.rasterizer.frontFace)
                .depthBiasEnable(this.rasterizer.depthBiasEnabled)
                .depthBiasConstantFactor(this.rasterizer.depthBiasFactor)
                .depthBiasSlopeFactor(this.rasterizer.depthBiasSlopeFactor)
                .depthClampEnable(this.rasterizer.depthBiasClamp != 0.0f)
                .depthBiasClamp(this.rasterizer.depthBiasClamp);


        // ===> MULTISAMPLE STATE <===

        VkPipelineMultisampleStateCreateInfo multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType$Default()
                .sampleShadingEnable(this.multisamplingState.enabled)
                .rasterizationSamples(this.multisamplingState.rasterizationSamples)
                .minSampleShading(this.multisamplingState.minSampleShading)
                .pSampleMask(this.multisamplingState.sampleMask == null?null:stack.ints(this.multisamplingState.sampleMask))
                .alphaToCoverageEnable(this.multisamplingState.alpha2converge)
                .alphaToOneEnable(this.multisamplingState.alpha21);

        // ===> DEPTH TEST <===
        //TODO: IMPLEMENT THIS PROPERLY
        VkPipelineDepthStencilStateCreateInfo depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType$Default()
                .depthTestEnable(true)
                .depthWriteEnable(true)
                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
        depthStencilState.back()
                .failOp(VK_STENCIL_OP_KEEP)
                .passOp(VK_STENCIL_OP_KEEP)
                .compareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
        depthStencilState.front(depthStencilState.back());

        // ===> VIEWPORT STATE <===
        VkViewport.Buffer viewports = VkViewport.calloc((int) this.viewports.stream().filter(Objects::nonNull).count(), stack);
        VkRect2D.Buffer scissors = VkRect2D.calloc((int) this.scissors.stream().filter(Objects::nonNull).count(), stack);
        this.viewports.stream().filter(Objects::nonNull).forEach(a->viewports.get()
                .x(a.x).y(a.y).width(a.width).height(a.height).minDepth(a.minDepth).maxDepth(a.maxDepth));
        this.scissors.stream().filter(Objects::nonNull).forEach(a->{
            VkRect2D rect2D = scissors.get();
            rect2D.offset().set(a.ox, a.oy);
            rect2D.extent().set(a.ex, a.ey);
        });
        viewports.rewind();
        scissors.rewind();

        VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType$Default()
                .pViewports(viewports)
                .pScissors(scissors)
                .viewportCount(this.viewports.size())
                .scissorCount(this.scissors.size())
                ;

        // ===> COLOR BLENDING <===

        VkPipelineColorBlendStateCreateInfo colourBlending = this.colourBlending.generate(stack);

        // ===> DYNAMIC STATES <===
        VkPipelineDynamicStateCreateInfo dynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType$Default()
                .pDynamicStates(stack.ints(dynamicStates.stream().mapToInt(a->a).toArray()));//FIXME: make this more efficient

        VkGraphicsPipelineCreateInfo graphicsPipeline = VkGraphicsPipelineCreateInfo.calloc(stack)
                .sType$Default()
                .layout(layout)
                .renderPass(renderPass.renderpass)
                .pVertexInputState(vertexInput)
                .pStages(shaderStages)
                .pRasterizationState(rasterizer)
                .pDynamicState(dynamicStateCreateInfo)
                .pInputAssemblyState(inputAssembly)
                .pMultisampleState(multisampleState)
                .pDepthStencilState(depthStencilState)
                .pViewportState(viewportState)
                .pColorBlendState(colourBlending);

        //TODO: FIGURE OUT HOW TO DO THE REST OF THE STUFF WITH DYNAMIC STATE

        return graphicsPipeline;
    }
}
