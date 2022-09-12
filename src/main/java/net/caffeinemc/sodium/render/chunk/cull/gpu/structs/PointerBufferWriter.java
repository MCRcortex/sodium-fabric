package net.caffeinemc.sodium.render.chunk.cull.gpu.structs;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;

public final class PointerBufferWriter implements IStructWriter {
    private long offset = 0;
    private final long addr;

    public PointerBufferWriter(long ptr, long offset) {
        addr = ptr;
        this.offset = offset;
    }

    @Override
    public void write(int number) {
        MemoryUtil.memPutInt(addr+offset, number);
        offset += 4;
    }

    @Override
    public void write(Vector4f vec) {
        vec.getToAddress(addr+offset);
        offset += 4*4;
    }

    @Override
    public void write(Vector4i vec) {
        vec.getToAddress(addr+offset);
        offset += 4*4;
    }

    @Override
    public void write(Matrix4f mat) {
        mat.getToAddress(addr+offset);
        offset += 4*4*4;
    }

    public void reset() {
        offset = 0;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getOffset() {
        return this.offset;
    }
}
