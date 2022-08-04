package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.RegionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.SectionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.MappedBufferWriter;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateRasterSectionCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateTerrainCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterRegionShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterSectionShader;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryUtil;

import java.util.Collection;
import java.util.Set;

import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedBufferData;
import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedBufferSubData;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;


//FIXME: The region the camera is in will get culled,
// this is cause winding culling, 2 ways to fix, hard inject the current section the camera is in too index 0 and just hard set
// that invocation id 0 is always true
public class OcclusionEngine {
    public static final int MAX_REGIONS = 150;
    public static final long MULTI_DRAW_INDIRECT_COMMAND_SIZE = 5*4;

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

    public void doOcclusion(Collection<RenderRegion> regions, int renderId, ChunkRenderMatrices matrices, ChunkCameraContext cam, Frustum frustum) {
        var viewport = ViewportedData.DATA.get();

        viewport.visible_regions.clear();
        int regionCount = 0;
        {
            long addrFrustumRegion = MemoryUtil.memAddress(viewport.frustumRegionArray.view());
            for (RenderRegion region : regions) {
                if (region.isEmpty() || region.meta == null) {
                    continue;
                }

                if (!region.meta.aabb.isVisible(frustum)) {
                    continue;
                }

                region.regionSortDistance = Math.pow(region.regionCenterBlockX-cam.posX, 2)+
                                Math.pow(region.regionCenterBlockY-cam.posY, 2)+
                                Math.pow(region.regionCenterBlockZ-cam.posZ, 2);
                viewport.visible_regions.add(region);
                //TODO: NEED TO ONLY DO THIS AFTER ALL REGIONS ARE DONE SO THAT ITS BASED ON THE sorted distance
                MemoryUtil.memPutInt(addrFrustumRegion + regionCount* 4L, region.meta.id);
                regionCount++;
                //TODO: Region on vis tick
            }
            viewport.frustumRegionArray.flush(0, regionCount * 4L);
        }
        {
            //TODO: put into gfx
            //TODO: FIXME: need to set the first 2 ints too 0 and the last one too 1
            glClearNamedBufferData(GlBuffer.getHandle(viewport.computeDispatchCommandBuffer),
                    GL_R32UI,GL_RED, GL_UNSIGNED_INT, new int[]{0});
            //ULTRA HACK FIX
            glClearNamedBufferSubData(GlBuffer.getHandle(viewport.computeDispatchCommandBuffer),
                    GL_R32UI, 8, 4,GL_RED, GL_UNSIGNED_INT, new int[]{1});
        }
        {
            viewport.scene.MVP.set(matrices.projection())
                    .mul(matrices.modelView())
                    //.translate(-(cam.blockX + cam.deltaX), -(cam.blockY + cam.deltaY), -(cam.blockZ + cam.deltaZ));
                    .translate((float) -(cam.posX), (float) -(cam.posY),(float) -(cam.posZ));
            viewport.scene.MV.set(matrices.modelView());
            viewport.scene.camera.set(cam.blockX + cam.deltaX, cam.blockY + cam.deltaY, cam.blockZ + cam.deltaZ);
            viewport.scene.regionCount = regionCount;
            viewport.scene.frameId = renderId;

            viewport.scene.write(new MappedBufferWriter(viewport.sceneBuffer));
            viewport.sceneBuffer.flush();
        }

        rasterRegion.execute(
                regionCount,
                viewport.sceneBuffer,
                viewport.frustumRegionArray,
                regionMeta.getBuffer(),
                viewport.regionVisibilityArray
        );

        createRasterSectionCommands.execute(
                regionCount,
                viewport.sceneBuffer,
                regionMeta.getBuffer(),
                viewport.frustumRegionArray,
                viewport.regionVisibilityArray,
                viewport.sectionCommandBuffer,
                viewport.computeDispatchCommandBuffer,
                viewport.visibleRegionArray
        );

        rasterSection.execute(
                regionCount,
                viewport.sceneBuffer,
                viewport.sectionCommandBuffer,
                sectionMeta.getBuffer(),
                viewport.sectionVisibilityBuffer
        );

        createTerrainCommands.execute(
                viewport.sceneBuffer,
                viewport.computeDispatchCommandBuffer,
                viewport.visibleRegionArray,
                regionMeta.getBuffer(),
                sectionMeta.getBuffer(),
                viewport.sectionVisibilityBuffer
        );
    }

    public void delete() {
        regionMeta.delete();
        sectionMeta.delete();
        rasterRegion.delete();
        createRasterSectionCommands.delete();
        rasterSection.delete();
        createTerrainCommands.delete();
    }
}
