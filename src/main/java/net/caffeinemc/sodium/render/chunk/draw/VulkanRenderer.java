package net.caffeinemc.sodium.render.chunk.draw;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.SVkGraphicsPipelineBuilder;
import net.caffeinemc.sodium.vkinterop.vk.pipeline.SVkShader;
import net.minecraft.util.Identifier;
import org.lwjgl.vulkan.VkDevice;

import static net.caffeinemc.sodium.render.chunk.draw.AbstractMdChunkRenderer.getBaseShaderConstants;

public class VulkanRenderer implements ChunkRenderer {
    protected final RenderDevice glDevice;
    protected final SVkDevice device;
    protected final ChunkCameraContext camera;
    protected final ChunkRenderPassManager renderPassManager;

    public VulkanRenderer(RenderDevice glDevice, ChunkCameraContext camera, ChunkRenderPassManager renderPassManager, TerrainVertexType vertexType) {
        this.glDevice = glDevice;
        this.camera = camera;
        this.device = SVkDevice.INSTANCE;
        this.renderPassManager = renderPassManager;


        for (ChunkRenderPass renderPass : renderPassManager.getAllRenderPasses()) {
            var constants = this.addAdditionalShaderConstants(getBaseShaderConstants(renderPass, vertexType)).build();

            var vertShaderSource = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.vert"),
                    constants
            );
            var fragShaderSource = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.frag"),
                    constants
            );
            var vertShader = new SVkShader(SVkDevice.INSTANCE, vertShaderSource, ShaderType.VERTEX);
            var fragShader = new SVkShader(SVkDevice.INSTANCE, fragShaderSource, ShaderType.FRAGMENT);
            //new SVkGraphicsPipelineBuilder().createPipeline();
        }
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
