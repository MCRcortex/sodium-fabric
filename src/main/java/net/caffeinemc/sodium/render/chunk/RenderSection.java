package net.caffeinemc.sodium.render.chunk;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.config.user.UserConfig;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.chunk.cull.gpu.structs.RenderPassRanges;
import net.caffeinemc.sodium.render.chunk.cull.gpu.structs.SectionMeta;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderData;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection {
    private final long regionKey;
    public final int innerRegionKey;
    private RenderRegion region;

    private final int sectionX, sectionY, sectionZ;
    private final double centerX, centerY, centerZ;

    private ChunkRenderData data = ChunkRenderData.ABSENT;
    private CompletableFuture<?> rebuildTask = null;

    private ChunkUpdateType pendingUpdate;
    private long uploadedGeometrySegment = BufferSegment.INVALID;

    private boolean disposed;

    private int lastAcceptedBuildTime = -1;
    private int flags;

    public RenderSection(int sectionX, int sectionY, int sectionZ) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;

        this.centerX = ChunkSectionPos.getBlockCoord(this.sectionX) + 8.0;
        this.centerY = ChunkSectionPos.getBlockCoord(this.sectionY) + 8.0;
        this.centerZ = ChunkSectionPos.getBlockCoord(this.sectionZ) + 8.0;

        this.regionKey = RenderRegion.getRegionCoord(this.sectionX, this.sectionY, this.sectionZ);
        this.innerRegionKey = RenderRegion.getInnerCoord(this.sectionX, this.sectionY, this.sectionZ);
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
            region = SodiumWorldRenderer.instance().getTerrainRenderer().regionManager.getRegion(sectionX, sectionY, sectionZ);
        if (region != null)
            region.deletedSection(this);
        if (SodiumClientMod.options().advanced.chunkRendererBackend == UserConfig.ChunkRendererBackend.GPU_DRIVEN)
            updateMeta(region, data, data);
    }

    public void setData(ChunkRenderData data) {
        if (data == null) {
            throw new NullPointerException("Mesh information must not be null");
        }
        ChunkRenderData old = this.data;
        this.data = data;
        this.flags = data.getFlags();
        if (SodiumClientMod.options().advanced.chunkRendererBackend == UserConfig.ChunkRendererBackend.GPU_DRIVEN)
            updateMeta(region, old, data);
    }

    /**
     * Returns the chunk section position which this render refers to in the world.
     */
    public ChunkSectionPos getChunkPos() {
        return ChunkSectionPos.from(this.sectionX, this.sectionY, this.sectionZ);
    }

    public int getSectionX() {
        return this.sectionX;
    }

    public int getSectionY() {
        return this.sectionY;
    }

    public int getSectionZ() {
        return this.sectionZ;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    @Override
    public String toString() {
        return String.format("RenderChunk{chunkX=%d, chunkY=%d, chunkZ=%d}",
                             this.sectionX, this.sectionY, this.sectionZ
        );
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
        if (SodiumClientMod.options().advanced.chunkRendererBackend == UserConfig.ChunkRendererBackend.GPU_DRIVEN)
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

    public double getDistanceSq(double x, double y, double z) {
        double xDist = x - this.centerX;
        double yDist = y - this.centerY;
        double zDist = z - this.centerZ;

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    public double getDistanceSq(double x, double z) {
        double xDist = x - this.centerX;
        double zDist = z - this.centerZ;

        return (xDist * xDist) + (zDist * zDist);
    }

    public long getRegionKey() {
        return this.regionKey;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public int getFlags() {
        return this.flags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        RenderSection section = (RenderSection) o;
        return this.sectionX == section.sectionX &&
               this.sectionY == section.sectionY &&
               this.sectionZ == section.sectionZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sectionX, this.sectionY, this.sectionZ);
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
        meta.sectionPos.x = sectionX<<4;
        meta.sectionPos.y = sectionY<<4;
        meta.sectionPos.z = sectionZ<<4;

        meta.visbitmask = 0;
        for (int i = 0; i < 3; i++) {
            var pass = data.models[i];
            if (pass == null)
                continue;
            meta.visbitmask |= (pass.getVisibilityBits()&0xFF) << (i*8);
            RenderPassRanges ranges = meta.ranges[i];
            for (int j = 0; j < 7; j++) {
                if ((pass.getVisibilityBits()&(1<<j)) == 0)
                    continue;
                var range = ranges.ranges[j];
                range.start = BufferSegment.getOffset(pass.getModelPartSegments()[j]) + BufferSegment.getOffset(uploadedGeometrySegment);
                range.count = 6*(BufferSegment.getLength(pass.getModelPartSegments()[j])>>2);//Convert to correct format
            }
        }
        if (data.models[3] != null) {
            var pass = data.models[3];
            meta.visbitmask |= (pass.getVisibilityBits()&0xFF) << (24);
            int start = Integer.MAX_VALUE;
            meta.translucency.count = 0;
            for (int i = 0; i < 7; i++) {
                if ((pass.getVisibilityBits()&(1<<i)) == 0)
                    continue;
                start = Math.min(start, BufferSegment.getOffset(pass.getModelPartSegments()[i]));
                meta.translucency.count += 6*(BufferSegment.getLength(pass.getModelPartSegments()[i])>>2);
            }
            meta.translucency.start = start + BufferSegment.getOffset(uploadedGeometrySegment);
        }

        region.enqueueSectionMetaUpdate(this);
    }
}
