package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.gfx.util.buffer.BufferPool;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.AsyncArenaBuffer;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

public class RenderRegion {
    public static final int REGION_WIDTH = 16;
    public static final int REGION_HEIGHT = 16;
    public static final int REGION_LENGTH = 16;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    private static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    private static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    private static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    public final ArenaBuffer vertexBuffers;
    public final int id;

    public RenderRegion(RenderDevice device, StreamingBuffer stagingBuffer, BufferPool<ImmutableBuffer> vertexBufferPool, TerrainVertexType vertexType, int id) {
        this.vertexBuffers = new AsyncArenaBuffer(
                device,
                stagingBuffer,
                vertexBufferPool,
                REGION_SIZE * 756, // 756 is the average-ish amount of vertices in a section
                .25f, // add 25% each resize
                vertexType.getBufferVertexFormat().stride()
        );
        this.id = id;
    }

    public void delete() {
        this.vertexBuffers.delete();
    }

    public boolean isEmpty() {
        return this.vertexBuffers.isEmpty();
    }

    public long getDeviceUsedMemory() {
        return this.vertexBuffers.getDeviceUsedMemory();
    }

    public long getDeviceAllocatedMemory() {
        return this.vertexBuffers.getDeviceAllocatedMemory();
    }

    public static long getRegionCoord(int chunkX, int chunkY, int chunkZ) {
        return ChunkSectionPos.asLong(chunkX >> REGION_WIDTH_SH, chunkY >> REGION_HEIGHT_SH, chunkZ >> REGION_LENGTH_SH);
    }
}
