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
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.Chunk;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL45C.glClearNamedBufferData;

public class GPUOcclusionManager {
    private RenderDevice device;
    private Program<RasterCullerInterface> rasterCullProgram;
    private Program<CommandGeneratorInterface> commandGeneratorProgram;
    private Pipeline<RasterCullerInterface, EmptyTarget> rasterCullPipeline;
    private Pipeline<CommandGeneratorInterface, EmptyTarget> commandGeneratorPipeline;
    private final ImmutableBuffer indexBuffer;
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
                //.setWriteMask(new WriteMask(false, false))
                .build(),
                this.rasterCullProgram,
                vertexArray);

        //FIXME: see if its possible to just put the compute source shader thing in the rasterCullPipeline
        this.commandGeneratorProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/command_generator.comp"), constants))
                .build(), CommandGeneratorInterface::new);

        this.commandGeneratorPipeline = device.createPipeline(PipelineDescription.builder()
                        .setWriteMask(new WriteMask(false, false))
                        //.setWriteMask(new WriteMask(false, false))
                        .build(),
                this.commandGeneratorProgram,
                vertexArray);


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
        //Bottom face
        indices.put((byte) 0); indices.put((byte) 1); indices.put((byte) 2);
        indices.put((byte) 1); indices.put((byte) 3); indices.put((byte) 2);

        //right face
        indices.put((byte) 0); indices.put((byte) 2); indices.put((byte) 6);
        indices.put((byte) 6); indices.put((byte) 4); indices.put((byte) 0);

        //Back face
        indices.put((byte) 0); indices.put((byte) 4); indices.put((byte) 5);
        indices.put((byte) 5); indices.put((byte) 1); indices.put((byte) 0);

        //Left face
        indices.put((byte) 1); indices.put((byte) 5); indices.put((byte) 7);
        indices.put((byte) 7); indices.put((byte) 3); indices.put((byte) 1);

        //Bottom face
        indices.put((byte) 4); indices.put((byte) 6); indices.put((byte) 7);
        indices.put((byte) 7); indices.put((byte) 5); indices.put((byte) 4);

        //Top face
        indices.put((byte) 2); indices.put((byte) 7); indices.put((byte) 6);
        indices.put((byte) 2); indices.put((byte) 3); indices.put((byte) 7);
        indices.position(0);
        indexBuffer = device.createBuffer(indices, Set.of());

    }

    //Either use glClearBuffer or something faster to clear the atomic counters of all the regions render data
    public void clearRenderCommandCounters(List<RenderRegion> regions) {
        //Could technically be a compute shader
    }

    //FIXME: do backplane culling on these cubes that are being rendered should provide and okish speed boost

    //FIXME cull regions that are not in frustum or are empty or have no geometry ready/after prelim region face cull

    //Fixme: need to use ChunkCameraContext
    public void computeOcclusionVis(Collection<RenderRegion> regions, ChunkRenderMatrices matrices, ChunkCameraContext cam) {
        for (RenderRegion region : regions) {
            //FIXME: move this to an outer/another loop that way driver has time to flush the data
            Vector3f campos = new Vector3f(region.getMinAsBlock().sub(cam.blockX, cam.blockY, cam.blockZ)).sub(cam.deltaX, cam.deltaY, cam.deltaZ);
            Matrix4f mvp = new Matrix4f(matrices.projection())
                    .mul(matrices.modelView())
                    .translate(campos);

            mvp.getToAddress(MemoryUtil.memAddress(region.sceneBuffer.view()));
            campos.getToAddress(MemoryUtil.memAddress(region.sceneBuffer.view())+4*4*4);
            region.sceneBuffer.flush();
            //FIXME: clear  region.counterBuffer
            //System.out.println(region.instanceBuffer.view().getFloat(0));
            //FIXME: put into gfx
            glClearNamedBufferData(GlBuffer.getHandle(region.counterBuffer),  GL_R32UI,GL_RED, GL_UNSIGNED_INT, new int[]{0});
        }
        //GL11.glFinish();

        //glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        this.device.usePipeline(this.rasterCullPipeline,  (cmd, programInterface, pipelineState) -> {
            cmd.bindElementBuffer(this.indexBuffer);
            for (RenderRegion region : regions) {


                pipelineState.bindBufferBlock(programInterface.scene, region.sceneBuffer);

                pipelineState.bindBufferBlock(programInterface.meta, region.metaBuffer.getBuffer());
                pipelineState.bindBufferBlock(programInterface.visbuff, region.visBuffer);
                cmd.drawElementsInstanced(PrimitiveType.TRIANGLES, 6*6, ElementFormat.UNSIGNED_BYTE, 0, region.sectionCount);
            }
        });
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        this.device.usePipeline(this.commandGeneratorPipeline, (cmd, programInterface, pipelineState) -> {
            for (RenderRegion region : regions) {
                pipelineState.bindBufferBlock(programInterface.scene, region.sceneBuffer);
                pipelineState.bindBufferBlock(programInterface.meta, region.metaBuffer.getBuffer());
                pipelineState.bindBufferBlock(programInterface.visbuff, region.visBuffer);

                pipelineState.bindBufferBlock(programInterface.counter, region.counterBuffer);
                pipelineState.bindBufferBlock(programInterface.instancedata, region.instanceBuffer);
                pipelineState.bindBufferBlock(programInterface.cmdbuffs[0], region.cmd0buff);
                cmd.dispatchCompute((int)Math.ceil((double) region.sectionCount/32),1,1);
            }
        });
        //glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void fillRenderCommands(List<RenderRegion> regions) {
        //For each region have a prebaked MultiBlockBind ready, when using the compute pipeline, bind this block for each region,
        // (of course keeping the scene uniform the same)
        // then dispatch compute per region with dispatch size being basicly maxSectionId/local_compute_size
    }

    enum EmptyTarget {

    }
}
