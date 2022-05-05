package me.cortex.cullmister.chunkBuilder;

import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import net.caffeinemc.sodium.render.terrain.format.compact.CompactTerrainVertexType;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferView;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferWriterUnsafe;
import org.lwjgl.system.MemoryUtil;

public class OptimizedTerrainVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements TerrainVertexSink {
    public OptimizedTerrainVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, TerrainVertexFormats.COMPACT);
    }

    @Override
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light) {

    }
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light, short textureIndex) {
        long i = this.writePointer;

        MemoryUtil.memPutShort(i + 0, CompactTerrainVertexType.encodePosition(posX));
        MemoryUtil.memPutShort(i + 2, CompactTerrainVertexType.encodePosition(posY));
        MemoryUtil.memPutShort(i + 4, CompactTerrainVertexType.encodePosition(posZ));

        MemoryUtil.memPutShort(i + 6, textureIndex);

        MemoryUtil.memPutInt(i + 8, color);

        MemoryUtil.memPutShort(i + 12, CompactTerrainVertexType.encodeBlockTexture(u));
        MemoryUtil.memPutShort(i + 14, CompactTerrainVertexType.encodeBlockTexture(v));

        MemoryUtil.memPutInt(i + 16, CompactTerrainVertexType.encodeLightMapTexCoord(light));

        this.advance();
    }
}
