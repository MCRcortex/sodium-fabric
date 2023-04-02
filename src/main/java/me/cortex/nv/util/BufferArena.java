package me.cortex.nv.util;

import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.DeviceOnlyMappedBuffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.buffers.PersistentSparseAddressableBuffer;
import me.cortex.nv.util.SegmentedManager;

public class BufferArena {
    SegmentedManager segments = new SegmentedManager();
    private final RenderDevice device;
    public final PersistentSparseAddressableBuffer buffer;

    public BufferArena(RenderDevice device, int vertexFormatSize) {
        this.device = device;
        buffer = device.createSparseBuffer(8000000000L);//Create a 8gb buffer
    }

    public int allocQuads(int quadCount) {
        int addr = (int) segments.alloc(quadCount);
        buffer.ensureAllocated(addr*32L, quadCount*32L);
        return addr;
    }

    public void free(int addr) {
        int count = segments.free(addr);
        buffer.deallocate(addr*32L, count*32L);
    }

    public long upload(UploadingBufferStream stream, int addr) {
        return stream.getUpload(buffer, addr*32L, (int) segments.getSize(addr)*32);
    }

    public void delete() {
        buffer.delete();
    }
}
