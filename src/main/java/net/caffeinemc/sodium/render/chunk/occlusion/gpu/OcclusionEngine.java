package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.device.RenderDevice;
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

public class OcclusionEngine {
    public static final int MAX_REGIONS = 150;

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
                if (region.isEmpty() || region.meta == null || !region.meta.aabb.isVisible(frustum)) {
                    continue;
                }
                //TODO: need to sort it by distance
                viewport.visible_regions.add(region);
                MemoryUtil.memPutInt(addrFrustumRegion + regionCount* 4L, region.meta.id);
                regionCount++;
                //TODO: Region on vis tick
            }
            viewport.frustumRegionArray.flush(0, regionCount * 4L);
        }

        {
            viewport.scene.MVP.set(matrices.modelView()).mul(matrices.projection());
            viewport.scene.MV.set(matrices.modelView());
            viewport.scene.camera.set(cam.blockX + cam.deltaX, cam.blockY + cam.deltaY, cam.blockZ + cam.deltaZ);
            viewport.scene.regionCount = regionCount;
            viewport.scene.frameId = renderId;

            viewport.scene.write(new MappedBufferWriter(viewport.sceneBuffer));
            viewport.sceneBuffer.flush();
        }
        if (true)
            return;
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
