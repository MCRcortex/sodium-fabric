package me.cortex.nv.util;

import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.DeviceOnlyMappedBuffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.buffers.PersistentSparseAddressableBuffer;
import me.cortex.nv.util.SegmentedManager;

public class BufferArena {
    SegmentedManager segments = new SegmentedManager();
    private final int vertexFormatSize;
    private final RenderDevice device;
    public final IDeviceMappedBuffer buffer;

    public BufferArena(RenderDevice device, int vertexFormatSize) {
        this.device = device;
        this.vertexFormatSize = vertexFormatSize;
        buffer = device.createSparseBuffer(5000000000L);//Create a 5gb buffer
    }

    public int alloc(int quadCount) {
        long addr = segments.alloc(quadCount*4*vertexFormatSize);
        if (addr%(4L *vertexFormatSize) != 0) {
            throw new IllegalStateException();
        }
        return (int) (addr/quadCount*4*vertexFormatSize);
    }

    public void free(int addr) {

    }

    public void delete() {
        buffer.delete();
    }
}
