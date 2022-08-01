package net.caffeinemc.sodium.render.chunk.region;

import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;

public interface IVertexBufferProvider {
    ArenaBuffer provide();

    void remove(ArenaBuffer vertexBuffer);

    void destroy();

    long getDeviceAllocatedMemory();

    int getDeviceBufferObjects();

    void prune(float prunePercentModifier);
}
