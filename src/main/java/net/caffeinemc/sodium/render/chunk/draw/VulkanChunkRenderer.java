package net.caffeinemc.sodium.render.chunk.draw;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorSetLayoutBuilder;
import me.cortex.vulkanitelib.pipelines.VVkPipeline;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;
import me.cortex.vulkanitelib.pipelines.VVkShader;
import me.cortex.vulkanitelib.pipelines.builders.GraphicsPipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.RenderPassBuilder;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.RenderPipeline;
import net.caffeinemc.gfx.api.pipeline.state.BlendFunc;
import net.caffeinemc.gfx.api.shader.Program;
import net.caffeinemc.gfx.api.shader.ShaderDescription;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderer;
import net.caffeinemc.sodium.render.chunk.draw.SortedTerrainLists;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.vk.VulkanContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import static org.lwjgl.vulkan.VK10.*;

public class VulkanChunkRenderer implements ChunkRenderer {
    private final RenderDevice glDevice;
    private final VVkDevice device = VulkanContext.device;
    protected final VVkPipeline[] renderPipelines;
    public VulkanChunkRenderer(RenderDevice glDevice, ChunkCameraContext camera, ChunkRenderPassManager renderPassManager, TerrainVertexType vertexType) {
        this.glDevice = glDevice;

        renderPipelines = new VVkPipeline[renderPassManager.getRenderPassCount()];

        VVkRenderPass renderPass = device.build(new RenderPassBuilder()
                .attachment(VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .attachment(VK_FORMAT_D24_UNORM_S8_UINT, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .subpass(VK_PIPELINE_BIND_POINT_GRAPHICS, 1,0));

        VVkDescriptorSetLayout uniformLayout = device.build(new DescriptorSetLayoutBuilder()
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT)//CameraMatrices
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT)//ChunkTransforms
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT)//FogParameters
                .binding(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)//tex_diffuse
                .binding(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_VERTEX_BIT)//tex_light
        );

        GraphicsPipelineBuilder pipelineBuilder = new GraphicsPipelineBuilder()
                //Doing compact format
                .addVertexInput(0, vertexType.getBufferVertexFormat().stride(), input->
                    input.attribute(VK_FORMAT_R16G16B16_SNORM,0)//POSITION
                            .attribute(VK_FORMAT_R8G8B8A8_UNORM, 8)//COLOR
                            .attribute(VK_FORMAT_R16G16_UNORM, 12)//BLOCK_TEXTURE
                            .attribute(VK_FORMAT_R16G16_SINT,16)//LIGHT_TEXTURE
                )
                .set(renderPass)
                .add(uniformLayout)
                .addDynamicStates(VK_DYNAMIC_STATE_DEPTH_BIAS, VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)
                .rasterization(true, false, VK_CULL_MODE_BACK_BIT)
                .inputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .multisampling()
                .addViewport()
                .addScissor()
                .depthStencil();

        for (ChunkRenderPass chunkRenderPass : renderPassManager.getAllRenderPasses()) {

            var constants = this.addAdditionalShaderConstants(getBaseShaderConstants(chunkRenderPass, vertexType)).build();

            var vertShader = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.vert"),
                    constants
            );
            var fragShader = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.frag"),
                    constants
            );

            VVkShader vert = device.compileShader(vertShader, VK_SHADER_STAGE_VERTEX_BIT);
            VVkShader frag = device.compileShader(fragShader, VK_SHADER_STAGE_FRAGMENT_BIT);
            pipelineBuilder.clearShaders()
                    .add(vert).add(frag);

            BlendFunc bf = chunkRenderPass.getPipelineDescription().blendFunc;//Ignoring the others atm cause not used
            if (bf != null) {
                pipelineBuilder.colourBlending().attachment();//FIXME: NEED TO ADD MAPPING OF TYPE
            } else
                pipelineBuilder.colourBlending().attachment();

            this.renderPipelines[chunkRenderPass.getId()] = device.build(pipelineBuilder);
        }
    }

    protected static ShaderConstants.Builder getBaseShaderConstants(ChunkRenderPass pass, TerrainVertexType vertexType) {
        var constants = ShaderConstants.builder();

        if (pass.isCutout()) {
            constants.add("ALPHA_CUTOFF", String.valueOf(pass.getAlphaCutoff()));
        }

        if (!MathHelper.approximatelyEquals(vertexType.getVertexRange(), 1.0f)) {
            constants.add("VERT_SCALE", String.valueOf(vertexType.getVertexRange()));
        }

        return constants;
    }

    protected ShaderConstants.Builder addAdditionalShaderConstants(ShaderConstants.Builder constants) {
        constants.add("BASE_INSTANCE_INDEX");
        constants.add("MAX_BATCH_SIZE", String.valueOf(RenderRegion.REGION_SIZE));
        return constants; // NOOP, override if needed
    }

    @Override
    public void createRenderLists(SortedTerrainLists lists, int frameIndex) {

    }

    @Override
    public void render(ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex) {

    }

    @Override
    public void delete() {

    }

    @Override
    public int getDeviceBufferObjects() {
        return 0;
    }

    @Override
    public long getDeviceUsedMemory() {
        return 0;
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return 0;
    }

    @Override
    public String getDebugName() {
        return "Vulkan";
    }
}
