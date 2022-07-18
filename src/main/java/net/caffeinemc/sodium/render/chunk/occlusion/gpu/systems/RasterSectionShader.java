package net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems;

import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.RenderPipeline;
import net.caffeinemc.gfx.api.pipeline.RenderPipelineDescription;
import net.caffeinemc.gfx.api.pipeline.state.WriteMask;
import net.caffeinemc.gfx.api.shader.*;
import net.caffeinemc.gfx.api.types.ElementFormat;
import net.caffeinemc.gfx.api.types.PrimitiveType;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.CubeIndexBuffer;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public class RasterSectionShader {
    private static final class RasterCullerInterface {
        public final BufferBlock scene;
        public final BufferBlock sectionMeta;
        public final BufferBlock visbuff;

        public RasterCullerInterface(ShaderBindingContext context) {
            //TODO: change scene to a uniform
            scene = context.bindBufferBlock(BufferBlockType.STORAGE, 0);
            sectionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
            visbuff = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
        }
    }

    private final RenderDevice device;
    private final Program<RasterCullerInterface> rasterCullProgram;
    private final RenderPipeline<RasterCullerInterface, EmptyTarget> rasterCullPipeline;

    public RasterSectionShader(RenderDevice device) {
        this.device = device;

        var vertexArray = new VertexArrayDescription<>(EmptyTarget.values(), List.of());

        ShaderConstants constants = ShaderConstants.builder().build();
        this.rasterCullProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.VERTEX,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/raster/section.vert"), constants))
                .addShaderSource(ShaderType.FRAGMENT,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/raster/section.frag"), constants))
                .build(), RasterCullerInterface::new);

        this.rasterCullPipeline = device.createRenderPipeline(RenderPipelineDescription.builder()
                        .setWriteMask(new WriteMask(false, false))
                        .build(),
                this.rasterCullProgram, vertexArray);

    }


    //Note: an exact number of calls are used as parameter draw takes too long, this is ok as long as the region compute
    // emits exactly regionCount calls which it should always do as long as it emits a null draw call on non visibility
    public void execute(Buffer renderCommands, int regionCount, Buffer scene, Buffer sectionMeta, Buffer visibilityBuffer) {
        device.useRenderPipeline(rasterCullPipeline, (cmd, programInterface, pipelineState) -> {
            cmd.bindCommandBuffer(renderCommands);
            cmd.bindElementBuffer(CubeIndexBuffer.INDEX_BUFFER);
            pipelineState.bindBufferBlock(programInterface.scene, scene);
            pipelineState.bindBufferBlock(programInterface.sectionMeta, sectionMeta);
            pipelineState.bindBufferBlock(programInterface.visbuff, visibilityBuffer);
            //TODO: change from triangles to like triangle fan or something, then on nvidia enable representitive fragment
            // tests
            cmd.multiDrawElementsIndirect(PrimitiveType.TRIANGLES, ElementFormat.UNSIGNED_BYTE, 0, regionCount, 5*4);
        });
    }
}
