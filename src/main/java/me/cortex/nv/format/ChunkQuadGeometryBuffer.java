package me.cortex.nv.format;

import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

public class ChunkQuadGeometryBuffer {
    public static class QuadBuffer {
        private long baseAddr = 0;//MemoryUtil.nmemAlignedAlloc(32, 32*1024);//1024 quads by default
        private long size;
        private long offset;

        public void reset() {
            offset = 0;
        }

        public void delete() {
            MemoryUtil.nmemFree(baseAddr);
            baseAddr = 0;
            size = 0;
        }

        //Push a 32 byte quad into the buffer
        public void push(long a, long b, long c, long d) {
            if (offset+32>size) {
                if (size == 0) {
                    size = 512;
                }
                baseAddr = MemoryUtil.nmemRealloc(baseAddr, size*2);
                size *= 2;
            }
            MemoryUtil.memPutLong(baseAddr+offset, a);
            MemoryUtil.memPutLong(baseAddr+offset+8, b);
            MemoryUtil.memPutLong(baseAddr+offset+16, c);
            MemoryUtil.memPutLong(baseAddr+offset+24, d);
            offset += 32;
        }

        public void push(long scratch) {
            push(MemoryUtil.memGetLong(scratch),
                    MemoryUtil.memGetLong(scratch+8),
                    MemoryUtil.memGetLong(scratch+16),
                    MemoryUtil.memGetLong(scratch+24));
        }
    }

    private final QuadBuffer[] alignedQuadBuffers = new QuadBuffer[8];
    public ChunkQuadGeometryBuffer() {
        for (int i = 0; i < 8; i++) {
            alignedQuadBuffers[i] = new QuadBuffer();
        }
    }

    public void init() {
        for (var buff : alignedQuadBuffers) {
            buff.reset();
        }
    }

    public void delete() {
        for (var buff : alignedQuadBuffers) {
            buff.delete();
        }
    }

    public QuadBuffer get(int id) {
        return alignedQuadBuffers[id];
    }

    public BakedChunkGeometry bake() {
        long size = 0;
        for (var b : alignedQuadBuffers) {
            size += b.offset;
        }
        var res = new BakedChunkGeometry.Range[alignedQuadBuffers.length];
        var buf = new NativeBuffer((int) size);
        long addr = MemoryUtil.memAddress(buf.getDirectBuffer());
        int offset = 0;
        for (int i = 0; i < alignedQuadBuffers.length; i++) {
            if (alignedQuadBuffers[i].offset != 0) {
                res[i] = new BakedChunkGeometry.Range((short) (offset/32L), (short) (alignedQuadBuffers[i].offset/32));
                MemoryUtil.memCopy(alignedQuadBuffers[i].baseAddr, addr+offset, alignedQuadBuffers[i].offset);
                offset += alignedQuadBuffers[i].offset;
            }
        }
        return new BakedChunkGeometry(res, buf);
    }
}
