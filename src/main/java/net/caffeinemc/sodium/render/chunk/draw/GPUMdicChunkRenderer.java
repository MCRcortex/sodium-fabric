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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;

import static org.lwjgl.opengl.GL11.glFinish;

public class GPUMdicChunkRenderer extends AbstractMdChunkRenderer {
    public GPUMdicChunkRenderer(RenderDevice device, ChunkRenderPassManager renderPassManager, TerrainVertexType vertexType) {
        super(device, renderPassManager, vertexType);
    }

    @Override
    public void createRenderLists(SortedTerrainLists lists, ChunkCameraContext camera, int frameIndex) {

    }

    @Override
    public void render(ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex) {
        //if (MinecraftClient.getInstance().player.isSneaking())
        //    return;
        int passId = renderPass.getId();
        if (passId < 0) {
            return;
        }
        RenderPipeline<ChunkShaderInterface, BufferTarget> renderPipeline = this.renderPipelines[passId];
        if (passId>3)
            return;

        indexBuffer.ensureCapacity(100000);
        this.device.useRenderPipeline(renderPipeline, (commandList, programInterface, pipelineState) -> {
            var viewport = ViewportedData.DATA.get();
            this.setupTextures(renderPass, pipelineState);

            //TODO:FIXME: fix this ugly af hacks
            ChunkCameraContext c;
            if (renderPass.isTranslucent()) {
                c = new ChunkCameraContext((-viewport.frameDeltaX),
                        (-viewport.frameDeltaY),
                        (-viewport.frameDeltaZ));
            } else {
                c = new ChunkCameraContext((viewport.frameDeltaX),
                        (viewport.frameDeltaY),
                        (viewport.frameDeltaZ));
            }
            float dx = (float) (c.deltaX + c.blockX);
            float dy = (float) (c.deltaY + c.blockY);
            float dz = (float) (c.deltaZ + c.blockZ);
            matrices.modelView().translate(dx,
                    dy,
                    dz
            );
            this.setupUniforms(matrices, programInterface, pipelineState, frameIndex);

            commandList.bindVertexBuffer(
                    BufferTarget.VERTICES,
                    SodiumWorldRenderer.instance().getGlobalVertexBufferTHISISTEMPORARY(),
                    0,
                    CompactTerrainVertexType.VERTEX_FORMAT.stride()
            );

            pipelineState.bindBufferBlock(
                    programInterface.ssboChunkTransforms,
                    viewport.chunkInstancedDataBuffer
            );

            commandList.bindElementBuffer(this.indexBuffer.getBuffer());
            if (!renderPass.isTranslucent()) {
                int count = viewport.cpuCommandBufferCounter.view().getInt(passId * 4 + 4);
                //if (passId == 0)
                //    System.out.println(count);
                if (count <= 0 || count > 100000)
                    return;
                commandList.bindCommandBuffer(viewport.commandOutputBuffer);
                commandList.bindParameterBuffer(viewport.commandBufferCounter);
                commandList.multiDrawElementsIndirectCount(
                        PrimitiveType.TRIANGLES,
                        ElementFormat.UNSIGNED_INT,
                        OcclusionEngine.MAX_RENDER_COMMANDS_PER_LAYER * passId * OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE,
                        4 + passId * 4,
                        //100000,
                        (int) (count * viewport.countMultiplier),
                        (int) OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE);
            } else {
                commandList.bindCommandBuffer(viewport.translucencyCommandBuffer);
                commandList.bindParameterBuffer(viewport.translucencyCountBuffer);
                for (int i = 49; i >= 0; i--) {
                    //FIXME: count should be the max of +-2 of the current i index
                    int count = viewport.cpuTranslucencyCountBuffer.view().getInt(i*4);
                    if (count <= 0 || count >= 100)
                        continue;
                    commandList.multiDrawElementsIndirectCount(
                            PrimitiveType.TRIANGLES,
                            ElementFormat.UNSIGNED_INT,
                            i*100*OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE,
                            i*4L,
                            (int) (count*viewport.countMultiplier),
                            (int) OcclusionEngine.MULTI_DRAW_INDIRECT_COMMAND_SIZE);
                }
            }
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
        return "GPU Occlusion";
    }
}
