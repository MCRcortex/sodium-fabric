package net.caffeinemc.sodium.render.chunk;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.RenderPassRanges;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.SectionMeta;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderData;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection {
    private final int id;

    private final long regionKey;
    public final int innerRegionKey;
    private RenderRegion region;

    private final int chunkX, chunkY, chunkZ;
    private final double originX, originY, originZ;

    private ChunkRenderData data = ChunkRenderData.ABSENT;
    private CompletableFuture<?> rebuildTask = null;

    private ChunkUpdateType pendingUpdate;
    private long uploadedGeometrySegment = BufferSegment.INVALID;

    private boolean disposed;

    private int lastAcceptedBuildTime = -1;
    private int flags;

    //Render data
    public int lastFrameId;
    public int instanceIndex;

    public RenderSection(int chunkX, int chunkY, int chunkZ, int id) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        this.originX = ChunkSectionPos.getBlockCoord(this.chunkX) + 8;
        this.originY = ChunkSectionPos.getBlockCoord(this.chunkY) + 8;
        this.originZ = ChunkSectionPos.getBlockCoord(this.chunkZ) + 8;

        this.id = id;
        this.regionKey = RenderRegion.getRegionCoord(this.chunkX, this.chunkY, this.chunkZ);
        this.innerRegionKey = RenderRegion.getInnerCoord(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * Cancels any pending tasks to rebuild the chunk. If the result of any pending tasks has not been processed yet,
     * those will also be discarded when processing finally happens.
     */
    public void cancelRebuildTask() {
        if (this.rebuildTask != null) {
            this.rebuildTask.cancel(false);
            this.rebuildTask = null;
            this.pendingUpdate = null;
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
        RenderRegion region = this.region;
        this.cancelRebuildTask();
        this.ensureGeometryDeleted();
        this.disposed = true;
        if (region == null)
            region = SodiumWorldRenderer.instance().getTerrainRenderer().regionManager.getRegion(chunkX, chunkY, chunkZ);
        if (region != null)
            region.deletedSection(this);
        updateMeta(region, data, data);
    }

    public void setData(ChunkRenderData data) {
        if (data == null) {
            throw new NullPointerException("Mesh information must not be null");
        }
        ChunkRenderData old = this.data;
        this.data = data;
        this.flags = data.getFlags();
        updateMeta(region, old, data);
    }

    /**
     * Returns the chunk section position which this render refers to in the world.
     */
    public ChunkSectionPos getChunkPos() {
        return ChunkSectionPos.from(this.chunkX, this.chunkY, this.chunkZ);
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
        if (this.pendingUpdate == null || type.ordinal() > this.pendingUpdate.ordinal()) {
            this.pendingUpdate = type;
        }
    }

    public void onBuildSubmitted(CompletableFuture<?> task) {
        this.cancelRebuildTask();

        this.rebuildTask = task;
        this.pendingUpdate = null;
    }

    public boolean isBuilt() {
        return this.data != ChunkRenderData.ABSENT;
    }

    public int getLastAcceptedBuildTime() {
        return this.lastAcceptedBuildTime;
    }

    public void setLastAcceptedBuildTime(int time) {
        this.lastAcceptedBuildTime = time;
    }

    public void ensureGeometryDeleted() {
        long uploadedGeometrySegment = this.uploadedGeometrySegment;
        
        if (uploadedGeometrySegment != BufferSegment.INVALID) {
            this.region.removeSection(this);
            this.uploadedGeometrySegment = BufferSegment.INVALID;
            //Dont need to clear the region as the region cant chance (i think???)
            //this.region = null;
        }
    }

    /**
     * Make sure to call {@link #ensureGeometryDeleted()} before calling this!
     */
    public void setGeometry(RenderRegion region, long bufferSegment) {
        this.setBufferSegment(bufferSegment);
        this.region = region;
        this.uploadedGeometrySegment = bufferSegment;
        updateMeta(region, data, data);
    }
    
    public void setBufferSegment(long bufferSegment) {
        if (bufferSegment == BufferSegment.INVALID) {
            throw new IllegalArgumentException("Segment cannot be invalid");
        }
        this.uploadedGeometrySegment = bufferSegment;
    }

    public long getUploadedGeometrySegment() {
        return this.uploadedGeometrySegment;
    }
    
    public int getId() {
        return this.id;
    }

    public double getDistance(double x, double y, double z) {
        double xDist = x - this.originX;
        double yDist = y - this.originY;
        double zDist = z - this.originZ;

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    public double getDistance(double x, double z) {
        double xDist = x - this.originX;
        double zDist = z - this.originZ;

        return (xDist * xDist) + (zDist * zDist);
    }

    public long getRegionKey() {
        return this.regionKey;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public boolean isWithinFrustum(Frustum frustum) {
        return frustum.isBoxVisible(
                (float) (this.originX - 8.0),
                (float) (this.originY - 8.0),
                (float) (this.originZ - 8.0),
                (float) (this.originX + 8.0),
                (float) (this.originY + 8.0),
                (float) (this.originZ + 8.0)
        );
    }

    public int getFlags() {
        return this.flags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        RenderSection section = (RenderSection) o;
        return this.chunkX == section.chunkX &&
               this.chunkY == section.chunkY &&
               this.chunkZ == section.chunkZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.chunkX, this.chunkY, this.chunkZ);
    }

    public SectionMeta meta;
    private boolean shouldRender() {
        if (uploadedGeometrySegment == BufferSegment.INVALID)
            return false;
        if (data == ChunkRenderData.EMPTY || data == ChunkRenderData.ABSENT)
            return false;
        if (data.models == null)
            return false;
        return true;
    }


    private void updateMeta(RenderRegion region, ChunkRenderData Old, ChunkRenderData New) {
        if (shouldRender() == (meta == null)) {
            if (meta == null) {
                meta = new SectionMeta();
                region.sectionReady(this);
                //Request new meta
            } else {
                //Delete and free old meta
                region.sectionDestroy(this);
                meta = null;
                return;
            }
        }
        if (!shouldRender())
            return;
        //Update and submit new meta upload

        if (!Old.bounds.equals(New.bounds)) {
            region.chunkBoundsUpdate(this, Old.bounds, New.bounds);
        }

        //TODO: update meta
        meta.regionId = region.meta.id;
        meta.aabb.set(data.bounds);
        meta.sectionPos.x = chunkX<<4;
        meta.sectionPos.y = chunkY<<4;
        meta.sectionPos.z = chunkZ<<4;

        meta.visbitmask = 0;
        for (int i = 0; i < 3; i++) {
            var pass = data.models[i];
            if (pass == null)
                continue;
            meta.visbitmask |= (pass.getVisibilityBits()&0xFF) << (i*8);
            RenderPassRanges ranges = meta.ranges[i];
            for (int j = 0; j < 7; j++) {
                var range = ranges.ranges[j];
                range.start = BufferSegment.getOffset(pass.getModelPartSegments()[j]) + BufferSegment.getOffset(uploadedGeometrySegment);
                range.count = 6*(BufferSegment.getLength(pass.getModelPartSegments()[j])>>2);//Convert to correct format
            }
        }

        region.enqueueSectionMetaUpdate(this);
    }
}
