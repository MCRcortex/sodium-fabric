package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.RegionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers.SectionMetaManager;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateRasterSectionCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.CreateTerrainCommandsComputeShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterRegionShader;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems.RasterSectionShader;

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

    public void doOcclusion() {

    }
}
