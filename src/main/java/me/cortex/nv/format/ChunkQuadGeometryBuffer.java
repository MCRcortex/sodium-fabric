package me.cortex.nv.format;

import org.lwjgl.system.MemoryUtil;

public class ChunkQuadGeometryBuffer {
    private static class QuadBuffer {
        private long baseAddr = MemoryUtil.nmemAlignedAlloc(32, 32*1024);//1024 quads by default
        private long size;
        private long offset;

        public void reset() {
            offset = 0;
        }

        public void delete() {
            MemoryUtil.nmemFree(baseAddr);
        }

        //Push a 32 byte quad into the buffer
        public void push(long a, long b, long c, long d) {
            if (offset+32>size) {
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
    public final long scratch = MemoryUtil.nmemAlignedAlloc(32, 32);
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
        MemoryUtil.nmemFree(scratch);
        for (var buff : alignedQuadBuffers) {
            buff.delete();
        }
    }

    public BakedChunkGeometry bakeAligned() {
        return null;
    }
}
