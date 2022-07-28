package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.RegionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.SectionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateRasterSectionCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateTerrainCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterRegionShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterSectionShader;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Set;

public class OcclusionEngine {
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

    public void doOcclusion(Set<RenderRegion> regions, int renderId, ChunkRenderMatrices matrices, ChunkCameraContext cam, Frustum frustum) {
        var viewport = ViewportedData.DATA.get();
        viewport.visible_regions.clear();
        for (RenderRegion region : regions) {

        }
        int regionCount = viewport.visible_regions.size();
        rasterRegion.execute(
                regionCount,
                viewport.scene,
                viewport.frustumRegionArray,
                regionMeta.getBuffer(),
                viewport.regionVisibilityArray
                );

        createRasterSectionCommands.execute(
                regionCount,
                viewport.scene,
                regionMeta.getBuffer(),
                viewport.frustumRegionArray,
                viewport.regionVisibilityArray,
                viewport.sectionCommandBuffer,
                viewport.computeDispatchCommandBuffer,
                viewport.visibleRegionArray
        );

        rasterSection.execute(
                regionCount,
                viewport.scene,
                viewport.sectionCommandBuffer,
                sectionMeta.getBuffer(),
                viewport.sectionVisibilityBuffer
        );

        createTerrainCommands.execute(
                viewport.scene,
                viewport.computeDispatchCommandBuffer,
                viewport.visibleRegionArray,
                regionMeta.getBuffer(),
                sectionMeta.getBuffer(),
                viewport.sectionVisibilityBuffer);
    }
}
