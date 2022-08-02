package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.BufferPool;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.AsyncArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.sparse.v2.AsyncSparseArenaBuffer;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;

import java.util.EnumSet;

public class DistinctRecycledBufferProvider implements IVertexBufferProvider {
    private final RenderDevice device;
    private final StreamingBuffer stagingBuffer;
    private final TerrainVertexType vertexType;
    private final BufferPool<ImmutableBuffer> bufferPool;

    public DistinctRecycledBufferProvider(RenderDevice device, StreamingBuffer stagingBuffer, TerrainVertexType vertexType) {
        this.device = device;
        this.stagingBuffer = stagingBuffer;
        this.vertexType = vertexType;
        this.bufferPool = new BufferPool<>(
                device,
                RenderRegionManager.PRUNE_SAMPLE_SIZE,
                c -> device.createBuffer(
                        c,
                        EnumSet.noneOf(ImmutableBufferFlags.class)
                )
        );
    }

    @Override
    public ArenaBuffer provide() {
        return new AsyncArenaBuffer(
                device,
                stagingBuffer,
                bufferPool,
                RenderRegion.REGION_SIZE * 756, // 756 is the average-ish amount of vertices in a section
                .25f, // add 25% each resize
                vertexType.getBufferVertexFormat().stride()
        );
    }

    @Override
    public void remove(ArenaBuffer vertexBuffer) {
        vertexBuffer.delete();
    }

    @Override
    public void destroy() {
        bufferPool.delete();
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return bufferPool.getDeviceAllocatedMemory();
    }

    @Override
    public int getDeviceBufferObjects() {
        return bufferPool.getDeviceBufferObjects();
    }

    @Override
    public void prune(float prunePercentModifier) {
        bufferPool.prune(prunePercentModifier);
    }
}
