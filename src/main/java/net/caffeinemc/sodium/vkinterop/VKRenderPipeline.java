package net.caffeinemc.sodium.vkinterop;

import net.caffeinemc.gfx.api.array.attribute.VertexAttribute;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeFormat;
import net.caffeinemc.gfx.api.array.attribute.VertexFormat;
import net.caffeinemc.gfx.api.pipeline.RenderPipelineDescription;
import net.caffeinemc.gfx.api.pipeline.state.CullMode;
import net.caffeinemc.gfx.api.pipeline.state.DepthFunc;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VKRenderPipeline {
    public Sync.GlVkSemaphore ready = new Sync.GlVkSemaphore();
    public Sync.GlVkSemaphore finish = new Sync.GlVkSemaphore();

    public long graphicsPipeline;
    private long descriptorSetLayout;
    private long pipelineLayout;

    private  long vertShaderModule;
    private  long fragShaderModule;

    public VKRenderPipeline(VertexFormat<TerrainMeshAttribute> format, RenderPipelineDescription pipelineDescription, String vertex, String fragment) {
        vertShaderModule = ShaderUtils.compileShader("vert", vertex, ShaderUtils.ShaderKind.VERTEX_SHADER).createShaderModule(SVkDevice.INSTANCE);
        fragShaderModule = ShaderUtils.compileShader("frag", fragment, ShaderUtils.ShaderKind.FRAGMENT_SHADER).createShaderModule(SVkDevice.INSTANCE);
        createDescriptorLayout();
        createPipelineLayout();
        createPipeline(pipelineDescription, format);
    }

    private void createPipelineLayout() {
        try(MemoryStack stack = stackPush()) {
            // ===> PIPELINE LAYOUT CREATION <===

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            pipelineLayoutInfo.pSetLayouts(stack.longs(descriptorSetLayout));

            /*
            if(pushConstant.getSize() > 0) {
                VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1, stack);
                pushConstantRange.size(pushConstant.getSize());
                pushConstantRange.offset(0);
                pushConstantRange.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

                pipelineLayoutInfo.pPushConstantRanges(pushConstantRange);
            }*/

            LongBuffer pPipelineLayout = stack.longs(VK_NULL_HANDLE);

            if(vkCreatePipelineLayout(VkContextTEMP.getDevice(), pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout");
            }

            pipelineLayout = pPipelineLayout.get(0);
        }
    }


    private void createDescriptorLayout() {
        try(MemoryStack stack = stackPush()) {

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(5, stack);
            //Just hard coding for now
            bindings.get(0)//Camera matrices
                    .binding(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

            bindings.get(1)//Chunk transforms
                    .binding(1)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)//.descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

            bindings.get(2)//Fog
                    .binding(2)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);



            bindings.get(3)//tex_diffuse
                    .binding(3)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_ALL_GRAPHICS);

            bindings.get(4)//tex_light
                    .binding(4)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_ALL_GRAPHICS);


            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack);
            layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            if(vkCreateDescriptorSetLayout(VkContextTEMP.getDevice(), layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }
            descriptorSetLayout = pDescriptorSetLayout.get(0);
        }
    }


    private static int findVkFormat(VertexAttribute format) {
        if (!format.intType() && format.normalized() && format.format()==VertexAttributeFormat.SHORT && format.count()==3) {
            return VK_FORMAT_R16G16B16_SNORM;
        } else if (!format.intType() && format.normalized() && format.format()==VertexAttributeFormat.UNSIGNED_BYTE && format.count()==4) {
            return VK_FORMAT_R8G8B8A8_UNORM;
        } else if (!format.intType() && format.normalized() && format.format()==VertexAttributeFormat.UNSIGNED_SHORT && format.count()==2) {
            return VK_FORMAT_R16G16_UNORM;
        } else if (format.intType() && !format.normalized() && format.format()==VertexAttributeFormat.UNSIGNED_SHORT && format.count()==2) {
            return VK_FORMAT_R16G16_SINT;
        } else {
            throw new IllegalStateException("Unknown format");
        }
    }

    private static VkVertexInputAttributeDescription.Buffer createAttributeDescription(VertexFormat<TerrainMeshAttribute> vertexFormat) {
        return createAttributeDescription(vertexFormat, 0);
    }


    private static VkVertexInputAttributeDescription.Buffer createAttributeDescription(VertexFormat<TerrainMeshAttribute> vertexFormat, int bindingIndex) {
        TerrainMeshAttribute[] values = TerrainMeshAttribute.values();
        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(values.length);
        for(int i = 0; i < values.length; ++i) {
            VkVertexInputAttributeDescription posDescription = attributeDescriptions.get(i);
            posDescription.binding(bindingIndex);
            posDescription.location(i);
            VertexAttribute attribute = vertexFormat.getAttribute(values[i]);
            posDescription.format(findVkFormat(attribute));
            posDescription.offset(attribute.offset());
        }

        return attributeDescriptions.rewind();
    }


    private static VkVertexInputBindingDescription.Buffer getBindingDescription(VertexFormat<TerrainMeshAttribute> vertexFormat) {

        VkVertexInputBindingDescription.Buffer bindingDescription =
                VkVertexInputBindingDescription.calloc(1);

        bindingDescription.binding(0);
        bindingDescription.stride(vertexFormat.stride());
        bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        return bindingDescription;
    }

    private void createPipeline(RenderPipelineDescription pipelineDescription, VertexFormat<TerrainMeshAttribute> vertexFormat) {
        try(MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);

            ByteBuffer entryPoint = stack.UTF8("main");

            vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT);
            vertShaderStageInfo.module(vertShaderModule);
            vertShaderStageInfo.pName(entryPoint);

            VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);

            fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
            fragShaderStageInfo.module(fragShaderModule);
            fragShaderStageInfo.pName(entryPoint);

            // ===> VERTEX STAGE <===

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            vertexInputInfo.pVertexBindingDescriptions(getBindingDescription(vertexFormat));
            vertexInputInfo.pVertexAttributeDescriptions(createAttributeDescription(vertexFormat));

            // ===> ASSEMBLY STAGE <===

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
            inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
            inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            inputAssembly.primitiveRestartEnable(false);

            // ===> VIEWPORT & SCISSOR

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack);
            viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);

            viewportState.viewportCount(1);
            viewportState.scissorCount(1);

            // ===> RASTERIZATION STAGE <===

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack);
            rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
            rasterizer.depthClampEnable(false);
            rasterizer.rasterizerDiscardEnable(false);
            rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
            rasterizer.lineWidth(1.0f);

            if(pipelineDescription.cullMode == CullMode.ENABLE) rasterizer.cullMode(VK_CULL_MODE_BACK_BIT);
            else rasterizer.cullMode(VK_CULL_MODE_NONE);

            rasterizer.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);
            rasterizer.depthBiasEnable(true);

            // ===> MULTISAMPLING <===

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack);
            multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            multisampling.sampleShadingEnable(false);
            multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // ===> DEPTH TEST <===

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
            depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
            depthStencil.depthTestEnable(pipelineDescription.depthFunc != DepthFunc.ALWAYS);
            depthStencil.depthWriteEnable(pipelineDescription.writeMask.depth());
            depthStencil.depthCompareOp(VkEnum.from(pipelineDescription.depthFunc));
            depthStencil.depthBoundsTestEnable(false);
            depthStencil.minDepthBounds(0.0f); // Optional
            depthStencil.maxDepthBounds(1.0f); // Optional
            depthStencil.stencilTestEnable(false);

            // ===> COLOR BLENDING <===

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.colorWriteMask(pipelineDescription.writeMask.color()?VK_COLOR_COMPONENT_R_BIT|VK_COLOR_COMPONENT_G_BIT|VK_COLOR_COMPONENT_B_BIT|VK_COLOR_COMPONENT_A_BIT:0);

            if(pipelineDescription.blendFunc != null) {
                colorBlendAttachment.blendEnable(true);
                colorBlendAttachment.srcColorBlendFactor(VkEnum.from(pipelineDescription.blendFunc.srcRGB));
                colorBlendAttachment.dstColorBlendFactor(VkEnum.from(pipelineDescription.blendFunc.dstRGB));
                colorBlendAttachment.colorBlendOp(VK_BLEND_OP_ADD);
                colorBlendAttachment.srcAlphaBlendFactor(VkEnum.from(pipelineDescription.blendFunc.srcAlpha));
                colorBlendAttachment.dstAlphaBlendFactor(VkEnum.from(pipelineDescription.blendFunc.dstAlpha));
                colorBlendAttachment.alphaBlendOp(VK_BLEND_OP_ADD);
            }
            else {
                colorBlendAttachment.blendEnable(false);
            }

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack);
            colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
            colorBlending.logicOpEnable(false);
            colorBlending.logicOp(0);
            colorBlending.pAttachments(colorBlendAttachment);
            colorBlending.blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            // ===> DYNAMIC STATES <===

            VkPipelineDynamicStateCreateInfo dynamicStates = VkPipelineDynamicStateCreateInfo.calloc(stack);
            dynamicStates.sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
            dynamicStates.pDynamicStates(stack.ints(VK_DYNAMIC_STATE_DEPTH_BIAS, VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
            pipelineInfo.pStages(shaderStages);
            pipelineInfo.pVertexInputState(vertexInputInfo);
            pipelineInfo.pInputAssemblyState(inputAssembly);
            pipelineInfo.pViewportState(viewportState);
            pipelineInfo.pRasterizationState(rasterizer);
            pipelineInfo.pMultisampleState(multisampling);
            pipelineInfo.pDepthStencilState(depthStencil);
            pipelineInfo.pColorBlendState(colorBlending);
            pipelineInfo.pDynamicState(dynamicStates);
            pipelineInfo.layout(pipelineLayout);
            pipelineInfo.renderPass(VkContextTEMP.getRenderPass());
            pipelineInfo.subpass(0);
            pipelineInfo.basePipelineHandle(VK_NULL_HANDLE);
            pipelineInfo.basePipelineIndex(-1);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);

            if(vkCreateGraphicsPipelines(VkContextTEMP.getDevice(), pipelineCache, pipelineInfo, null, pGraphicsPipeline) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            graphicsPipeline = pGraphicsPipeline.get(0);
        }
    }

    private static final long pipelineCache = createPipelineCache();
    private static long createPipelineCache() {
        try(MemoryStack stack = stackPush()) {

            VkPipelineCacheCreateInfo cacheCreateInfo = VkPipelineCacheCreateInfo.callocStack(stack);
            cacheCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);

            LongBuffer pPipelineCache = stack.mallocLong(1);

            if(vkCreatePipelineCache(VkContextTEMP.getDevice(), cacheCreateInfo, null, pPipelineCache) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            return pPipelineCache.get(0);
        }
    }
}
