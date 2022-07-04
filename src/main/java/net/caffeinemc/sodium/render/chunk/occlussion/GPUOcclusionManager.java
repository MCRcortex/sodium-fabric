package net.caffeinemc.sodium.render.chunk.occlussion;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import net.caffeinemc.sodium.render.chunk.ViewportInterface;
import net.caffeinemc.sodium.render.chunk.ViewportedData;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.Chunk;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.GL45C.glClearNamedBufferData;
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;

//TODO: if a chunk is completly covered on all faces, remove it from the section list
// this should improve performance by some amount
public class GPUOcclusionManager {
    private RenderDevice device;
    private Program<RasterCullerInterface> rasterCullProgram;
    private Program<CommandGeneratorInterface> commandGeneratorProgram;
    private Program<TranslucentCommandGeneratorInterface> translucentCommandGeneratorProgram;
    private Pipeline<RasterCullerInterface, EmptyTarget> rasterCullPipeline;
    private Pipeline<CommandGeneratorInterface, EmptyTarget> commandGeneratorPipeline;
    private Pipeline<TranslucentCommandGeneratorInterface, EmptyTarget> translucentCommandGeneratorPipeline;
    private final ImmutableBuffer indexBuffer;

    public TreeSet<RenderRegion> getVisRegion() {
        return ViewportedData.get().visible_regions;
    }

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



        this.translucentCommandGeneratorProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "cull/translucent_command_builder.comp"), constants))
                .build(), TranslucentCommandGeneratorInterface::new);

        this.translucentCommandGeneratorPipeline = device.createPipeline(PipelineDescription.builder()
                        .setWriteMask(new WriteMask(false, false))
                        .build(),
                this.translucentCommandGeneratorProgram,
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

    //TODO: can actually make a delta list that needs to be rendered, e.g. for sections that are visible this frame but not last frame
    // and emit those, thus keeping up the presence of all the frames
    public void computeOcclusionVis(Collection<RenderRegion> regions, ChunkRenderMatrices matrices, ChunkCameraContext cam, Frustum frustum) {
        MinecraftClient.getInstance().getProfiler().push("Compute regions");
        var visRegion = getVisRegion();
        visRegion.clear();
        for (RenderRegion region : regions) {
            if (region.sectionCount == 0) {
                continue;
            }
            //if (region.getRenderData().cpuCommandCount.view().getInt(0) == 0) {
            //    continue;
            //}
            //FIXME: need to maybe cut up even more cause sometimes none of the corner points are visible
            Vector3i corner = region.getMinAsBlock();
            //FIXME: need to make a region bounding box for the min AABB of all the contained sections and use that

            if (!frustum.isBoxVisible(corner.x, corner.y, corner.z,
                    corner.x + RenderRegion.REGION_WIDTH * 16,
                    corner.y + RenderRegion.REGION_HEIGHT * 16,
                    corner.z + RenderRegion.REGION_LENGTH * 16)) {
                continue;
            }
            //When using RegionPreTester, need to check if its a new region thats visible in the frustum,
            // if it is, add it reguardless of visibility

            region.weight = new Vector3f(corner.x + RenderRegion.REGION_WIDTH * 8,
                    corner.y + RenderRegion.REGION_HEIGHT * 8,
                    corner.z + RenderRegion.REGION_LENGTH * 8)
                    .sub(cam.blockX, cam.blockY, cam.blockZ)
                    .sub(cam.deltaX, cam.deltaY, cam.deltaZ).lengthSquared();
            visRegion.add(region);
            region.onVisibleTick();
        }

        /*
        for (RenderRegion region : visRegions) {
            ByteBuffer cdib = region.computeDispatchIndirectBuffer.view().order(ByteOrder.nativeOrder());
            cdib.putInt(0, (int) Math.ceil((double) region.sectionCount / 32));
            cdib.putInt(4, 0);
            cdib.putInt(8, 1);
            region.computeDispatchIndirectBuffer.flush();
        }*/

        //TODO: disable multisample, and enable representitive pixel

        //GL11.glFinish();

        MinecraftClient.getInstance().getProfiler().swap("Update memory");
        for (RenderRegion region : visRegion) {
            //FIXME: move this to an outer/another loop that way driver has time to flush the data
            Vector3f campos = new Vector3f(region.getMinAsBlock().sub(cam.blockX, cam.blockY, cam.blockZ)).sub(cam.deltaX, cam.deltaY, cam.deltaZ);
            Matrix4f mvp = new Matrix4f(matrices.projection())
                    .mul(matrices.modelView())
                    .translate(campos);
            ByteBuffer bb = region.getRenderData().sceneBuffer.view().order(ByteOrder.nativeOrder());
            mvp.get(bb.asFloatBuffer());
            bb.position(4*4*4);
            campos.get(bb.asFloatBuffer());
            bb.putInt(4*4*4+4*3, //0
                            region.getRenderData().cpuCommandCount.view().getInt(0)!=0?region.translucentSections.intValue():0
                            );
            bb.rewind();
            region.getRenderData().sceneBuffer.flush();

            //FIXME: put into gfx
            glCopyNamedBufferSubData(GlBuffer.getHandle(region.getRenderData().counterBuffer), GlBuffer.getHandle(region.getRenderData().cpuCommandCount),4,0,4*4);
            //glFlush();
            //FIXME: put into gfx
            glClearNamedBufferData(GlBuffer.getHandle(region.getRenderData().cpuSectionVis),  GL_R32UI,GL_RED, GL_UNSIGNED_INT, new int[]{0});
            glClearNamedBufferData(GlBuffer.getHandle(region.getRenderData().counterBuffer),  GL_R32UI,GL_RED, GL_UNSIGNED_INT, new int[]{0});
            glClearNamedBufferData(GlBuffer.getHandle(region.getRenderData().visBuffer),  GL_R32UI,GL_RED, GL_UNSIGNED_INT, new int[]{0});
        }

        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        MinecraftClient.getInstance().getProfiler().swap("Raster vis");
        //glEnable(0x937F);
        this.device.usePipeline(this.rasterCullPipeline,  (cmd, programInterface, pipelineState) -> {
            cmd.bindElementBuffer(this.indexBuffer);
            for (RenderRegion region : visRegion) {
                pipelineState.bindBufferBlock(programInterface.scene, region.getRenderData().sceneBuffer);
                pipelineState.bindBufferBlock(programInterface.meta, region.metaBuffer.getBufferObject());
                pipelineState.bindBufferBlock(programInterface.visbuff, region.getRenderData().visBuffer);
                //pipelineState.bindBufferBlock(programInterface.indirectbuff, region.computeDispatchIndirectBuffer);

                //FIXME: optimize by only drawing sides facing the camera
                cmd.drawElementsInstanced(PrimitiveType.TRIANGLES, 6*6, ElementFormat.UNSIGNED_BYTE, 0, region.sectionCount);
            }
        });

        //glDisable(0x937F);
        //glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        //glFinish();
        //glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        MinecraftClient.getInstance().getProfiler().swap("Command gen");
        this.device.usePipeline(this.commandGeneratorPipeline, (cmd, programInterface, pipelineState) -> {
            for (RenderRegion region : visRegion) {
                pipelineState.bindBufferBlock(programInterface.scene, region.getRenderData().sceneBuffer);
                pipelineState.bindBufferBlock(programInterface.meta, region.metaBuffer.getBufferObject());
                pipelineState.bindBufferBlock(programInterface.visbuff, region.getRenderData().visBuffer);

                pipelineState.bindBufferBlock(programInterface.cpuvisbuff, region.getRenderData().cpuSectionVis);

                pipelineState.bindBufferBlock(programInterface.counter, region.getRenderData().counterBuffer);
                pipelineState.bindBufferBlock(programInterface.instancedata, region.getRenderData().instanceBuffer);
                pipelineState.bindBufferBlock(programInterface.id2inst, region.getRenderData().id2InstanceBuffer);
                pipelineState.bindBufferBlock(programInterface.cmdbuffs[0], region.getRenderData().cmd0buff);
                pipelineState.bindBufferBlock(programInterface.cmdbuffs[1], region.getRenderData().cmd1buff);
                pipelineState.bindBufferBlock(programInterface.cmdbuffs[2], region.getRenderData().cmd2buff);
                pipelineState.bindBufferBlock(programInterface.transSort, region.getRenderData().trans3);

                //TODO: optimize the group size
                cmd.dispatchCompute((int)Math.ceil((double) region.sectionCount/16),1,1);
                //cmd.bindDispatchIndirectBuffer(region.computeDispatchIndirectBuffer);
                //cmd.dispatchComputeIndirect(0);
            }
        });

        MinecraftClient.getInstance().getProfiler().swap("Translucency command gen");
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        if (true) {
            this.device.usePipeline(this.translucentCommandGeneratorPipeline, (cmd, programInterface, pipelineState) -> {
                for (RenderRegion region : visRegion) {
                    if (region.translucentSections.intValue() == 0) {
                        continue;
                    }
                    pipelineState.bindBufferBlock(programInterface.transSort, region.getRenderData().trans3);
                    pipelineState.bindBufferBlock(programInterface.meta, region.metaBuffer.getBufferObject());
                    pipelineState.bindBufferBlock(programInterface.command, region.getRenderData().cmd3buff);
                    pipelineState.bindBufferBlock(programInterface.counter, region.getRenderData().counterBuffer);
                    pipelineState.bindBufferBlock(programInterface.id2inst, region.getRenderData().id2InstanceBuffer);
                    cmd.dispatchCompute((int)Math.ceil((double) region.translucentSections.intValue()/32),1,1);
                }
            });
        }
        MinecraftClient.getInstance().getProfiler().pop();
        //glFlush();
    }

    public void fillRenderCommands(List<RenderRegion> regions) {
        //For each region have a prebaked MultiBlockBind ready, when using the compute pipeline, bind this block for each region,
        // (of course keeping the scene uniform the same)
        // then dispatch compute per region with dispatch size being basicly maxSectionId/local_compute_size
    }

    enum EmptyTarget {

    }
}
