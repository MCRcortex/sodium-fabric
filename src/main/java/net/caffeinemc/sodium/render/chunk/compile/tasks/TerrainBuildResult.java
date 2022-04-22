package net.caffeinemc.sodium.render.chunk.compile.tasks;

import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.state.BuiltChunkGeometry;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderData;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * The result of a chunk rebuild task which contains any and all data that needs to be processed or uploaded on
 * the main thread. If a task is cancelled after finishing its work and not before the result is processed, the result
 * will instead be discarded.
 */
public record TerrainBuildResult(RenderSection render,
                                 ChunkRenderData data,
                                 BuiltChunkGeometry geometry,
                                 int buildTime,
                                 ChunkSectionPos pos) {
    public void delete() {
        this.geometry.delete();
    }
}
