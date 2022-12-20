package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.texture.Sprite;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection {
    private final SodiumWorldRenderer worldRenderer;
    private final int chunkX, chunkY, chunkZ;

    private final Map<BlockRenderPass, ChunkGraphicsState> graphicsStates;
    private final RenderRegion region;
    private final int chunkId;

    private ChunkRenderData data = ChunkRenderData.ABSENT;
    private CompletableFuture<?> rebuildTask = null;

    private ChunkUpdateType pendingUpdate;

    public int flags;
    private boolean disposed;

    private int lastAcceptedBuildTime = -1;

    public RenderSection(SodiumWorldRenderer worldRenderer, int chunkX, int chunkY, int chunkZ, RenderRegion region) {
        this.worldRenderer = worldRenderer;
        this.region = region;

        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        this.graphicsStates = new EnumMap<>(BlockRenderPass.class);

        int rX = this.getChunkX() & (RenderRegion.REGION_WIDTH - 1);
        int rY = this.getChunkY() & (RenderRegion.REGION_HEIGHT - 1);
        int rZ = this.getChunkZ() & (RenderRegion.REGION_LENGTH - 1);

        this.chunkId = RenderRegion.getChunkIndex(rX, rY, rZ);
    }

    /**
     * Cancels any pending tasks to rebuild the chunk. If the result of any pending tasks has not been processed yet,
     * those will also be discarded when processing finally happens.
     */
    public void cancelRebuildTask() {
        if (this.rebuildTask != null) {
            this.rebuildTask.cancel(false);
            this.rebuildTask = null;
        }
    }

    public ChunkRenderData getData() {
        return this.data;
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        this.cancelRebuildTask();
        this.setData(ChunkRenderData.ABSENT);
        this.deleteGraphicsState();

        this.disposed = true;
    }

    private void deleteGraphicsState() {
        for (ChunkGraphicsState state : this.graphicsStates.values()) {
            state.delete();
        }

        this.graphicsStates.clear();
    }

    public void setData(ChunkRenderData info) {
        if (info == null) {
            throw new NullPointerException("Mesh information must not be null");
        }

        this.worldRenderer.onChunkRenderUpdated(this.chunkX, this.chunkY, this.chunkZ, this.data, info);
        this.data = info;

        this.flags = 0;

        if (!info.getRenderLayers().isEmpty()) {
            this.flags |= ChunkDataFlags.HAS_BLOCK_GEOMETRY;
        }

        if (!info.getAnimatedSprites().isEmpty()) {
            this.flags |= ChunkDataFlags.HAS_ANIMATED_SPRITES;
        }

        if (!info.getBlockEntities().isEmpty()) {
            this.flags |= ChunkDataFlags.HAS_BLOCK_ENTITIES;
        }
        worldRenderer.getRenderSectionManager().sstate.setChunkDataFlags(this);
    }

    /**
     * @return True if the chunk render contains no data, otherwise false
     */
    public boolean isEmpty() {
        return this.flags == 0;
    }

    /**
     * Returns the chunk section position which this render refers to in the world.
     */
    public ChunkSectionPos getChunkPos() {
        return ChunkSectionPos.from(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * Ensures that all resources attached to the given chunk render are "ticked" forward. This should be called every
     * time before this render is drawn if {@link RenderSection#isTickable()} is true.
     */
    public void tick() {
        for (Sprite sprite : this.data.getAnimatedSprites()) {
            SpriteUtil.markSpriteActive(sprite);
        }
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.chunkZ << 4;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the given position
     */
    public double getSquaredDistance(double x, double y, double z) {
        double xDist = x - this.getCenterX();
        double yDist = y - this.getCenterY();
        double zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    /**
     * @return The x-coordinate of the center position of this chunk render
     */
    private double getCenterX() {
        return this.getOriginX() + 8.0D;
    }

    /**
     * @return The y-coordinate of the center position of this chunk render
     */
    private double getCenterY() {
        return this.getOriginY() + 8.0D;
    }

    /**
     * @return The z-coordinate of the center position of this chunk render
     */
    private double getCenterZ() {
        return this.getOriginZ() + 8.0D;
    }

    public ChunkGraphicsState setGraphicsState(BlockRenderPass pass, ChunkGraphicsState state) {
        if (state == null) {
            return this.graphicsStates.remove(pass);
        } else {
            return this.graphicsStates.put(pass, state);
        }
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkY() {
        return this.chunkY;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public ChunkRenderBounds getBounds() {
        return this.data.getBounds();
    }

    public ChunkGraphicsState getGraphicsState(BlockRenderPass pass) {
        return this.graphicsStates.get(pass);
    }

    public boolean hasFlag(int flag) {
        return (this.flags & flag) != 0;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    @Override
    public String toString() {
        return String.format("RenderChunk{chunkX=%d, chunkY=%d, chunkZ=%d}",
                this.chunkX, this.chunkY, this.chunkZ);
    }

    public ChunkUpdateType getPendingUpdate() {
        return this.pendingUpdate;
    }

    public void markForUpdate(ChunkUpdateType type) {
        worldRenderer.getRenderSectionManager().sstate.setChunkDataFlags(this);
        if (this.pendingUpdate == null || type.ordinal() > this.pendingUpdate.ordinal()) {
            this.pendingUpdate = type;
        }
    }

    public void onBuildSubmitted(CompletableFuture<?> task) {
        if (this.rebuildTask != null) {
            this.rebuildTask.cancel(false);
            this.rebuildTask = null;
        }

        this.rebuildTask = task;
        this.pendingUpdate = null;
        worldRenderer.getRenderSectionManager().sstate.setChunkDataFlags(this);
    }

    public boolean isBuilt() {
        return this.data != ChunkRenderData.ABSENT;
    }

    public boolean canAcceptBuildResults(ChunkBuildResult result) {
        return !this.isDisposed() && result.buildTime > this.lastAcceptedBuildTime;
    }

    public void onBuildFinished(ChunkBuildResult result) {
        this.setData(result.data);
        this.lastAcceptedBuildTime = result.buildTime;
    }

    public int getChunkId() {
        return this.chunkId;
    }
}
