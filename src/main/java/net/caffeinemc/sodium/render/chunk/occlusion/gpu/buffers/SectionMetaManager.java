package net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.MappedBufferWriter;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.SectionMeta;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Set;

public final class SectionMetaManager {
    private final RenderDevice device;
    private final MappedBuffer buffer;
    private final MappedBufferWriter writer;
    public final MappedBuffer cpuSectionVisibility;

    public SectionMetaManager(RenderDevice device) {
        this.device = device;
        buffer = device.createMappedBuffer((long) SectionMeta.SIZE * RenderRegion.REGION_SIZE * OcclusionEngine.MAX_REGIONS,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        writer = new MappedBufferWriter(buffer);
        cpuSectionVisibility = device.createMappedBuffer((long) 4 * RenderRegion.REGION_SIZE * OcclusionEngine.MAX_REGIONS, Set.of(MappedBufferFlags.READ));
    }

    public void update(RenderSection section) {
        if (section.meta == null || section.getRegion() == null || section.getRegion().meta == null)
            return;
        if (section.getRegion().meta.id == -1)
            throw new IllegalStateException();
        //TODO: abstract the getting of the region offset or something
        long offset = ((long) section.meta.id * SectionMeta.SIZE) + ((long) section.getRegion().meta.id * RenderRegion.REGION_SIZE * SectionMeta.SIZE);
        writer.setOffset(offset);
        section.meta.write(writer);
        buffer.flush(offset, SectionMeta.SIZE);
    }

    public void remove(RenderSection section) {
        if (section.meta == null)
            return;
        if (section.getRegion().meta.id == -1)
            throw new IllegalStateException();
        long offset = ((long) section.meta.id * SectionMeta.SIZE) + ((long) section.getRegion().meta.id * RenderRegion.REGION_SIZE * SectionMeta.SIZE);
        writer.setOffset(offset);
        section.meta.id = -1;
        section.meta.write(writer);
        buffer.flush(offset, SectionMeta.SIZE);
        //TODO: just sets the id to -1 or something of the mapped buffer
    }

    public void delete() {
        device.deleteBuffer(buffer);
    }

    public Buffer getBuffer() {
        return buffer;
    }
}
