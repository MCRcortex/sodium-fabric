package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.arena.AsyncBufferArena;
import net.caffeinemc.sodium.render.arena.BufferArena;
import net.caffeinemc.sodium.render.buffer.StreamingBuffer;
import net.caffeinemc.sodium.render.chunk.occlussion.SectionMeta;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

import java.util.Set;

public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

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


    public final BufferArena vertexBuffers;
    public final StreamingBuffer metaBuffer;
    public final ImmutableBuffer visBuffer;

    //FIXME: can probably move this to be a bigger buffer for all lists, note will need to be 1 PER render layer
    // steal from RenderListBuilder
    /*
    public final ImmutableBuffer countBuffer;
    public final ImmutableBuffer commandBuffer;
    public final ImmutableBuffer instanceBuffer;

     */

    public final int id;

    public RenderRegion(RenderDevice device, StreamingBuffer streamingBuffer, TerrainVertexType vertexType, int id) {
        this.vertexBuffers = new AsyncBufferArena(device, streamingBuffer, REGION_SIZE * 756, vertexType.getBufferVertexFormat().stride());
        this.metaBuffer = new StreamingBuffer(device, 1, SectionMeta.SIZE, REGION_SIZE);//FIXME: add relevant flags
        this.visBuffer = device.createBuffer(REGION_SIZE, Set.of());//FIXME: add relevant flags
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
