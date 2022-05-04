package me.cortex.cullmister.commandListStuff;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;


public class BindlessBuffer {
    public static int INSTANCE_COUNT = 0;
    public int id;
    public long addr;
    public long size;

    public BindlessBuffer(long size, int flags) {
        this.size = size;
        this.id = glCreateBuffers();
        glNamedBufferStorage(id, size, flags);
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(id, GL_READ_WRITE);
        addr = holder[0];
        if (addr == 0) {
            throw new IllegalStateException();
        }
        INSTANCE_COUNT++;
    }

    public BindlessBuffer(long size, int type, long ptr) {
        this.size = size;
        this.id = glCreateBuffers();
        nglNamedBufferData(id, size, ptr, type);
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(id, GL_READ_WRITE);
        addr = holder[0];
        if (addr == 0) {
            throw new IllegalStateException();
        }
        INSTANCE_COUNT++;
    }



    public void delete() {
        INSTANCE_COUNT--;
        glMakeNamedBufferNonResidentNV(id);
        glDeleteBuffers(id);
        addr = 0;
        id = 0;
    }
}
