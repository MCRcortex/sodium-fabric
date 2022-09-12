package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.RegionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.SectionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.MappedBufferWriter;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.PointerBufferWriter;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.SectionMeta;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateRasterSectionCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateTerrainCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterRegionShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterSectionShader;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;


//FIXME: The region the camera is in will get culled,
// this is cause winding culling, 2 ways to fix, hard inject the current section the camera is in too index 0 and just hard set
// that invocation id 0 is always true


//Todo: maybe try doing the multidraw command as 2 ivec4 or something to be able to batch store everything in 2 memory writes
public class OcclusionEngine {
    public static final int MAX_REGIONS = 150;

    //Used to create render command buffers and instanced data buffers
    public static final int MAX_VISIBLE_SECTIONS = 50000;

    public static final int MAX_RENDER_COMMANDS_PER_LAYER = 150000;
    public static final int MAX_TEMPORAL_COMMANDS_PER_LAYER = 1000;

    public static final long MULTI_DRAW_INDIRECT_COMMAND_SIZE = 32;//5*4;

    public final RegionMetaManager regionMeta;
    public final SectionMetaManager sectionMeta;

    private final RenderDevice device;
    private final RasterRegionShader rasterRegion;
    private final CreateRasterSectionCommandsComputeShader createRasterSectionCommands;
    private final RasterSectionShader rasterSection;
    private final CreateTerrainCommandsComputeShader createTerrainCommands;

    public OcclusionEngine(RenderDevice device) {
        this.device = device;
        this.regionMeta = new RegionMetaManager(device);
        this.sectionMeta = new SectionMetaManager(device);
        this.rasterRegion = new RasterRegionShader(device);
        this.createRasterSectionCommands = new CreateRasterSectionCommandsComputeShader(device);
        this.rasterSection = new RasterSectionShader(device);
        this.createTerrainCommands = new CreateTerrainCommandsComputeShader(device);
    }

    public void prepRender(Collection<RenderRegion> regions, int renderId, ChunkRenderMatrices matrices, ChunkCameraContext cam, Frustum frustum) {
        var viewport = ViewportedData.DATA.get();

        //TODO:FIXME: This is here cause else stuff like frameid gets overriden while shader is running causing ALOT of flickering
        //glFinish();

        //TODO: NEED TO DO THE 3 buffer frame id rotate thing with frustum viewable region ids!!!!!!!!! this will fix region flicker i believe
        viewport.visible_regions.clear();
        int regionCount = 0;

        MinecraftClient.getInstance().getProfiler().push("region_loop");
        //TODO: OPTIMIZE THIS
        {

            StreamingBuffer.WritableSection frustumSection = viewport.frustumRegionArray.getSection(
                    renderId,
                    0,//ViewportedData.FRUSTUM_REGION_ALIGNMENT,
                    true);
            ByteBuffer frustumView = frustumSection.getView();
            long addrFrustumRegion = MemoryUtil.memAddress(frustumView);
            viewport.frustumRegionOffset = frustumSection.getDeviceOffset();

            //List<RenderRegion> regions1 = regions.stream().collect(Collectors.toList());
            //Collections.shuffle(regions1);
            for (RenderRegion region : regions) {

                region.tickInitialBuilds();
                if (region.isEmpty() || region.meta == null) {
                    continue;
                }

                if (!region.meta.aabb.isVisible(frustum)) {
                    continue;
                }

                region.regionSortDistance = (Math.pow(region.regionCenterBlockX-cam.getPosX(), 2)+
                        Math.pow(region.regionCenterBlockY-cam.getPosY(), 2)+
                        Math.pow(region.regionCenterBlockZ-cam.getPosZ(), 2));
                viewport.visible_regions.add(region);
                //TODO: NEED TO ONLY DO THIS AFTER ALL REGIONS ARE DONE SO THAT ITS BASED ON THE sorted distance
                MemoryUtil.memPutInt(addrFrustumRegion + regionCount* 4L, region.meta.id);
                //TODO: Region on vis tick

                //This is a hack too inject visibility for region and section the camera is in
                if (region.meta.aabb.isInside(cam.getBlockX(), cam.getBlockY(), cam.getBlockZ())) {
                    //Could technically move this to the CreateSectionRenderCommands shader
                    glClearNamedBufferSubData(GlBuffer.getHandle(viewport.regionVisibilityArray),
                            GL_R32UI, regionCount*4L, 4,
                            GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{renderId});

                    /*
                    RenderSection section = SodiumWorldRenderer.instance().getTerrainRenderer()
                            .getSection(cam.blockX>>4, cam.blockY>>4, cam.blockZ>>4);
                    if (section != null && section.meta != null) {
                        glClearNamedBufferSubData(GlBuffer.getHandle(viewport.sectionVisibilityBuffer),
                                GL_R32UI, (section.meta.id + (long) region.meta.id * RenderRegion.REGION_SIZE)*4L, 4,
                                GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{renderId});
                    }*/
                }


                regionCount++;
            }
            frustumSection.getView().position(regionCount*4);
            frustumSection.flushPartial();
        }
        {
            MinecraftClient.getInstance().getProfiler().swap("region_tick");
            for (RenderRegion region : viewport.visible_regions) {
                region.tickInitialBuilds();
                region.tickEnqueuedBuilds();
            }
        }
        //glFinish();
        MinecraftClient.getInstance().getProfiler().swap("scene stuff");
        //TODO: need to somehow add a delta for the render for position from last frame to the current frame camera position
        if (true) {
            //TODO: put into gfx
            //TODO: FIXME: need to set the first 2 ints too 0 and the last one too 1
             glClearNamedBufferData(GlBuffer.getHandle(viewport.computeDispatchCommandBuffer),
                     GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{0});
             //ULTRA HACK FIX
             glClearNamedBufferSubData(GlBuffer.getHandle(viewport.computeDispatchCommandBuffer),
                     GL_R32UI, 8, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{1});

            //glFinish();
            //Copy the counts from gpu to cpu buffers
            device.copyBuffer(viewport.commandBufferCounter, viewport.cpuCommandBufferCounter,
                    0, 0, viewport.cpuCommandBufferCounter.capacity());
            //glMemoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
            //glFinish();
            //System.out.println(viewport.cpuCommandBufferCounter.view().getInt(0));
            //glMemoryBarrier(GL_ALL_BARRIER_BITS);
            device.copyBuffer(viewport.translucencyCountBuffer, viewport.cpuTranslucencyCountBuffer,
                    0, 0, 50*4);
        }

        {
            viewport.scene.MVP.set(matrices.projection())
                    .mul(matrices.modelView())
                    //.translate(-(cam.blockX + cam.deltaX), -(cam.blockY + cam.deltaY), -(cam.blockZ + cam.deltaZ));
                    .translate((float) -(cam.getPosX()), (float) -(cam.getPosY()),(float) -(cam.getPosZ()));
            viewport.scene.MV.set(matrices.modelView());
            viewport.scene.camera.set(cam.getBlockX() + cam.getDeltaX(), cam.getBlockY() + cam.getDeltaY(), cam.getBlockZ() + cam.getDeltaZ());
            viewport.scene.cameraSection.set(cam.getSectionX(), cam.getSectionY(), cam.getSectionZ(), 0);
            viewport.scene.regionCount = regionCount;
            viewport.scene.frameId = renderId;

            RenderSection sectionIn = SodiumWorldRenderer.instance().getTerrainRenderer().getSection(cam.getSectionX(), cam.getSectionY(), cam.getSectionZ());
            if (sectionIn != null && sectionIn.meta != null) {
                viewport.scene.regionInId = sectionIn.getRegion().meta.id;
                viewport.scene.sectionInIndex = sectionIn.meta.id;
            } else {
                viewport.scene.regionInId = -1;
                viewport.scene.sectionInIndex = -1;
            }


            StreamingBuffer.WritableSection sceneSection = viewport.sceneBuffer.getSection(
                    renderId,
                    0,//ViewportedData.SCENE_STRUCT_ALIGNMENT,
                    true);
            viewport.sceneOffset = (int) sceneSection.getDeviceOffset();
            PointerBufferWriter writer = new PointerBufferWriter(MemoryUtil.memAddress(sceneSection.getView()), 0);
            viewport.scene.write(writer);
            sceneSection.getView().position((int) writer.getOffset());
            sceneSection.flushPartial();

            viewport.frameDeltaX    = viewport.currentCameraX - cam.getPosX();
            viewport.frameDeltaY    = viewport.currentCameraY - cam.getPosY();
            viewport.frameDeltaZ    = viewport.currentCameraZ - cam.getPosZ();
            viewport.currentCameraX = cam.getPosX();
            viewport.currentCameraY = cam.getPosY();
            viewport.currentCameraZ = cam.getPosZ();

            //FIXME: need to update to be a scalar of the amount the camera moved between the measurement var frame camera position and the current frame
            viewport.countMultiplier = 2;
            //glFinish();
        }
        MinecraftClient.getInstance().getProfiler().pop();

        //FIXME: THIS IS HERE CAUSE OF GOD AWFUL SYNCING PAIN IN THE ASS
        //SodiumClientMod.DEVICE.createFence().sync(true);
        //System.out.println(viewport.visible_regions.size());

        viewport.renderMatrices = matrices;
        //System.out.println(regionCount);
    }

    public void doOcclusion() {
        var viewport = ViewportedData.DATA.get();
        {
            //Clear the commandCountBuffer, NOTE: this must be done here cause else the commandBufferCounter is 0 when drawing
            glClearNamedBufferData(GlBuffer.getHandle(viewport.commandBufferCounter),
                    GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{0});


            glClearNamedBufferData(GlBuffer.getHandle(viewport.translucencyCountBuffer),
                    GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{0});
            /*
            glClearNamedBufferSubData(GlBuffer.getHandle(viewport.translucencyCountBuffer),
                    GL_RGBA32UI, 0, 32, GL_RGBA_INTEGER, GL_UNSIGNED_INT, new int[]{
                            0,
                            (int) (100*MULTI_DRAW_INDIRECT_COMMAND_SIZE),
                            (int) (200*MULTI_DRAW_INDIRECT_COMMAND_SIZE),
                            (int) (300*MULTI_DRAW_INDIRECT_COMMAND_SIZE),
            });*/
        }

        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        //TODO: see if i can remove one of these
        //glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        //glFinish();
        int regionCount = viewport.visible_regions.size();
        rasterRegion.execute(
                regionCount,
                viewport.sceneBuffer.getBufferObject(),
                viewport.sceneOffset,
                viewport.frustumRegionArray.getBufferObject(),
                viewport.frustumRegionOffset,
                regionMeta.getBuffer(),
                viewport.regionVisibilityArray
        );
        //glFinish();
        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        //glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        createRasterSectionCommands.execute(
                regionCount,
                viewport.sceneBuffer.getBufferObject(),
                viewport.sceneOffset,
                regionMeta.getBuffer(),
                viewport.frustumRegionArray.getBufferObject(),
                viewport.regionVisibilityArray,
                viewport.sectionCommandBuffer,
                viewport.computeDispatchCommandBuffer,
                viewport.visibleRegionArray,
                regionMeta.cpuRegionVisibility,
                viewport.sectionVisibilityBuffer
        );
        //glFinish();
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT);
        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        rasterSection.execute(
                regionCount,
                viewport.sceneBuffer.getBufferObject(),
                viewport.sceneOffset,
                viewport.sectionCommandBuffer,
                sectionMeta.getBuffer(),
                viewport.sectionVisibilityBuffer
        );
        //glFinish();

        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        createTerrainCommands.execute(
                viewport.sceneBuffer.getBufferObject(),
                viewport.sceneOffset,
                viewport.computeDispatchCommandBuffer,
                viewport.visibleRegionArray,
                regionMeta.getBuffer(),
                sectionMeta.getBuffer(),
                viewport.sectionVisibilityBuffer,
                viewport.commandBufferCounter,
                viewport.chunkInstancedDataBuffer,
                viewport.commandOutputBuffer,
                viewport.temporalSectionData,
                sectionMeta.cpuSectionVisibility,
                viewport.translucencyCountBuffer,
                viewport.translucencyCommandBuffer
        );

        //glMemoryBarrier(GL_ALL_BARRIER_BITS);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT);
        //glFinish();


        //Note for temporal rendering, can maybe make the max number of temporal commands equal to the max
        // number of visible chunk sections within the view frustum


        //TODO:!!!!!!!!!!!!!!!!!!
        // for translucency, use 3D chunk manhatten distance into buckets of each integer chunk distance
        // e.g.
        // uint bucketId = sum(abs(SECTION.pos-camera))
        // uint cmdIdx   = atomicAdd(bucketCounts[bucketId], 1)
        // write the command to cmdIdx
        // then too render everything, do MDIC over all buckets or something

        viewport.isRenderingTemporal = true;
        viewport.frameDeltaX = 0;
        viewport.frameDeltaY = 0;
        viewport.frameDeltaZ = 0;
        //SodiumWorldRenderer.instance().getTerrainRenderer().chunkRenderer.render(SodiumWorldRenderer.instance().renderPassManager.getRenderPassForId(0), viewport.renderMatrices, 0);
        //SodiumWorldRenderer.instance().getTerrainRenderer().chunkRenderer.render(SodiumWorldRenderer.instance().renderPassManager.getRenderPassForId(1), viewport.renderMatrices, 0);
        //SodiumWorldRenderer.instance().getTerrainRenderer().chunkRenderer.render(SodiumWorldRenderer.instance().renderPassManager.getRenderPassForId(2), viewport.renderMatrices, 0);
        viewport.isRenderingTemporal = false;

    }

    public void delete() {
        regionMeta.delete();
        sectionMeta.delete();
        rasterRegion.delete();
        createRasterSectionCommands.delete();
        rasterSection.delete();
        createTerrainCommands.delete();
        ViewportedData.DATA.deleteAll();
    }
}
