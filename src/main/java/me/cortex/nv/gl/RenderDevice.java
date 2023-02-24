package me.cortex.nv.gl;

import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.IClientMappedBuffer;
import me.cortex.nv.gl.buffers.PersistentMappedBuffer;

public class RenderDevice {
    public PersistentMappedBuffer createClientMappedBuffer(long size) {
        return null;
    }

    public void flush(IClientMappedBuffer buffer, long offset, int size) {

    }

    public void copy(Buffer source, long sourceOffset, Buffer dest, long destOffset, long size) {

    }

    public RenderCommandList createRenderList() {
        return null;
    }
}
