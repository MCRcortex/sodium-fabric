package me.cortex.cullmister.utils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL45.*;

public class VBO implements IBindable {
    public int id;
    protected int bound_to;

    public VBO() {
        id = glCreateBuffers();
    }

    public void bind() {
        bind(GL_ARRAY_BUFFER);
    }

    public void bind(int target) {
        bound_to = target;
        glBindBuffer(target, id);
    }

    public void bufferData(int usage, int[] data) {
        glBufferData(bound_to, data, usage);
    }

    public void bufferData(int usage, long ptr, long size) {
        nglBufferData(bound_to, size, ptr, usage);
    }
    public void bufferNamedData(int usage, long ptr, long size) {
        nglNamedBufferData(id, size, ptr, usage);
    }
    public void bufferNamedData(int usage, ByteBuffer ptr) {
        glNamedBufferData(id, ptr, usage);
    }

    public void bufferData(int usage, float[] data) {
        glBufferData(bound_to, data, usage);
    }

    public void bufferData(int usage, ByteBuffer data) {
        glBufferData(bound_to, data, usage);
    }
    public void bufferData(int usage, FloatBuffer data) {
        glBufferData(bound_to, data, usage);
    }
    public void bufferNamedData(int usage, FloatBuffer data) {
        glNamedBufferData(id, data, usage);
    }

    public void unbind() {
        glBindBuffer(bound_to, 0);
        bound_to = 0;
    }

    public void bufferSubData(long offset, float[] floats) {
        glBufferSubData(bound_to, offset, floats);
    }

    public void delete() {
        glDeleteBuffers(id);
    }

    public long mappedNamedPtr(int usage) {
        return nglMapNamedBuffer(id, usage);
    }
    public long mappedNamedPtrRanged(long offset, long length, int access) {
        return nglMapNamedBufferRange(id, offset, length, access);
    }

    public void unmapNamed() {
        glUnmapNamedBuffer(id);
    }

    public long mapPtr(int usage) {
        return nglMapBuffer(bound_to, usage);
    }
    public void unmap() {
        glUnmapBuffer(bound_to);
    }
}
