package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.array.VertexArrayResourceBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttribute;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeFormat;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.Pipeline;
import net.caffeinemc.gfx.api.pipeline.PipelineDescription;
import net.caffeinemc.gfx.api.pipeline.state.WriteMask;
import net.caffeinemc.gfx.api.shader.Program;
import net.caffeinemc.gfx.api.shader.ShaderDescription;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.gfx.opengl.shader.GlProgram;
import net.caffeinemc.sodium.render.chunk.draw.DefaultChunkRenderer;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderBindingPoints;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.minecraft.util.Identifier;

import java.util.List;

public class GPUOcclusionManager {
    private RenderDevice device;
    private Program<RasterCullerInterface> program;
    private Pipeline<RasterCullerInterface, DefaultChunkRenderer.BufferTarget> pipeline;
    public GPUOcclusionManager(RenderDevice device) {
        this.device = device;


        var vertexArray = new VertexArrayDescription<>(DefaultChunkRenderer.BufferTarget.values(), List.of(
                new VertexArrayResourceBinding<>(DefaultChunkRenderer.BufferTarget.VERTICES, new VertexAttributeBinding[] {
                        new VertexAttributeBinding(0,
                                new VertexAttribute(
                                        VertexAttributeFormat.FLOAT,
                                        3,
                                        false,
                                        0,
                                        false)),
                })
        ));


        ShaderConstants constants = ShaderConstants.builder().build();
        this.program = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.VERTEX,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/rasterDepthTest.vert"), constants))
                .addShaderSource(ShaderType.FRAGMENT,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/rasterDepthTest.frag"), constants))
                .build(), RasterCullerInterface::new);
        this.pipeline = device.createPipeline(PipelineDescription.builder()
                .setWriteMask(new WriteMask(false, false))
                .build(),
                this.program,
                vertexArray);
    }

    public void clearRenderCommandCounters(List<RenderRegion> regions) {

    }

    public void computeOcclusionVis(List<RenderRegion> regions) {
        this.device.usePipeline(this.pipeline,  (cmd, programInterface, pipelineState) -> {

        });
    }

    public void fillRenderCommands(List<RenderRegion> regions) {

    }

}
