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
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.ViewportedData;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11C.*;

public class RasterRegionShader {

    private static final class RasterCullerInterface {
        public final BufferBlock scene;
        public final BufferBlock regionArray;
        public final BufferBlock regionMeta;
        public final BufferBlock visArray;
        public RasterCullerInterface(ShaderBindingContext context) {
            //TODO: change scene to a uniform
            scene = context.bindBufferBlock(BufferBlockType.UNIFORM, 0);
            regionArray = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
            regionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
            visArray = context.bindBufferBlock(BufferBlockType.STORAGE, 3);
        }
    }

    private final RenderDevice device;
    private final Program<RasterCullerInterface> rasterCullProgram;
    private final RenderPipeline<RasterCullerInterface, EmptyTarget> rasterCullPipeline;

    public RasterRegionShader(RenderDevice device) {
        this.device = device;

        var vertexArray = new VertexArrayDescription<>(EmptyTarget.values(), List.of());

        ShaderConstants constants = ShaderConstants.builder()
                .add("MAX_REGIONS", String.valueOf(OcclusionEngine.MAX_REGIONS))
                .build();
        this.rasterCullProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.VERTEX,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/raster/region.vert"), constants))
                .addShaderSource(ShaderType.FRAGMENT,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/raster/region.frag"), constants))
                .build(), RasterCullerInterface::new);

        this.rasterCullPipeline = device.createRenderPipeline(RenderPipelineDescription.builder()
                        .setWriteMask(new WriteMask(false, false))
                        .build(),
                this.rasterCullProgram, vertexArray);
    }


    //Note: an exact number of calls are used as parameter draw takes too long, this is ok as long as the region compute
    // emits exactly regionCount calls which it should always do as long as it emits a null draw call on non visibility
    public void execute(int regionCount, Buffer scene, int offset, Buffer regionArray, Buffer regionMeta, Buffer regionVisibilityArray) {
        device.useRenderPipeline(rasterCullPipeline, (cmd, programInterface, pipelineState) -> {
            cmd.bindElementBuffer(CubeIndexBuffer.INDEX_BUFFER);
            pipelineState.bindBufferBlock(programInterface.scene, scene, offset, ViewportedData.SCENE_STRUCT_ALIGNMENT);
            pipelineState.bindBufferBlock(programInterface.regionArray, regionArray);
            pipelineState.bindBufferBlock(programInterface.regionMeta, regionMeta);
            pipelineState.bindBufferBlock(programInterface.visArray, regionVisibilityArray);

            //TODO: change from triangles to like triangle fan or something, then on nvidia enable representitive fragment
            // tests
            cmd.drawElementsInstanced(PrimitiveType.TRIANGLES, ElementFormat.UNSIGNED_BYTE, 3*2*6, 0, regionCount);
        });
    }

    public void delete() {
        device.deleteProgram(rasterCullProgram);
        device.deleteRenderPipeline(rasterCullPipeline);
    }
}
