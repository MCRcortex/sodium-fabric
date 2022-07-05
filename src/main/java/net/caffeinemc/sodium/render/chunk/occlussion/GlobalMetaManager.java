package net.caffeinemc.sodium.render.chunk.occlussion;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.SectionedStreamingBuffer;
import net.caffeinemc.gfx.util.buffer.StreamingBuffer;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Set;

public class GlobalMetaManager {
    private final RenderDevice device;
    private final SectionedStreamingBuffer globalMetaBuffer;
    public GlobalMetaManager(RenderDevice device, int renderDistance) {
        this.device = device;
        int regions = (int)(Math.pow((Math.ceil(renderDistance/16.0))*2 + 1,2)* Math.ceil(24.0/RenderRegion.REGION_HEIGHT));
        globalMetaBuffer = new SectionedStreamingBuffer(device, 1, SectionMeta.SIZE, RenderRegion.REGION_SIZE*regions, Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
    }


    public void delete() {
        globalMetaBuffer.delete();
    }



    public StreamingBuffer.WritableSection getSection(int regionId, int sectionId) {
        return globalMetaBuffer.getSection(RenderRegion.REGION_SIZE*regionId + sectionId);
    }

    public long getBaseOffset(int regionId) {
        return (long) RenderRegion.REGION_SIZE * SectionMeta.SIZE * regionId;
    }

    public long getSize(int regionId) {
        return RenderRegion.REGION_SIZE * SectionMeta.SIZE;
    }

    public Buffer getBufferObject() {
        return globalMetaBuffer.getBufferObject();
    }
}
