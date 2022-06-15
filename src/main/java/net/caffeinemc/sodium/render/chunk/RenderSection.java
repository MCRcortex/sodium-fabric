package net.caffeinemc.sodium.render.chunk;

import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.buffer.VertexRange;
import net.caffeinemc.sodium.render.chunk.occlussion.SectionMeta;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.DefaultRenderPasses;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.state.*;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    private final float originX, originY, originZ;

    private ChunkRenderData data = ChunkRenderData.ABSENT;
    private CompletableFuture<?> rebuildTask = null;

    private ChunkUpdateType pendingUpdate;
    private UploadedChunkGeometry uploadedGeometry;
    private SectionMeta sectionMeta;

    private boolean disposed;

    private int lastAcceptedBuildTime = -1;
    private int flags;

    public RenderSection(int chunkX, int chunkY, int chunkZ, int id) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        this.originX = ChunkSectionPos.getBlockCoord(this.chunkX) + 8;
        this.originY = ChunkSectionPos.getBlockCoord(this.chunkY) + 8;
        this.originZ = ChunkSectionPos.getBlockCoord(this.chunkZ) + 8;

        this.id = id;
        this.regionKey = RenderRegion.getRegionCoord(this.chunkX, this.chunkY, this.chunkZ);
        this.innerRegionKey = RenderRegion.getInnerRegionCoord(this.chunkX, this.chunkY, this.chunkZ);
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

    public ChunkRenderData data() {
        return this.data;
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        this.cancelRebuildTask();
        if (region != null)
            region.freeMetaSection(this);
        this.deleteGeometry();

        this.disposed = true;
    }

    public void setData(ChunkRenderData data) {
        if (data == null) {
            throw new NullPointerException("Mesh information must not be null");
        }

        this.data = data;
        this.flags = data.getFlags();
        onGeoUpdate();
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

    public ChunkRenderBounds getBounds() {
        return this.data.bounds;
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
        if (this.rebuildTask != null) {
            this.rebuildTask.cancel(false);
            this.rebuildTask = null;
        }

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

    public void deleteGeometry() {
        if (this.uploadedGeometry != null) {
            this.uploadedGeometry.delete();
            this.uploadedGeometry = null;
            this.region = null;
        }
    }

    public void updateGeometry(RenderRegion region, UploadedChunkGeometry geometry) {
        this.deleteGeometry();
        this.uploadedGeometry = geometry;
        this.region = region;
        if (this.sectionMeta != null) {
            this.sectionMeta = region.sectionGeometryUpdated(this);
        } else {
            this.sectionMeta = region.requestNewMetaSection(this);
        }
        onGeoUpdate();
    }

    //FIXME: ULTRA BAAD DONT DO HACK TO FIGURE OUT IF THIS IS THE ISSUE
    int measuredBase = 0;
    private void onGeoUpdate() {
        if (data == ChunkRenderData.EMPTY || data == ChunkRenderData.ABSENT || data == null)
            return;
        if (uploadedGeometry == null)
            return;

        Vector3f secPos = new Vector3f(getSectionPos()).mul(16);
        sectionMeta.setPos(secPos);
        sectionMeta.setAABB(
                new Vector3f(data.bounds.x1-(chunkX<<4),
                        data.bounds.y1-(chunkY<<4),
                        data.bounds.z1-(chunkZ<<4))
                            .add(secPos)
                            .add(0.5f, 0.5f, 0.5f),
                new Vector3f(data.bounds.x2 - data.bounds.x1,
                        data.bounds.y2 - data.bounds.y1,
                        data.bounds.z2 - data.bounds.z1));//FIxME: if in - coords need to -1 from each dim that is in neg coords

        measuredBase = uploadedGeometry.segment.getOffset();
        sectionMeta.reset();
        for (UploadedChunkGeometry.PackedModel model : uploadedGeometry.models) {
            if (model.pass == DefaultRenderPasses.SOLID) {
                for (long p : model.ranges) {
                    int face = Integer.numberOfTrailingZeros(UploadedChunkGeometry.ModelPart.unpackFace(p));
                    sectionMeta.SOLID[face] = new VertexRange(
                            UploadedChunkGeometry.ModelPart.unpackFirstVertex(p)+uploadedGeometry.segment.getOffset(),
                            UploadedChunkGeometry.ModelPart.unpackVertexCount(p));
                }
            }
            if (model.pass == DefaultRenderPasses.CUTOUT) {
                for (long p : model.ranges) {
                    int face = Integer.numberOfTrailingZeros(UploadedChunkGeometry.ModelPart.unpackFace(p));
                    sectionMeta.CUTOUT[face] = new VertexRange(
                            UploadedChunkGeometry.ModelPart.unpackFirstVertex(p)+uploadedGeometry.segment.getOffset(),
                            UploadedChunkGeometry.ModelPart.unpackVertexCount(p));
                }
            }
            if (model.pass == DefaultRenderPasses.CUTOUT_MIPPED) {
                for (long p : model.ranges) {
                    int face = Integer.numberOfTrailingZeros(UploadedChunkGeometry.ModelPart.unpackFace(p));
                    sectionMeta.CUTOUT_MIPPED[face] = new VertexRange(
                            UploadedChunkGeometry.ModelPart.unpackFirstVertex(p)+uploadedGeometry.segment.getOffset(),
                            UploadedChunkGeometry.ModelPart.unpackVertexCount(p));
                }
            }
        }
        sectionMeta.flush();
    }

    private Vector3i getSectionPos() {
        return new Vector3i(chunkX&(RenderRegion.REGION_LENGTH-1),chunkY&(RenderRegion.REGION_HEIGHT-1),chunkZ&(RenderRegion.REGION_WIDTH-1));
    }

    public UploadedChunkGeometry getGeometry() {
        return this.uploadedGeometry;
    }

    public int id() {
        return this.id;
    }

    public float getDistance(float x, float y, float z) {
        float xDist = x - this.originX;
        float yDist = y - this.originY;
        float zDist = z - this.originZ;

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    public float getDistance(float x, float z) {
        if (uploadedGeometry != null && uploadedGeometry.segment != null && !disposed
            && uploadedGeometry.segment.getOffset() != measuredBase) {
            onGeoUpdate();
        }
        float xDist = x - this.originX;
        float zDist = z - this.originZ;

        return (xDist * xDist) + (zDist * zDist);
    }

    public long getRegionKey() {
        return this.regionKey;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public boolean isWithinFrustum(Frustum frustum) {
        return frustum.isBoxVisible(this.originX - 8.0f, this.originY - 8.0f, this.originZ - 8.0f,
                this.originX + 8.0f, this.originY + 8.0f, this.originZ + 8.0f);
    }

    public int getFlags() {
        return this.flags;
    }

    public SectionMeta getMeta() {
        return sectionMeta;
    }
}
