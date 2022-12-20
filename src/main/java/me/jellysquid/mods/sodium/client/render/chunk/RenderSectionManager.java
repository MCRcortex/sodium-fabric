package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphIterationQueue;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderEmptyBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import me.jellysquid.mods.sodium.client.util.collections.BitArray;
import me.jellysquid.mods.sodium.client.util.frustum.Frustum;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import me.jellysquid.mods.sodium.common.util.collections.WorkStealingFutureDrain;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RenderSectionManager {
    /**
     * The maximum distance a chunk can be from the player's camera in order to be eligible for blocking updates.
     */
    private static final double NEARBY_CHUNK_DISTANCE = Math.pow(32, 2.0);

    /**
     * The minimum distance the culling plane can be from the player's camera. This helps to prevent mathematical
     * errors that occur when the fog distance is less than 8 blocks in width, such as when using a blindness potion.
     */
    private static final float FOG_PLANE_MIN_DISTANCE = (float) Math.pow(8.0f, 2.0);

    /**
     * The distance past the fog's far plane at which to begin culling. Distance calculations use the center of each
     * chunk from the camera's position, and as such, special care is needed to ensure that the culling plane is pushed
     * back far enough. I'm sure there's a mathematical formula that should be used here in place of the constant,
     * but this value works fine in testing.
     */
    private static final float FOG_PLANE_OFFSET = 12.0f;

    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final ClonedChunkSectionCache sectionCache;
    private final Map<ChunkUpdateType, PriorityQueue<RenderSection>> rebuildQueues = new EnumMap<>(ChunkUpdateType.class);

    private final ChunkRenderList chunkRenderList = new ChunkRenderList();

    private final RegionChunkRenderer chunkRenderer;

    private final SodiumWorldRenderer worldRenderer;
    private final ClientWorld world;

    private final int renderDistance;

    private boolean needsUpdate;

    private final ChunkTracker tracker;

    final SectionState sstate;
    private final SectionCulling scull;

    int currentFrame = 0;
    boolean alwaysDeferChunkUpdates = false;//TODO: CHECK THIS


    public RenderSectionManager(SodiumWorldRenderer worldRenderer, BlockRenderPassManager renderPassManager, ClientWorld world, int renderDistance, CommandList commandList) {
        this.chunkRenderer = new RegionChunkRenderer(RenderDevice.INSTANCE, ChunkModelVertexFormats.DEFAULT);

        this.worldRenderer = worldRenderer;
        this.world = world;

        this.builder = new ChunkBuilder(ChunkModelVertexFormats.DEFAULT);
        this.builder.init(world, renderPassManager);

        this.needsUpdate = true;
        this.renderDistance = renderDistance;

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.world);

        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.rebuildQueues.put(type, new ObjectArrayFIFOQueue<>());
        }

        this.tracker = this.worldRenderer.getChunkTracker();

        sstate = new SectionState(renderDistance, world);
        scull = new SectionCulling(sstate);
    }

    public void reloadChunks(ChunkTracker tracker) {
        tracker.getChunks(ChunkStatus.FLAG_HAS_BLOCK_DATA)
                .forEach(pos -> this.onChunkAdded(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos)));

        tracker.getChunks(ChunkStatus.FLAG_ALL)
                .forEach(pos -> this.scheduleReadyChunk(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos)));
    }

    public void update(Camera camera, Frustum frustum, int frame, boolean spectator) {
        currentFrame = frame;
        this.resetLists();

        scull.calculateVisibleSections(frustum, true, new Vector3d(camera.getPos().x, camera.getPos().y, camera.getPos().z));
        chunkRenderList.clear();
        for (int i = 0; i < scull.sortedSectionList.terrainSectionCount; i++) {
            chunkRenderList.add(sstate.sections[scull.sortedSectionList.terrainSectionIdxs[i]]);
        }
        for (int i = 0; i < scull.sortedSectionList.importantUpdateSectionCount; i++) {
            performScheduling(sstate.sections[scull.sortedSectionList.importantUpdatableSectionIdxs[i]]);
        }
        for (int i = 0; i < scull.sortedSectionList.secondaryUpdateSectionCount; i++) {
            performScheduling(sstate.sections[scull.sortedSectionList.secondaryUpdatableSectionIdxs[i]]);
        }

        this.needsUpdate = false;
    }

    private void performScheduling(RenderSection section) {
        if (section.getPendingUpdate() != null) {
            var queue = this.rebuildQueues.get(section.getPendingUpdate());

            if (queue.size() >= 32) {
                return;
            }

            queue.enqueue(section);
        }
    }

    private void resetLists() {
        for (var queue : this.rebuildQueues.values()) {
            queue.clear();
        }

        this.chunkRenderList.clear();
    }

    public Iterator<BlockEntity> getVisibleBlockEntities() {
        return Arrays.stream(scull.sortedSectionList.blockEntitySectionIdxs, 0, scull.sortedSectionList.blockEntitySectionCount)
                .mapToObj(id->sstate.sections[id])
                .flatMap(section -> section.getData()
                        .getBlockEntities()
                        .stream())
                .iterator();
    }

    public void onChunkAdded(int x, int z) {
        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.needsUpdate |= this.loadSection(x, y, z);
        }
    }

    public void onChunkRemoved(int x, int z) {
        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.needsUpdate |= this.unloadSection(x, y, z);
        }
    }

    private boolean loadSection(int x, int y, int z) {
        var id = this.sstate.getSectionIdx(x, y, z);

        if (this.sstate.sectionExistenceBits.get(id)) {
            throw new IllegalStateException("Chunk is already loaded: " + ChunkSectionPos.from(x, y, z));
        }

        RenderRegion region = this.regions.createRegionForChunk(x, y, z);

        RenderSection render = new RenderSection(this.worldRenderer, x, y, z, region);
        region.addChunk(render);

        sstate.markLoaded(x, y, z, render);

        return true;
    }

    private boolean unloadSection(int x, int y, int z) {
        var id = this.sstate.getSectionIdx(x, y, z);

        if (!this.sstate.sectionExistenceBits.get(id)) {
            throw new IllegalStateException("Chunk is not loaded: " + ChunkSectionPos.from(x, y, z));
        }

        RenderSection chunk = sstate.unmarkLoaded(x, y, z);

        chunk.delete();

        RenderRegion region = chunk.getRegion();
        region.removeChunk(chunk);

        this.readyColumns.remove(ChunkSectionPos.asLong(x, y, z));

        tracker.unloadChunk(x,z);
        return true;
    }

    public void renderLayer(ChunkRenderMatrices matrices, BlockRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrices, commandList, this.chunkRenderList, pass, new ChunkCameraContext(x, y, z));

        commandList.flush();
    }

    public void tickVisibleRenders() {
        for (int i = 0; i < scull.sortedSectionList.tickingTextureSectionCount; i++) {
            sstate.sections[scull.sortedSectionList.tickingTextureSectionIdxs[i]].tick();
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        return this.scull.isVisible(x, y, z);
    }

    public void updateChunks() {
        this.updateChunks(false);
    }

    public void updateAllChunksNow() {
        this.updateChunks(true);

        // Also wait for any rebuilds which had already been scheduled before this method was called
        this.needsUpdate |= this.performAllUploads();
    }

    private void updateChunks(boolean allImmediately) {
        var blockingFutures = new LinkedList<CompletableFuture<ChunkBuildResult>>();

        var sectionCache = new ClonedChunkSectionCache(this.world);

        this.submitRebuildTasks(ChunkUpdateType.IMPORTANT_REBUILD, blockingFutures, sectionCache);
        this.submitRebuildTasks(ChunkUpdateType.INITIAL_BUILD, allImmediately ? blockingFutures : null, sectionCache);
        this.submitRebuildTasks(ChunkUpdateType.REBUILD, allImmediately ? blockingFutures : null, sectionCache);

        // Try to complete some other work on the main thread while we wait for rebuilds to complete
        this.needsUpdate |= this.performPendingUploads();

        if (!blockingFutures.isEmpty()) {
            this.needsUpdate = true;
            this.regions.upload(RenderDevice.INSTANCE.createCommandList(), new WorkStealingFutureDrain<>(blockingFutures, this.builder::stealTask));
        }

        this.regions.cleanup();
    }

    private void submitRebuildTasks(ChunkUpdateType filterType, LinkedList<CompletableFuture<ChunkBuildResult>> immediateFutures, ClonedChunkSectionCache sectionCache) {
        int budget = immediateFutures != null ? Integer.MAX_VALUE : this.builder.getSchedulingBudget();

        PriorityQueue<RenderSection> queue = this.rebuildQueues.get(filterType);

        while (budget > 0 && !queue.isEmpty()) {
            RenderSection section = queue.dequeue();

            if (section.isDisposed()) {
                continue;
            }

            // Sections can move between update queues, but they won't be removed from the queue they were
            // previously in to save CPU cycles. We just filter any changed entries here instead.
            if (section.getPendingUpdate() != filterType) {
                continue;
            }

            ChunkRenderBuildTask task = this.createRebuildTask(sectionCache, section);
            CompletableFuture<?> future;

            if (immediateFutures != null) {
                CompletableFuture<ChunkBuildResult> immediateFuture = this.builder.schedule(task);
                immediateFutures.add(immediateFuture);

                future = immediateFuture;
            } else {
                future = this.builder.scheduleDeferred(task);
            }

            section.onBuildSubmitted(future);

            budget--;
        }
    }

    private boolean performPendingUploads() {
        Iterator<ChunkBuildResult> it = this.builder.createDeferredBuildResultDrain();

        if (!it.hasNext()) {
            return false;
        }

        this.regions.upload(RenderDevice.INSTANCE.createCommandList(), it);

        return true;
    }

    /**
     * Processes all build task uploads, blocking for tasks to complete if necessary.
     */
    private boolean performAllUploads() {
        boolean anythingUploaded = false;

        while (true) {
            // First check if all tasks are done building (and therefore the upload queue is final)
            boolean allTasksBuilt = this.builder.isIdle();

            // Then process the entire upload queue
            anythingUploaded |= this.performPendingUploads();

            // If the upload queue was the final one
            if (allTasksBuilt) {
                // then we are done
                return anythingUploaded;
            } else {
                // otherwise we need to wait for the worker threads to make progress
                try {
                    // This code path is not the default one, it doesn't need super high performance, and having the
                    // workers notify the main thread just for it is probably not worth it.
                    //noinspection BusyWait
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
        }
    }

    public ChunkRenderBuildTask createRebuildTask(ClonedChunkSectionCache sectionCache, RenderSection render) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, render.getChunkPos(), sectionCache);
        int frame = this.currentFrame;

        if (context == null) {
            return new ChunkRenderEmptyBuildTask(render, frame);
        }

        return new ChunkRenderRebuildTask(render, context, frame);
    }

    public void markGraphDirty() {
        this.needsUpdate = true;
    }

    public boolean isGraphDirty() {
        return this.needsUpdate;
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.resetLists();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
        }

        this.chunkRenderer.delete();
        this.builder.stopWorkers();
    }

    public int getTotalSections() {
        int sum = 0;

        for (RenderRegion region : this.regions.getLoadedRegions()) {
            sum += region.getChunkCount();
        }

        return sum;
    }

    public int getVisibleChunkCount() {
        return this.chunkRenderList.getCount();
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        if ((!this.canSubmitChunkForRebuilds(x, z))||!scull.isInRenderDistance(x, y, z)) {
            return;
        }
        if (this.sstate.getSectionIdx(x, y, z)<0 || this.sstate.getSectionIdx(x, y, z)>=sstate.sections.length) {
            System.err.println("asddsfgdfh");
        }
        RenderSection section = sstate.sections[this.sstate.getSectionIdx(x, y, z)];

        if (section != null && section.isBuilt()) {
            if (!this.alwaysDeferChunkUpdates && important) {
                section.markForUpdate(ChunkUpdateType.IMPORTANT_REBUILD);
            } else {
                section.markForUpdate(ChunkUpdateType.REBUILD);
            }
            sstate.setChunkDataFlags(section);
        }

        this.needsUpdate = true;
    }

    public void onChunkRenderUpdates(int x, int y, int z, ChunkRenderData data) {
        sstate.setChunkVisibility(x,y,z, data.getOcclusionData());
    }


    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        Iterator<RenderRegion.RenderRegionArenas> it = this.regions.getLoadedRegions()
                .stream()
                .map(RenderRegion::getArenas)
                .filter(Objects::nonNull)
                .iterator();

        int count = 0;

        long deviceUsed = 0;
        long deviceAllocated = 0;

        while (it.hasNext()) {
            RenderRegion.RenderRegionArenas arena = it.next();
            deviceUsed += arena.getDeviceUsedMemory();
            deviceAllocated += arena.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Chunk arena allocator: %s", SodiumClientMod.options().advanced.arenaMemoryAllocator.name()));
        list.add(String.format("Device buffer objects: %d", count));
        list.add(String.format("Device memory: %d/%d MiB", MathUtil.toMib(deviceUsed), MathUtil.toMib(deviceAllocated)));
        list.add(String.format("Staging buffer: %s", this.regions.getStagingBuffer().toString()));
        return list;
    }

    private final LongSet readyColumns = new LongOpenHashSet();
    public void onChunkDataChanged(int x, int z) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int adjX = x + offsetX;
                int adjZ = z + offsetZ;

                if (!this.canSubmitChunkForRebuilds(adjX, adjZ)) {
                    continue;
                }

                if (!this.readyColumns.add(ChunkPos.toLong(adjX, adjZ))) {
                    continue;
                }

                this.scheduleReadyChunk(adjX, adjZ);
            }
        }
    }

    private boolean canSubmitChunkForRebuilds(int x, int z) {
        return this.tracker.hasMergedFlags(x, z, ChunkStatus.FLAG_ALL);
    }

    private void scheduleReadyChunk(int x, int z) {
        Chunk chunk = this.world.getChunk(x, z);

        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            var render = sstate.sections[this.sstate.getSectionIdx(x, y, z)];

            if (render == null) {
                throw new IllegalStateException("Tried to mark section as ready, but it wasn't loaded? [x=%s, y=%s, z=%s]".formatted(x, y, z));
            }

            ChunkSection section = chunk.getSectionArray()[this.world.sectionCoordToIndex(y)];

            if (section.isEmpty()) {
                render.setData(ChunkRenderData.EMPTY);
            } else {
                render.markForUpdate(ChunkUpdateType.INITIAL_BUILD);
                sstate.setChunkDataFlags(render);
            }
        }
    }

    public void notifyChunksChanged(LongSet dirtyChunks) {
        var it = dirtyChunks.iterator();
        while (it.hasNext()) {
            var key = it.nextLong();
            this.onChunkDataChanged(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        }
    }
}
