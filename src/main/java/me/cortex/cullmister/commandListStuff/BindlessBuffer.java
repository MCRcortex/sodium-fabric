package me.cortex.cullmister.commandListStuff;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;


public class BindlessBuffer {
    public int id;
    public long addr;

    public BindlessBuffer(long size, int flags) {
        this.id = glCreateBuffers();
        glNamedBufferStorage(id, size, flags);
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(id, GL_READ_ONLY);
        addr = holder[0];
        if (addr == 0) {
            throw new IllegalStateException();
        }
    }

    public BindlessBuffer(long size, int type, long ptr) {
        this.id = glCreateBuffers();
        nglNamedBufferData(id, size, ptr, type);
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(id, GL_READ_ONLY);
        addr = holder[0];
        if (addr == 0) {
            throw new IllegalStateException();
        }
    }



    public void delete() {
        addr = 0;
        id = 0;
        glDeleteBuffers(id);
    }
}
