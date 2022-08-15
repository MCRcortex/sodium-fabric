package net.caffeinemc.sodium.render.chunk.region;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.Set;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.PendingUpload;
import net.caffeinemc.sodium.render.chunk.ChunkUpdateType;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.RegionMeta;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderBounds;
import net.caffeinemc.sodium.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

public class RenderRegion {
    public static final int REGION_WIDTH = 16;
    public static final int REGION_HEIGHT = 16;
    public static final int REGION_LENGTH = 16;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    private static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    private static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    private static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    public static final int REGION_SIZE_M = REGION_SIZE-1;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final Set<RenderSection> sections = new ObjectOpenHashSet<>(REGION_SIZE);


    private final ArenaBuffer vertexBuffer;
    private final IVertexBufferProvider vbm;
    private final int id;
    public RegionMeta meta;

    public int renderDataIndex;
    public int lastFrameId;

    public double regionSortDistance;

    public final int regionX;
    public final int regionY;
    public final int regionZ;

    public final double regionCenterBlockX;
    public final double regionCenterBlockY;
    public final double regionCenterBlockZ;

    public RenderRegion(RenderDevice device, IVertexBufferProvider provider, int id, long packedPosition) {
        this.vertexBuffer = provider.provide();
        vbm = provider;
        this.id = id;
        if (this.id >= OcclusionEngine.MAX_REGIONS) {
            throw new IllegalStateException();
        }
        ChunkSectionPos pos  = ChunkSectionPos.from(packedPosition);
        regionX = pos.getX();
        regionY = pos.getY();
        regionZ = pos.getZ();

        regionCenterBlockX = (regionX*REGION_WIDTH+(double)REGION_WIDTH/2)*16+8;
        regionCenterBlockY = (regionY*REGION_HEIGHT+(double)REGION_HEIGHT/2)*16+8;
        regionCenterBlockZ = (regionZ*REGION_LENGTH+(double)REGION_LENGTH/2)*16+8;
    }

    /**
     * Uploads the given pending uploads to the buffers, adding sections to this region as necessary.
     */
    public void submitUploads(List<PendingUpload> pendingUploads, int frameIndex) {
        this.vertexBuffer.upload(pendingUploads, frameIndex);

        // Collect the upload results
        for (PendingUpload pendingUpload : pendingUploads) {
            long bufferSegment = pendingUpload.bufferSegmentResult.get();
            RenderSection section = pendingUpload.section;

            section.setGeometry(this, bufferSegment);
            this.sections.add(section);
        }
    }

    /**
     * Removes the given section from the region, and frees the vertex buffer segment associated with the section.
     */
    public void removeSection(RenderSection section) {
        this.vertexBuffer.free(section.getUploadedGeometrySegment());
        this.sections.remove(section);
    }

    public void delete() {
        vbm.remove(vertexBuffer);
    }

    public boolean isEmpty() {
        //return this.vertexBuffer.isEmpty();
        return this.importantBuilds.isEmpty() && this.updateBuilds.isEmpty() && initialBuilds.isEmpty() && pos2id.isEmpty() && sections.isEmpty();
    }

    public long getDeviceUsedMemory() {
        if (SodiumWorldRenderer.instance().getTerrainRenderer().isGlobalAllocation())
            return 0;
        return this.vertexBuffer.getDeviceUsedMemory();
    }

    public long getDeviceAllocatedMemory() {
        if (SodiumWorldRenderer.instance().getTerrainRenderer().isGlobalAllocation())
            return 0;
        return this.vertexBuffer.getDeviceAllocatedMemory();
    }

    public static long getRegionCoord(int chunkX, int chunkY, int chunkZ) {
        return ChunkSectionPos.asLong(chunkX >> REGION_WIDTH_SH, chunkY >> REGION_HEIGHT_SH, chunkZ >> REGION_LENGTH_SH);
    }

    public static int getInnerCoord(int chunkX, int chunkY, int chunkZ) {
        return (((chunkX & ((1<<REGION_WIDTH_SH)-1))*REGION_HEIGHT+ (chunkY & ((1<<REGION_HEIGHT_SH)-1))) * REGION_LENGTH + (chunkZ & ((1<<REGION_LENGTH_SH)-1)));
    }

    public Set<RenderSection> getSections() {
        return this.sections;
    }

    public ArenaBuffer getVertexBuffer() {
        return this.vertexBuffer;
    }

    public int getId() {
        return this.id;
    }


    public int sectionCount = 0;
    private final Int2ObjectOpenHashMap<RenderSection> sectionMetaMap = new Int2ObjectOpenHashMap<>();
    //TODO: optimize this as RenderSections already have position id
    private final Int2IntOpenHashMap pos2id = new Int2IntOpenHashMap();
    private final IntAVLTreeSet freeIds = new IntAVLTreeSet();

    private int newSectionId() {
        if (freeIds.isEmpty()) {
            if (sectionCount>=REGION_SIZE) {
                //TODO: print error in log/throw/raise exception
                return -1;
            }
            return sectionCount++;//+1;//Offset by 1 so that 0 is always free
        }
        int id = freeIds.firstInt();
        freeIds.remove(id);
        return id;
    }

    public synchronized void sectionReady(RenderSection section) {
        int id = newSectionId();
        pos2id.put(section.innerRegionKey, id);
        section.meta.id = id;
        sectionMetaMap.put(id, section);
        updateRegionMeta();
    }

    public synchronized void sectionDestroy(RenderSection section) {
        SodiumWorldRenderer.instance().getOcclusionEngine().sectionMeta.remove(section);

        int id = pos2id.remove(section.innerRegionKey);
        if (id == sectionCount-1) {
            //Free as many sections as possible
            sectionCount--;
            while ((!freeIds.isEmpty()) && freeIds.lastInt() == sectionCount -1) {
                freeIds.remove(freeIds.lastInt());
                sectionCount--;
            }
            if (sectionCount == 0 && !freeIds.isEmpty())
                throw new IllegalStateException();
        } else {
            //Enqueue id
            freeIds.add(id);
        }
        if (!shouldRender())
            updateRegionMeta();
    }

    //TODO: Group and batch upload instead of per region invalidation or some shit
    public void enqueueSectionMetaUpdate(RenderSection section) {
        SodiumWorldRenderer.instance().getOcclusionEngine().sectionMeta.update(section);
    }

    public void chunkBoundsUpdate(RenderSection section, ChunkRenderBounds Old, ChunkRenderBounds New) {
        //TODO: this: update region bounding meta

        if (meta.aabb.isOnInsideBoarder(Old)) {
            //TODO: need to update the entire AABB of the region, i.e. recreate it and enumerate over all sections aabbs
            meta.aabb.set(New);
            for (var sec : sectionMetaMap.values()) {
                meta.aabb.ensureContains(sec.getData().bounds);
            }
        } else {
            meta.aabb.setOrExpand(New);
        }
        updateRegionMeta();
    }

    public boolean shouldRender() {
        if (vertexBuffer.isEmpty())
            return false;
        if (pos2id.isEmpty())
            return false;
        return true;
    }

    private void updateRegionMeta() {
        if (shouldRender() == (meta == null)) {
            if (meta == null) {
                //System.out.println("NEW REGION ALLOCATION");
                meta = new RegionMeta();
                meta.id = id - 1;//FIXME: THE -1 IS CAUSE THE ID POOL STARTS AT 1 this is not good to do this
                if (pos2id.size() != 1)
                    throw new IllegalStateException();
                for (var sec : sectionMetaMap.values()) {
                    meta.aabb.set(sec.getData().bounds);
                }
            } else {
                //System.out.println("DESTROY REGION ALLOCATION");
                SodiumWorldRenderer.instance().getOcclusionEngine().regionMeta.remove(this);
                meta = null;
                return;
            }
        }

        meta.sectionCount = sectionCount;
        meta.sectionStart = meta.id * REGION_SIZE;
        SodiumWorldRenderer.instance().getOcclusionEngine().regionMeta.update(this);
    }


    private ObjectLinkedOpenHashSet<RenderSection> initialBuilds = new ObjectLinkedOpenHashSet<>();
    public void tickInitialBuilds() {
        //TODO: maybe scale count with distance to camera
        for (int i = 0; i < Math.min((meta != null?20:10), initialBuilds.size()); i++) {
            //TODO: do inital builds via event based
            // e.g. when section next to it becomes flagged enqueu surrounding etc
            // this.tracker.hasMergedFlags(section.getChunkX(), section.getChunkZ(), ChunkStatus.FLAG_ALL)

            //NOTE: its done like this to cycle the render sections in the list (dw its O(1))
            RenderSection section = initialBuilds.removeFirst();
            if (section.getPendingUpdate() == ChunkUpdateType.INITIAL_BUILD && !section.isDisposed()) {
                SodiumWorldRenderer.instance().getTerrainRenderer().schedulePendingUpdates(section);
                initialBuilds.addAndMoveToLast(section);
            }
        }
    }

    public void sectionInitialBuild(RenderSection section) {
        initialBuilds.addAndMoveToLast(section);
    }

    private ObjectLinkedOpenHashSet<RenderSection> importantBuilds = new ObjectLinkedOpenHashSet<>();
    private ObjectLinkedOpenHashSet<RenderSection> updateBuilds = new ObjectLinkedOpenHashSet<>();

    public void tickEnqueuedBuilds() {
        for (int i = 0; i < Math.min(32, importantBuilds.size()); i++) {
            RenderSection section = importantBuilds.removeFirst();
            if (section.getPendingUpdate() == ChunkUpdateType.IMPORTANT_REBUILD && !section.isDisposed()) {
                SodiumWorldRenderer.instance().getTerrainRenderer().schedulePendingUpdates(section);
                importantBuilds.addAndMoveToLast(section);
            }
        }
        for (int i = 0; i < Math.min(16, updateBuilds.size()); i++) {
            RenderSection section = updateBuilds.removeFirst();
            if (section.getPendingUpdate() == ChunkUpdateType.REBUILD && !section.isDisposed()) {
                SodiumWorldRenderer.instance().getTerrainRenderer().schedulePendingUpdates(section);
                updateBuilds.addAndMoveToLast(section);
            }
        }
    }

    public void scheduleSectionUpdate(RenderSection section) {
        if (section.getPendingUpdate() == ChunkUpdateType.IMPORTANT_REBUILD) {
            importantBuilds.addAndMoveToLast(section);
        } else {
            if (section.getPendingUpdate() != ChunkUpdateType.REBUILD) {
                throw new IllegalStateException();
            }
            updateBuilds.addAndMoveToLast(section);
        }
    }

    public void deletedSection(RenderSection section) {
        initialBuilds.remove(section);
        importantBuilds.remove(section);
        updateBuilds.remove(section);
    }
}
