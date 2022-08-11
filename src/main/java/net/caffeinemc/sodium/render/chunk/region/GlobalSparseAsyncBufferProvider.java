package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.sparse.v2.AsyncSparseArenaBuffer;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;

public class GlobalSparseAsyncBufferProvider implements IVertexBufferProvider {
    private final RenderDevice device;
    private final AsyncSparseArenaBuffer globalBuffer;
    public GlobalSparseAsyncBufferProvider(RenderDevice device,
                                           StreamingBuffer stagingBuffer,
                                           TerrainVertexType vertexType,
                                           long maxMemory) {
        this.device = device;
        globalBuffer = new AsyncSparseArenaBuffer(
                device,
                stagingBuffer,
                maxMemory,
                vertexType.getBufferVertexFormat().stride()
        );
    }

    @Override
    public ArenaBuffer provide() {
        return globalBuffer;
    }

    @Override
    public void remove(ArenaBuffer vertexBuffer) {

    }

    @Override
    public void destroy() {
        globalBuffer.delete();
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return globalBuffer.getDeviceAllocatedMemory();
    }

    @Override
    public long getDeviceUsedMemory() {
        return globalBuffer.getDeviceUsedMemory();
    }

    @Override
    public int getDeviceBufferObjects() {
        return 1;
    }

    @Override
    public void prune(float prunePercentModifier) {

    }

    @Override
    public String getName() {
        return "Sparse";
    }
}
