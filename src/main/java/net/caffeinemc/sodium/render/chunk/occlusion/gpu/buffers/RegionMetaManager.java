package net.caffeinemc.sodium.render.chunk.occlusion.gpu.buffers;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.streaming.SectionedStreamingBuffer;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.MappedBufferWriter;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.RegionMeta;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.SectionMeta;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Set;

public final class RegionMetaManager {
    private final RenderDevice device;
    private final MappedBuffer buffer;
    private final MappedBufferWriter writer;

    public RegionMetaManager(RenderDevice device) {
        this.device = device;
        buffer = device.createMappedBuffer(RegionMeta.SIZE * OcclusionEngine.MAX_REGIONS,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        writer = new MappedBufferWriter(buffer);
    }

    public void update(RenderRegion region) {
        if (region.meta == null)
            return;
        writer.setOffset((long) region.meta.id * RegionMeta.SIZE);
        region.meta.write(writer);
        buffer.flush((long) region.meta.id * RegionMeta.SIZE, RegionMeta.SIZE);
    }

    public void remove(RenderRegion region) {
        if (region.meta == null)
            return;
        //TODO: just sets the id to -1 or something of the mapped buffer
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public void delete() {
        device.deleteBuffer(buffer);
    }
}
