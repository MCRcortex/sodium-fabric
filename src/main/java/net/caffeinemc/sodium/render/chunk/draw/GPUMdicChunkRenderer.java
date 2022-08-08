package net.caffeinemc.sodium.render.chunk.draw;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.device.commands.RenderCommandList;
import net.caffeinemc.gfx.api.pipeline.PipelineState;
import net.caffeinemc.gfx.api.pipeline.RenderPipeline;
import net.caffeinemc.gfx.api.types.ElementFormat;
import net.caffeinemc.gfx.api.types.PrimitiveType;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.ViewportedData;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.render.terrain.format.compact.CompactTerrainVertexType;
import net.minecraft.client.render.RenderLayer;

public class GPUMdicChunkRenderer extends AbstractMdChunkRenderer {
    public GPUMdicChunkRenderer(RenderDevice device, ChunkRenderPassManager renderPassManager, TerrainVertexType vertexType) {
        super(device, renderPassManager, vertexType);
    }

    @Override
    public void createRenderLists(SortedTerrainLists lists, ChunkCameraContext camera, int frameIndex) {

    }

    @Override
    public void render(ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex) {
        int passId = renderPass.getId();
        if (passId < 0) {
            return;
        }
        RenderPipeline<ChunkShaderInterface, BufferTarget> renderPipeline = this.renderPipelines[passId];
        if (renderPass != renderPassManager.getRenderPassForLayer(RenderLayer.getSolid()))
            return;
        indexBuffer.ensureCapacity(100000);
        this.device.useRenderPipeline(renderPipeline, (commandList, programInterface, pipelineState) -> {
            this.setupTextures(renderPass, pipelineState);
            this.setupUniforms(matrices, programInterface, pipelineState, frameIndex);

            var viewport = ViewportedData.DATA.get();
            commandList.bindVertexBuffer(
                    BufferTarget.VERTICES,
                    SodiumWorldRenderer.instance().getGlobalVertexBufferTHISISTEMPORARY(),
                    0,
                    CompactTerrainVertexType.VERTEX_FORMAT.stride()
            );

            pipelineState.bindBufferBlock(
                    programInterface.ssboChunkTransforms,
                    viewport.chunkInstancedDataBuffer,
                    0,
                    OcclusionEngine.MAX_VISIBLE_SECTIONS*(4*4)
            );

            commandList.bindCommandBuffer(viewport.commandOutputBuffer);
            commandList.bindParameterBuffer(viewport.commandBufferCounter);
            commandList.bindElementBuffer(this.indexBuffer.getBuffer());
            commandList.multiDrawElementsIndirectCount(
                    PrimitiveType.TRIANGLES,
                    ElementFormat.UNSIGNED_INT,
                    0,
                    4,
                    (int)(viewport.cpuCommandBufferCounter.view().getInt(4)*1.5),
                    20);
            commandList.multiDrawElementsIndirectCount(
                    PrimitiveType.TRIANGLES,
                    ElementFormat.UNSIGNED_INT,
                    OcclusionEngine.MAX_RENDER_COMMANDS_PER_LAYER*OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE,
                    8,
                    (int)(viewport.cpuCommandBufferCounter.view().getInt(8)*1.5),
                    20);
        });
    }

    @Override
    protected ShaderConstants.Builder addAdditionalShaderConstants(ShaderConstants.Builder constants) {
        constants.add("BASE_INSTANCE_INDEX");
        //TODO: this depends on the mode, if the backend uses a global vertex allocation then use this, else use uniforms
        constants.add("SSBO_MODEL_TRANSFORM");
        //constants.add("MAX_BATCH_SIZE", String.valueOf(OcclusionEngine.MAX_VISIBLE_SECTIONS));
        return constants;
    }

    @Override
    public String getDebugName() {
        return null;
    }
}
