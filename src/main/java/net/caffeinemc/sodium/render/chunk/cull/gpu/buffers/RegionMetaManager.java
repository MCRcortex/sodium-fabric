package net.caffeinemc.sodium.render.chunk.cull.gpu.buffers;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.sodium.render.chunk.cull.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.cull.gpu.structs.MappedBufferWriter;
import net.caffeinemc.sodium.render.chunk.cull.gpu.structs.RegionMeta;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Set;

import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedBufferSubData;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;

public final class RegionMetaManager {
    private final RenderDevice device;
    private final MappedBuffer buffer;
    private final MappedBufferWriter writer;
    public final MappedBuffer cpuRegionVisibility;

    public RegionMetaManager(RenderDevice device) {
        this.device = device;
        buffer = device.createMappedBuffer(RegionMeta.SIZE * OcclusionEngine.MAX_REGIONS,
                Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        writer = new MappedBufferWriter(buffer);
        cpuRegionVisibility = device.createMappedBuffer(4 * OcclusionEngine.MAX_REGIONS, Set.of(MappedBufferFlags.READ));
    }

    public void update(RenderRegion region) {
        if (region.meta == null)
            return;
        if (region.meta.id == -1)
            throw new IllegalStateException();
        writer.setOffset((long) region.meta.id * RegionMeta.SIZE);
        region.meta.write(writer);
        buffer.flush((long) region.meta.id * RegionMeta.SIZE, RegionMeta.SIZE);
    }

    public void remove(RenderRegion region) {
        if (region.meta == null)
            return;
        if (region.meta.id == -1)
            throw new IllegalStateException();
        glClearNamedBufferSubData(GlBuffer.getHandle(buffer),
                GL_R32UI, (long) region.meta.id * RegionMeta.SIZE, 4,
                GL_RED_INTEGER, GL_UNSIGNED_INT, new int[]{-1});
        region.meta.id = -1;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public void delete() {
        device.deleteBuffer(buffer);
    }
}
