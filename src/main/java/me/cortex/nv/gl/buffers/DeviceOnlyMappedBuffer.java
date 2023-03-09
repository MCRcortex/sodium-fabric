package me.cortex.nv.gl.buffers;

import me.cortex.nv.gl.GlObject;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;

public class DeviceOnlyMappedBuffer extends GlObject implements IDeviceMappedBuffer {
    public final long size;
    public final long addr;
    public DeviceOnlyMappedBuffer(long size) {//TODO: Make the access flag be specified so more optimization go brr
        super(glCreateBuffers());
        this.size = size;
        glNamedBufferStorage(id, size, 0);
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(id, GL_READ_WRITE);
        addr = holder[0];
        if (addr == 0) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void delete() {
        glDeleteBuffers(id);
    }

    @Override
    public long getDeviceAddress() {
        return addr;
    }
}
