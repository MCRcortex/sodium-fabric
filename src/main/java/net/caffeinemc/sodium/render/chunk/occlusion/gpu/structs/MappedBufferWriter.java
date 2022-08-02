package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

public final class MappedBufferWriter implements IStructWriter {
    private final MappedBuffer buffer;
    private long offset = 0;
    private final long addr;

    public MappedBufferWriter(MappedBuffer buffer) {
        this(buffer, 0);
    }

    public MappedBufferWriter(MappedBuffer buffer, long offset) {
        this.buffer = buffer;
        addr = MemoryUtil.memAddress(buffer.view());
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
}
