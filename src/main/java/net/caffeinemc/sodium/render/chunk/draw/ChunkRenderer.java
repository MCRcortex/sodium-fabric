package net.caffeinemc.sodium.render.chunk.draw;

import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;

import java.util.Collection;

/**
 * The chunk render backend takes care of managing the graphics resource state of chunk render containers. This includes
 * the handling of uploading their data to the graphics card and rendering responsibilities.
 */
public interface ChunkRenderer {
    /**
     * Renders the given chunk render list to the active framebuffer.
     * @param renderLists An iterator over the list of chunks to be rendered
     * @param renderPass The block render pass to execute
     * @param matrices The camera matrices to use for rendering
     * @param frameIndex The monotonic index of the current frame being rendered
     */
    void render(RenderListBuilder.RenderList renderLists, ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex);

    void render(Collection<RenderRegion> regions, ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex, ChunkCameraContext cameraContext);

    /**
     * Deletes this render backend and any resources attached to it.
     */
    void delete();
}
