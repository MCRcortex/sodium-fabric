package me.cortex.nv.format;

import me.jellysquid.mods.sodium.client.util.NativeBuffer;

public record BakedChunkGeometry(Range[] geometry, NativeBuffer buffer) {
    public record Range() {}

    public void release() {
        buffer.free();
    }
}
