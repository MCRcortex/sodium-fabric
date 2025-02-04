package net.caffeinemc.sodium.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.*;

import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.util.buffer.streaming.SectionedStreamingBuffer;
import net.caffeinemc.gfx.util.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.config.user.UserConfig;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.buffer.arena.PendingUpload;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderData;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.IntPool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.profiler.Profiler;

public class RenderRegionManager {
    // these constants have been found from experimentation
    static final int PRUNE_SAMPLE_SIZE = 100;
    private static final double PRUNE_RATIO_THRESHOLD = .35;
    private static final float PRUNE_PERCENT_MODIFIER = -.2f;
    private static final float DEFRAG_THRESHOLD = 0.000008f; // this may look dumb, but keep in mind that 1.0 is the absolute maximum
    
    // turn this into a 3d array if needed
    private final Long2ReferenceMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();
    private final IntPool idPool = new IntPool();

    private final IVertexBufferProvider bufferProvider;
    //private final BufferPool<ImmutableBuffer> bufferPool;

    private final RenderDevice device;
    private final TerrainVertexType vertexType;
    private final StreamingBuffer stagingBuffer;

    public RenderRegionManager(RenderDevice device, TerrainVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;

        var maxInFlightFrames = SodiumClientMod.options().advanced.cpuRenderAheadLimit + 1;
        this.stagingBuffer = new SectionedStreamingBuffer(
                device,
                1,
                0x80000, // start with 512KiB per section and expand from there if needed
                maxInFlightFrames,
                EnumSet.of(
                        MappedBufferFlags.EXPLICIT_FLUSH,
                        MappedBufferFlags.CLIENT_STORAGE
                )
        );


        bufferProvider = createDataStoreProvider();
    }

    public RenderRegion getRegion(long regionId) {
        return this.regions.get(regionId);
    }

    public RenderRegion getRegion(int x, int y, int z) {
        return this.regions.get(RenderRegion.getRegionCoord(x, y, z));
    }

    public RenderRegion getOrMakeRegionSectionPos(int x, int y, int z) {
        long regionKey = RenderRegion.getRegionCoord(x, y, z);
        return this.regions.computeIfAbsent(regionKey, key -> new RenderRegion(
                this.device,
                this.bufferProvider,
                this.idPool.create(),
                key
        ));
    }

    public void cleanup() {
        Iterator<RenderRegion> it = this.regions.values()
                .iterator();

        while (it.hasNext()) {
            RenderRegion region = it.next();

            if (region.isEmpty()) {
                this.deleteRegion(region);
                it.remove();
            }
        }
    
        long activeSize = this.getDeviceAllocatedMemoryActive();
        long reserveSize = this.bufferProvider.getDeviceAllocatedMemory();
        
        if ((double) reserveSize / activeSize > PRUNE_RATIO_THRESHOLD) {
            this.prune();
        }
    }
    
    public void prune() {
        this.bufferProvider.prune(PRUNE_PERCENT_MODIFIER);
    }

    public void uploadChunks(Iterator<TerrainBuildResult> queue, int frameIndex, @Deprecated RenderUpdateCallback callback) {
        Profiler profiler = MinecraftClient.getInstance().getProfiler();
        
        profiler.push("chunk_upload");
        
        // we have to use a list with a varied size here, because the upload method can create new regions
        ReferenceList<RenderRegion> writtenRegions = new ReferenceArrayList<>(Math.max(this.getRegionTableSize(), 16));
        
        for (var entry : this.setupUploadBatches(queue)) {
            this.uploadGeometryBatch(entry.getLongKey(), entry.getValue(), frameIndex);

            for (TerrainBuildResult result : entry.getValue()) {
                RenderSection section = result.render();

                if (section.getData() != null) {
                    callback.accept(
                            section.getSectionX(),
                            section.getSectionY(),
                            section.getSectionZ(),
                            section.getData(),
                            result.data()
                    );
                }

                section.setData(result.data());
                section.setLastAcceptedBuildTime(result.buildTime());

                result.delete();
    
                RenderRegion region = section.getRegion();
                if (region != null) {
                    // expand list as needed
                    int currentSize = writtenRegions.size();
                    int requiredSize = region.getId() + 1;
                    if (currentSize < requiredSize) {
                        writtenRegions.size(Math.max(requiredSize, currentSize * 2));
                    }
                    writtenRegions.set(region.getId(), region);
                }
            }
        }
    
        profiler.swap("chunk_defrag");
        
        // check if we need to defragment any of the regions we just modified
        for (RenderRegion region : writtenRegions) {
            // null entries will exist due to the nature of the ID based table
            if (region == null) {
                continue;
            }
            
            ArenaBuffer arenaBuffer = region.getVertexBuffer();
            if (arenaBuffer.getFragmentation() >= DEFRAG_THRESHOLD && SodiumClientMod.options().advanced.chunkRendererBackend != UserConfig.ChunkRendererBackend.GPU_DRIVEN) {
                LongSortedSet removedSegments = arenaBuffer.compact();
            
                if (removedSegments == null) {
                    continue;
                }
            
                // fix existing sections' buffer segment locations after the defrag
                for (RenderSection section : region.getSections()) {
                    long currentBufferSegment = section.getUploadedGeometrySegment();
                    int currentSegmentOffset = BufferSegment.getOffset(currentBufferSegment);
                    int currentSegmentLength = BufferSegment.getLength(currentBufferSegment);
                
                    for (long prevFreedSegment : removedSegments.headSet(currentBufferSegment)) {
                        currentSegmentOffset -= BufferSegment.getLength(prevFreedSegment);
                    }
                
                    long newBufferSegment = BufferSegment.createKey(currentSegmentLength, currentSegmentOffset);
                    section.setBufferSegment(newBufferSegment); // TODO: in the future, if something extra happens when this method is called, we should check if cur = new
                }
            }
        }
    
        profiler.pop();
    }

    public int getRegionTableSize() {
        return this.idPool.capacity();
    }

    public Collection<RenderRegion> getRegions() {
        return regions.values();
    }

    public Buffer getGlobalVertexBufferTHISISTEMPORARY() {
        return (bufferProvider.provide().getBufferObject());
    }

    public IVertexBufferProvider getProvider() {
        return bufferProvider;
    }

    public interface RenderUpdateCallback {
        void accept(int x, int y, int z, ChunkRenderData prev, ChunkRenderData next);
    }

    private void uploadGeometryBatch(long regionKey, List<TerrainBuildResult> results, int frameIndex) {
        List<PendingUpload> uploads = new ReferenceArrayList<>(results.size());

        for (TerrainBuildResult result : results) {
            var section = result.render();
            var geometry = result.geometry();

            // De-allocate all storage for the meshes we're about to replace
            // This will allow it to be cheaply re-allocated later
            section.ensureGeometryDeleted();

            var vertices = geometry.vertices();
    
            // Only submit an upload job if there is data in the first place
            if (vertices != null) {
                uploads.add(new PendingUpload(section, vertices.buffer()));
            }
        }

        // If we have nothing to upload, don't attempt to allocate a region
        if (uploads.isEmpty()) {
            return;
        }

        RenderRegion region = this.regions.get(regionKey);

        if (region == null) {
            region = new RenderRegion(
                    this.device,
                    this.bufferProvider,
                    this.idPool.create(),
                    regionKey
            );
            
            this.regions.put(regionKey, region);
        }
        
        region.submitUploads(uploads, frameIndex);
    }

    private Iterable<Long2ReferenceMap.Entry<List<TerrainBuildResult>>> setupUploadBatches(Iterator<TerrainBuildResult> renders) {
        var batches = new Long2ReferenceOpenHashMap<List<TerrainBuildResult>>();

        while (renders.hasNext()) {
            TerrainBuildResult result = renders.next();
            RenderSection render = result.render();

            // TODO: this is kinda gross, maybe find a way to make the Future dispose of the result when cancelled?
            if (render.isDisposed() || result.buildTime() <= render.getLastAcceptedBuildTime()) {
                result.delete();
                continue;
            }

            var batch = batches.computeIfAbsent(render.getRegionKey(), key -> new ReferenceArrayList<>());
            batch.add(result);
        }

        return batches.long2ReferenceEntrySet();
    }

    public void delete() {
        for (RenderRegion region : this.regions.values()) {
            region.delete();
        }
        this.regions.clear();
        
        this.bufferProvider.destroy();
        this.stagingBuffer.delete();
    }

    private void deleteRegion(RenderRegion region) {
        var id = region.getId();
        region.delete();

        this.idPool.free(id);
    }
    
    private long getDeviceAllocatedMemoryActive() {
        long sum = 0L;
        for (RenderRegion region : this.regions.values()) {
            long deviceAllocatedMemory = region.getDeviceAllocatedMemory();
            sum += deviceAllocatedMemory;
        }
        return sum;
    }
    
    public int getDeviceBufferObjects() {
        if (SodiumWorldRenderer.instance().getTerrainRenderer().isGlobalAllocation())
            return this.bufferProvider.getDeviceBufferObjects();
        return this.regions.size() + this.bufferProvider.getDeviceBufferObjects();
    }
    
    public long getDeviceUsedMemory() {
        // the buffer pool doesn't actively use any memory
        long sum = 0L;
        for (RenderRegion region : this.regions.values()) {
            long deviceUsedMemory = region.getDeviceUsedMemory();
            sum += deviceUsedMemory;
        }
        return sum + this.bufferProvider.getDeviceUsedMemory();
    }
    
    public long getDeviceAllocatedMemory() {
        return this.getDeviceAllocatedMemoryActive() + this.bufferProvider.getDeviceAllocatedMemory();
    }

    private IVertexBufferProvider createDataStoreProvider() {
        return createDataStoreProvider(SodiumClientMod.options().advanced.regionDataStore);
    }
    private IVertexBufferProvider createDataStoreProvider(UserConfig.RegionDataStore store) {
        return switch (store) {
            case DEFAULT -> device.properties().capabilities.sparseBuffers
                    ? createDataStoreProvider(UserConfig.RegionDataStore.SPARSE)
                    : (SodiumClientMod.options().advanced.chunkRendererBackend == UserConfig.ChunkRendererBackend.GPU_DRIVEN?
                        createDataStoreProvider(UserConfig.RegionDataStore.SINGLE)
                        :createDataStoreProvider(UserConfig.RegionDataStore.DISTINCT));

            case SPARSE -> new GlobalSparseAsyncBufferProvider(device, stagingBuffer, vertexType, 5000000000L);//2GB max size

            case SINGLE -> new GlobalSingleBufferProvider(device, stagingBuffer, vertexType);

            case DISTINCT -> new DistinctRecycledBufferProvider(device, stagingBuffer, vertexType);
        };
    }
}
