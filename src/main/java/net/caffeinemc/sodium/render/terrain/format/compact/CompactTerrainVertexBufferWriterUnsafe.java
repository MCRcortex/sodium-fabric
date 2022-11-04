package net.caffeinemc.sodium.render.terrain.format.compact;

import net.caffeinemc.sodium.render.terrain.format.AccelerationBufferSink;
import net.caffeinemc.sodium.render.terrain.format.AccelerationSink;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferView;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferWriterUnsafe;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import org.lwjgl.system.MemoryUtil;

public class CompactTerrainVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements TerrainVertexSink, AccelerationSink {
    public CompactTerrainVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, TerrainVertexFormats.COMPACT);
    }

    @Override
    public void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light) {
        long i = this.writePointer;

        //writeAccelerationVertex(posX, posY, posZ);

        MemoryUtil.memPutShort(i + 0, CompactTerrainVertexType.encodePosition(posX));
        MemoryUtil.memPutShort(i + 2, CompactTerrainVertexType.encodePosition(posY));
        MemoryUtil.memPutShort(i + 4, CompactTerrainVertexType.encodePosition(posZ));

        MemoryUtil.memPutInt(i + 8, color);

        MemoryUtil.memPutShort(i + 12, CompactTerrainVertexType.encodeBlockTexture(u));
        MemoryUtil.memPutShort(i + 14, CompactTerrainVertexType.encodeBlockTexture(v));

        MemoryUtil.memPutInt(i + 16, light);

        this.advance();
    }

    AccelerationBufferSink abs;
    @Override
    public void writeAccelerationVertex(float posX, float posY, float posZ, int meta) {
        abs.write(posX, posY, posZ, meta);
    }

    @Override
    public AccelerationBufferSink getAccelerationBuffer() {
        return abs;
    }

    @Override
    public void setAccelerationBuffer(AccelerationBufferSink accelerationSink) {
        abs = accelerationSink;
    }
}
