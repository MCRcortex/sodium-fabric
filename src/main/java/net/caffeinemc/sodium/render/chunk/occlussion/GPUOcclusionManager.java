package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.buffer.*;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.Pipeline;
import net.caffeinemc.gfx.api.pipeline.PipelineDescription;
import net.caffeinemc.gfx.api.pipeline.state.WriteMask;
import net.caffeinemc.gfx.api.shader.Program;
import net.caffeinemc.gfx.api.shader.ShaderDescription;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.gfx.api.types.ElementFormat;
import net.caffeinemc.gfx.api.types.PrimitiveType;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class GPUOcclusionManager {
    private RenderDevice device;
    private Program<RasterCullerInterface> rasterCullProgram;
    private Program<CommandGeneratorInterface> commandGeneratorProgram;
    private Pipeline<RasterCullerInterface, EmptyTarget> rasterCullPipeline;
    private Pipeline<RasterCullerInterface, EmptyTarget> commandGeneratorPipeline;
    private final ImmutableBuffer indexBuffer;
    private final MappedBuffer sceneBuffer;
    public GPUOcclusionManager(RenderDevice device) {
        this.device = device;


        var vertexArray = new VertexArrayDescription<>(EmptyTarget.values(), List.of());


        ShaderConstants constants = ShaderConstants.builder().build();
        this.rasterCullProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.VERTEX,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/raster_depth_test.vert"), constants))
                .addShaderSource(ShaderType.FRAGMENT,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/raster_depth_test.frag"), constants))
                .build(), RasterCullerInterface::new);

        this.rasterCullPipeline = device.createPipeline(PipelineDescription.builder()
                .setWriteMask(new WriteMask(false, false))
                .build(),
                this.rasterCullProgram,
                vertexArray);

        //FIXME: see if its possible to just put the compute source shader thing in the rasterCullPipeline
        this.commandGeneratorProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/command_generator.comp"), constants))
                .build(), CommandGeneratorInterface::new);


        //TODO: create this except with all 2^6 varients of face visibility (except 0 of course)
        // this way face culling for testing can be done
        /*
          4_________5
         /.        /|
        6_________7 |
        | 0.......|.1
        |.        |/
        2_________3
        */
        ByteBuffer indices = ByteBuffer.allocateDirect(3*2*6);
        //Front face
        indices.put((byte) 0); indices.put((byte) 1); indices.put((byte) 2);
        indices.put((byte) 2); indices.put((byte) 3); indices.put((byte) 0);

        //right face
        indices.put((byte) 1); indices.put((byte) 5); indices.put((byte) 6);
        indices.put((byte) 6); indices.put((byte) 2); indices.put((byte) 1);

        //Back face
        indices.put((byte) 7); indices.put((byte) 6); indices.put((byte) 5);
        indices.put((byte) 5); indices.put((byte) 4); indices.put((byte) 7);

        //Left face
        indices.put((byte) 4); indices.put((byte) 0); indices.put((byte) 3);
        indices.put((byte) 3); indices.put((byte) 7); indices.put((byte) 4);

        //Bottom face
        indices.put((byte) 4); indices.put((byte) 5); indices.put((byte) 1);
        indices.put((byte) 1); indices.put((byte) 0); indices.put((byte) 4);

        //Top face
        indices.put((byte) 3); indices.put((byte) 2); indices.put((byte) 6);
        indices.put((byte) 6); indices.put((byte) 7); indices.put((byte) 3);
        indices.rewind();
        indexBuffer = device.createBuffer(indices, Set.of());

        sceneBuffer = device.createMappedBuffer(4*4*4+3*4, Set.of(MappedBufferFlags.WRITE));
    }

    //Either use glClearBuffer or something faster to clear the atomic counters of all the regions render data
    public void clearRenderCommandCounters(List<RenderRegion> regions) {
        //Could technically be a compute shader
    }

    //FIXME: do backplane culling on these cubes that are being rendered should provide and okish speed boost
    public void computeOcclusionVis(Collection<RenderRegion> regions, Frustum frustum) {
        this.device.usePipeline(this.rasterCullPipeline,  (cmd, programInterface, pipelineState) -> {
            new Matrix4f().getToAddress( MemoryUtil.memAddress(sceneBuffer.view()));


            pipelineState.bindBufferBlock(programInterface.scene, this.sceneBuffer);
            cmd.bindElementBuffer(this.indexBuffer);
            for (RenderRegion region : regions) {
                pipelineState.bindBufferBlock(programInterface.meta, region.metaBuffer.getBuffer());
                pipelineState.bindBufferBlock(programInterface.visbuff, region.visBuffer);
                cmd.drawElementsInstanced(PrimitiveType.TRIANGLES, 6*6, ElementFormat.UNSIGNED_BYTE, 0, 10);
            }
        });
    }

    public void fillRenderCommands(List<RenderRegion> regions) {
        //For each region have a prebaked MultiBlockBind ready, when using the compute pipeline, bind this block for each region,
        // (of course keeping the scene uniform the same)
        // then dispatch compute per region with dispatch size being basicly maxSectionId/local_compute_size
    }

    enum EmptyTarget {

    }
}
