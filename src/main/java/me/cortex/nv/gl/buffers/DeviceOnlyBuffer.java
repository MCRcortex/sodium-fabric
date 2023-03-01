package me.cortex.nv.gl.buffers;

import me.cortex.nv.gl.GlObject;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

public class DeviceOnlyBuffer extends GlObject implements Buffer {
    public final long size;
    public DeviceOnlyBuffer(long size) {
        super(glCreateBuffers());
        this.size = size;
        glNamedBufferStorage(id, size, 0);
    }

    @Override
    public void delete() {
        glDeleteBuffers(id);
    }
}
