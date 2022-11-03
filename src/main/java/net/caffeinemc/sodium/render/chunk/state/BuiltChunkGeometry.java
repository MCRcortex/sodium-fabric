package net.caffeinemc.sodium.render.chunk.state;

import net.caffeinemc.sodium.render.buffer.VertexData;
import net.caffeinemc.sodium.vk.AccelerationData;
import org.jetbrains.annotations.Nullable;

public record BuiltChunkGeometry(@Nullable VertexData vertices,
                                 ChunkPassModel[] models,
                                 AccelerationData accelerationData) {
    
    private static final BuiltChunkGeometry EMPTY_INSTANCE = new BuiltChunkGeometry(null, null, null);
    
    public static BuiltChunkGeometry empty() {
        return EMPTY_INSTANCE;
    }

    public void delete() {
        if (this.vertices != null) {
            this.vertices.delete();
        }
        if (this.accelerationData != null) {
            this.accelerationData.delete();
        }
    }
}
