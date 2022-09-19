package net.caffeinemc.sodium.render.terrain.format.merging;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import org.lwjgl.system.MemoryUtil;

import java.util.Objects;

public class IndexShareMergingTerrainVertexSink implements TerrainVertexSink {
    private final TerrainVertexSink delegate;

    private record Vertex(float posX, float posY, float posZ, int color, float u, float v, int light, int index) {
        @Override
        public boolean equals(Object o) {
            Vertex vertex = (Vertex) o;
            return Float.compare(vertex.posX, posX) == 0 &&
                    Float.compare(vertex.posY, posY) == 0 &&
                    Float.compare(vertex.posZ, posZ) == 0 &&
                    color == vertex.color &&
                    Float.compare(vertex.u, u) == 0 &&
                    Float.compare(vertex.v, v) == 0 &&
                    light == vertex.light &&
                    true
                    ;
        }

        @Override
        public int hashCode() {
            return Objects.hash(posX, posY, posZ, u, v, color, light);
        }
    }
    private final ObjectOpenHashSet<Vertex> mapping = new ObjectOpenHashSet<>();
    private long ptr;
    private long maxSize;
    private long curr;
    public IndexShareMergingTerrainVertexSink(TerrainVertexSink delegate) {
        this.delegate = delegate;
        maxSize = 4*4096;
        ptr = MemoryUtil.nmemAlloc(maxSize);
    }

    private void addIndexedVertex(int index) {
        if (curr >= maxSize) {
            ensureCapacity(4);
        }
        MemoryUtil.memPutInt(ptr+curr,index);
        curr += 4;
    }

    @Override
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light) {
        int idx = mapping.size();
        Vertex vert = mapping.addOrGet(new Vertex(posX, posY, posZ, color, u, v, light, idx));
        if (vert.index == idx)
            delegate.writeVertex(posX, posY, posZ, color, u, v, light);
        addIndexedVertex(idx);
    }

    @Override
    public void ensureCapacity(int count) {
        delegate.ensureCapacity(count);
        if (count * 4L + curr >= maxSize) {
            ptr = MemoryUtil.nmemRealloc(ptr, count * 4L + curr);
            maxSize += count * 4L;
        }
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void finish() {
        delegate.finish();
    }

    @Override
    public TerrainVertexSink getDelegate() {
        return delegate;
    }
}
