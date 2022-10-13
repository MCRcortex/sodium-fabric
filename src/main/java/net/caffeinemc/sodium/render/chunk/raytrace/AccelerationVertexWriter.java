package net.caffeinemc.sodium.render.chunk.raytrace;

import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import org.lwjgl.system.MemoryUtil;

public class AccelerationVertexWriter implements TerrainVertexSink {
    final TerrainVertexSink delegate;
    public long mem = MemoryUtil.nmemAlloc(0);
    public long pos = 0;
    long size = 0;

    public AccelerationVertexWriter(TerrainVertexSink delegate) {
        this.delegate = delegate;
    }

    @Override
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light) {
        long i = this.mem;
        MemoryUtil.memPutFloat(i, posX);
        MemoryUtil.memPutFloat(i+4, posY);
        MemoryUtil.memPutFloat(i+8, posZ);
        pos += 4*3;
        if (pos > size) {
            throw new IllegalStateException();
        }
        delegate.writeVertex(posX, posY, posZ, color, u, v, light);
    }

    @Override
    public void ensureCapacity(int count) {
        if ((long) count *3*4+pos>size) {
            mem = MemoryUtil.nmemRealloc(mem, (long) count *3*4*4+pos);//the extra *4 is just to make it nicer
            size = (long) count *3*4*4+pos;
        }
        delegate.ensureCapacity(count);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public TerrainVertexSink getDelegate() {
        return delegate;
    }

    @Override
    public void finish() {
        delegate.finish();
    }
}
