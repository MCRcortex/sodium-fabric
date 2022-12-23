package me.jellysquid.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphIterationQueue;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static me.jellysquid.mods.sodium.client.util.frustum.Frustum.Visibility.*;

public class RenderSectionManager {
    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final EnumMap<ChunkUpdateType, PriorityQueue<RenderSection>> rebuildQueues = new EnumMap<>(ChunkUpdateType.class);

    private final ChunkRenderList chunkRenderList;

    private final IntArrayList tickableChunks = new IntArrayList();
    private final IntArrayList entityChunks = new IntArrayList();

    private final RegionChunkRenderer chunkRenderer;

    private final SodiumWorldRenderer worldRenderer;
    private final ClientWorld world;

    private final int renderDistance;
    private final int bottomSectionCoord, topSectionCoord;

    private float cameraX, cameraY, cameraZ;
    private int centerChunkX, centerChunkY, centerChunkZ;

    private boolean needsUpdate;

    private boolean useOcclusionCulling;

    private Frustum frustum;

    private int currentFrame = 0;
    private boolean alwaysDeferChunkUpdates;
    private boolean useBlockFaceCulling;

    private static class State {
        private final int offsetZ, offsetY;
        private final int maskXZ, maskY;

        private final int rOffsetZ, rOffsetY;
        private final int rMaskXZ, rMaskY;

        public final RenderSection[] sections;
        public final long[] visible;
        public final byte[] frustumCache;
        public final byte[] regionFrustumCache;

        public int sectionCount = 0;

        public final long[] sectionTraversalData;
        public final int[] sortedSections;
        public int visibleChunksCount;
        public int visibleChunksQueue;

        public State(World world, int renderDistance) {
            int sizeXZ = MathHelper.smallestEncompassingPowerOfTwo((renderDistance * 2) + 1);
            int sizeY = MathHelper.smallestEncompassingPowerOfTwo(world.getTopSectionCoord() - world.getBottomSectionCoord());

            this.maskXZ = sizeXZ - 1;
            this.maskY = sizeY - 1;

            this.offsetZ = Integer.numberOfTrailingZeros(sizeXZ);
            this.offsetY = this.offsetZ * 2;

            int arraySize = sizeXZ * sizeY * sizeXZ;

            this.visible = BitArray.create(arraySize);

            this.sections = new RenderSection[arraySize];

            this.sectionTraversalData = new long[arraySize];
            this.sortedSections = new int[arraySize];

            this.frustumCache = new byte[arraySize / (4)];

            int rArraySize = (sizeXZ>>3)*(sizeXZ>>3)*(sizeY>>2);
            this.regionFrustumCache = new byte[rArraySize / (4)];
            this.rMaskXZ = (sizeXZ>>3) - 1;
            this.rMaskY = (sizeY>>2) - 1;
            this.rOffsetZ = Integer.numberOfTrailingZeros(sizeXZ>>3);
            this.rOffsetY = this.rOffsetZ * 2;
        }

        public void reset() {
            BitArray.clear(this.visible);

            Arrays.fill(this.frustumCache, (byte) 0);
            Arrays.fill(this.regionFrustumCache, (byte) 0);

        }

        public int getIndex(int x, int y, int z) {
            return ((y & this.maskY) << this.offsetY) |((z & this.maskXZ) << (this.offsetZ)) | (x & this.maskXZ);
        }

        public int getRIndex(int x, int y, int z) {
            return ((y & this.rMaskY) << this.rOffsetY) |((z & this.rMaskXZ) << (this.rOffsetZ)) | (x & this.rMaskXZ);
        }
    }

    private final State state;

    public RenderSectionManager(SodiumWorldRenderer worldRenderer, BlockRenderPassManager renderPassManager, ClientWorld world, int renderDistance, CommandList commandList) {
        this.chunkRenderer = new RegionChunkRenderer(RenderDevice.INSTANCE, ChunkModelVertexFormats.DEFAULT);

        this.worldRenderer = worldRenderer;
        this.world = world;

        this.builder = new ChunkBuilder(ChunkModelVertexFormats.DEFAULT);
        this.builder.init(world, renderPassManager);

        this.needsUpdate = true;
        this.renderDistance = renderDistance;

        this.regions = new RenderRegionManager(commandList);

        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.rebuildQueues.put(type, new ObjectArrayFIFOQueue<>());
        }

        this.bottomSectionCoord = this.world.getBottomSectionCoord();
        this.topSectionCoord = this.world.getTopSectionCoord();

        this.state = new State(this.world, renderDistance);
        this.chunkRenderList = new ChunkRenderList(this.regions);
    }

    public void update(Camera camera, Frustum frustum, int frame, boolean spectator) {
        this.resetLists();
        initSearch(camera, frustum, frame, spectator);
        this.setup(camera);
        this.iterateChunks3();
        this.needsUpdate = false;
    }

    private void setup(Camera camera) {
        Vec3d cameraPos = camera.getPos();

        this.cameraX = (float) cameraPos.x;
        this.cameraY = (float) cameraPos.y;
        this.cameraZ = (float) cameraPos.z;

        var options = SodiumClientMod.options();

        this.alwaysDeferChunkUpdates = options.performance.alwaysDeferChunkUpdates;
        this.useBlockFaceCulling = options.performance.useBlockFaceCulling;

        this.state.reset();
    }

    public void setVisibilityData(int sectionIdx, ChunkOcclusionData data) {
        long bits = 0;

        // The underlying data is already formatted to what we need, so we can just grab the long representation and work with that
        if (data != null) {
            BitSet bitSet = data.visibility;
            if (!bitSet.isEmpty()) {
                bits = bitSet.toLongArray()[0];
            }
        }

        long pack = 0;
        for (int fromIdx = 0; fromIdx < DirectionUtil.COUNT; fromIdx++) {
            byte toBits = (byte) (bits & ((1<<6)-1));
            bits >>= DirectionUtil.COUNT;

            pack |= Byte.toUnsignedLong(toBits)<<((fromIdx+2)<<3);
        }
        this.state.sectionTraversalData[sectionIdx] = pack;
    }

    private byte getVisibilityData(long data, int incomingDirection) {
        return (byte) (data>>((incomingDirection+2)<<3));
    }

    private void iterateChunks3() {
        int traversalOverride = 0;
        state.sortedSections[state.visibleChunksCount++] = state.getIndex(centerChunkX, centerChunkY, centerChunkZ);
        state.sectionTraversalData[state.sortedSections[0]] |= 0xFFFFL;

        while (this.state.visibleChunksQueue != this.state.visibleChunksCount) {
            int sectionIdx = this.state.sortedSections[this.state.visibleChunksQueue++];

            long sectionData = this.state.sectionTraversalData[sectionIdx];
            short traversalData = (short) (sectionData | traversalOverride);
            traversalData &= ((traversalData >> 8) & 0xFF) | 0xFF00; // Apply inbound chunk filter to prevent backwards traversal

            this.state.sectionTraversalData[sectionIdx] = sectionData&(~(0xFFFFL)); // Reset the traversalData

            BitArray.set(this.state.visible, sectionIdx);
            RenderSection section = state.sections[sectionIdx];
            if (section == null) {
                continue;
            }
            this.addSectionToLists(sectionIdx, section);
            if (raycast(section.getChunkX(), section.getChunkY(), section.getChunkZ(), centerChunkX, centerChunkY, centerChunkZ)) {
                continue;
            }

            for (int outgoingDir = 0; outgoingDir < DirectionUtil.COUNT; outgoingDir++) {
                if ((traversalData & (1 << outgoingDir)) == 0) {
                    continue;
                }

                int neighborSectionIdx = this.state.getIndex(section.getChunkX() + DirectionUtil.getOffsetX(outgoingDir), section.getChunkY()+DirectionUtil.getOffsetY(outgoingDir), section.getChunkZ() + DirectionUtil.getOffsetZ(outgoingDir));
                RenderSection neighborSection = this.state.sections[neighborSectionIdx];
                if (neighborSection == null) {
                    continue;
                }

                if (isCulledByFrustum(neighborSection.getChunkX(),neighborSection.getChunkY(),neighborSection.getChunkZ())) {
                    continue;
                }

                long neighborData = this.state.sectionTraversalData[neighborSectionIdx];
                short neighborTraversalData = (short) neighborData;
                if (neighborTraversalData == 0) {
                    this.state.sortedSections[this.state.visibleChunksCount++] = neighborSectionIdx;
                    neighborTraversalData |= (1 << 15) | (traversalData & 0xFF00);
                }

                int inboundDir = DirectionUtil.getOpposite(outgoingDir);
                neighborTraversalData |= this.getVisibilityData(neighborData, inboundDir);
                neighborTraversalData &= ~(1 << (8 + inboundDir)); // Un mark incoming direction
                this.state.sectionTraversalData[neighborSectionIdx] = (neighborData&(~0xFFFFL))|Short.toUnsignedLong(neighborTraversalData);
            }
        }
        int x = 0;
        x-=1;
    }

    private void addSectionToLists(int sectionId, RenderSection section) {
        if (section.getPendingUpdate() != null) {
            var queue = this.rebuildQueues.get(section.getPendingUpdate());

            if (queue.size() < 32) {
                queue.enqueue(section);
            }
        }

        if (section.hasFlag(ChunkDataFlags.HAS_BLOCK_GEOMETRY)) {
            this.chunkRenderList.add(section, this.getVisibleFaces(section));
        }

        if (section.hasFlag(ChunkDataFlags.HAS_ANIMATED_SPRITES)) {
            this.tickableChunks.add(sectionId);
        }

        if (section.hasFlag(ChunkDataFlags.HAS_BLOCK_ENTITIES)) {
            this.entityChunks.add(sectionId);
        }
    }

    private int getVisibleFaces(RenderSection section) {
        if (this.useBlockFaceCulling) {
            var bounds = section.getBounds();

            int faces = ModelQuadFacing.BIT_UNASSIGNED;

            if (this.cameraY > bounds.y1) {
                faces |= ModelQuadFacing.BIT_UP;
            }

            if (this.cameraY < bounds.y2) {
                faces |= ModelQuadFacing.BIT_DOWN;
            }

            if (this.cameraX > bounds.x1) {
                faces |= ModelQuadFacing.BIT_EAST;
            }

            if (this.cameraX < bounds.x2) {
                faces |= ModelQuadFacing.BIT_WEST;
            }

            if (this.cameraZ > bounds.z1) {
                faces |= ModelQuadFacing.BIT_SOUTH;
            }

            if (this.cameraZ < bounds.z2) {
                faces |= ModelQuadFacing.BIT_NORTH;
            }

            return faces;
        } else {
            return ModelQuadFacing.BIT_ALL;
        }
    }

    private void resetLists() {
        for (var queue : this.rebuildQueues.values()) {
            queue.clear();
        }

        this.entityChunks.clear();
        this.chunkRenderList.clear();
        this.tickableChunks.clear();
    }

    public Iterator<BlockEntity> getVisibleBlockEntities() {
        return this.entityChunks.intStream()
                .mapToObj(id -> this.state.sections[id])
                .flatMap(section -> section.getData()
                        .getBlockEntities()
                        .stream())
                .iterator();
    }

    public void renderLayer(ChunkRenderMatrices matrices, BlockRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrices, commandList, this.chunkRenderList, pass, new ChunkCameraContext(x, y, z));

        commandList.flush();
    }

    public void tickVisibleRenders() {
        var iterator = this.tickableChunks.iterator();
        var sections = this.state.sections;

        while (iterator.hasNext()) {
            var section = sections[iterator.nextInt()];
            section.tick();
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        return BitArray.get(this.state.visible, this.state.getIndex(x, y, z));
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
        this.needsUpdate |= this.processBuiltChunks(this.builder.createAsyncResultDrain());

        if (!blockingFutures.isEmpty()) {
            this.needsUpdate = this.processBuiltChunks(new WorkStealingFutureDrain<>(blockingFutures, this.builder::stealTask));
        }

        this.regions.cleanup();
    }

    private boolean processBuiltChunks(Iterator<ChunkBuildResult> it) {
        var results = collectBuiltChunks(it);

        this.regions.uploadMeshes(RenderDevice.INSTANCE.createCommandList(), results);

        for (var result : results) {
            this.updateSectionData(result);
            result.delete();
        }

        return !results.isEmpty();
    }

    private static ArrayList<ChunkBuildResult> collectBuiltChunks(Iterator<ChunkBuildResult> it) {
        var results = new ArrayList<ChunkBuildResult>();

        while (it.hasNext()) {
            var result = it.next();
            var section = result.section;

            if (section.isDisposed() || result.timestamp < section.getLastRebuildTime()) {
                result.delete();
                continue;
            }

            results.add(result);
        }

        return results;
    }

    private void updateSectionData(ChunkBuildResult result) {
        var section = result.section;

        this.worldRenderer.onChunkRenderUpdated(section.getChunkX(), section.getChunkY(), section.getChunkZ(),
                section.getData(), result.data);

        section.setData(result.data);
        section.finishRebuild();
    }

    private void submitRebuildTasks(ChunkUpdateType updateType, LinkedList<CompletableFuture<ChunkBuildResult>> immediateFutures, ClonedChunkSectionCache sectionCache) {
        int budget = immediateFutures != null ? Integer.MAX_VALUE : this.builder.getSchedulingBudget();

        PriorityQueue<RenderSection> queue = this.rebuildQueues.get(updateType);

        var frame = this.currentFrame;

        while (budget > 0 && !queue.isEmpty()) {
            RenderSection section = queue.dequeue();

            if (section.isDisposed()) {
                continue;
            }

            // Sections can move between update queues, but they won't be removed from the queue they were
            // previously in to save CPU cycles. We just filter any changed entries here instead.
            if (section.getPendingUpdate() != updateType) {
                continue;
            }

            section.cancelRebuild();

            ChunkRenderBuildTask task = this.createRebuildTask(sectionCache, section, frame);
            CompletableFuture<?> future;

            if (immediateFutures != null) {
                CompletableFuture<ChunkBuildResult> immediateFuture = this.builder.schedule(task);
                immediateFutures.add(immediateFuture);

                future = immediateFuture;
            } else {
                future = this.builder.scheduleDeferred(task);
            }

            section.setRebuildFuture(future, frame);

            budget--;
        }
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
            anythingUploaded |= this.processBuiltChunks(this.builder.createAsyncResultDrain());

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

    public ChunkRenderBuildTask createRebuildTask(ClonedChunkSectionCache sectionCache, RenderSection render, int frame) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, render.getChunkPos(), sectionCache);

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
        return this.state.sectionCount;
    }

    public int getVisibleChunkCount() {
        return this.chunkRenderList.getCount();
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        RenderSection section = this.state.sections[this.state.getIndex(x, y, z)];

        if (section != null && section.isBuilt()) {
            if (!this.alwaysDeferChunkUpdates && important) {
                section.markForUpdate(ChunkUpdateType.IMPORTANT_REBUILD);
            } else {
                section.markForUpdate(ChunkUpdateType.REBUILD);
            }

            this.needsUpdate = true;
        }
    }

    public void onChunkRenderUpdates(int x, int y, int z, ChunkRenderData data) {
        ChunkOcclusionData occlusionData = data.getOcclusionData();
        setVisibilityData(this.state.getIndex(x, y, z), occlusionData);
    }

    private void initSearch(Camera camera, Frustum frustum, int frame, boolean spectator) {
        this.currentFrame = frame;
        this.frustum = frustum;
        this.useOcclusionCulling = MinecraftClient.getInstance().chunkCullingEnabled;

        state.visibleChunksQueue = 0;
        state.visibleChunksCount = 0;

        BlockPos origin = camera.getBlockPos();

        final int chunkX = origin.getX() >> 4;
        final int chunkY = origin.getY() >> 4;
        final int chunkZ = origin.getZ() >> 4;

        this.centerChunkX = chunkX;
        this.centerChunkY = chunkY;
        this.centerChunkZ = chunkZ;

        int rootRenderId = this.state.getIndex(chunkX, chunkY, chunkZ);
        var rootRender = this.state.sections[rootRenderId];

        if (rootRender != null) {
            if (spectator && this.world.getBlockState(origin).isOpaqueFullCube(this.world, origin)) {
                this.useOcclusionCulling = false;
            }

            state.sectionTraversalData[rootRenderId] |= 0xFFFFL;
            state.sortedSections[state.visibleChunksCount++] = rootRenderId;
            BitArray.set(state.visible, rootRenderId);
        } else {
            int chunkTop = MathHelper.clamp(origin.getY() >> 4, this.world.getBottomSectionCoord(), this.world.getTopSectionCoord() - 1);

            IntArrayList sorted = new IntArrayList();

            for (int x2 = -this.renderDistance; x2 <= this.renderDistance; ++x2) {
                for (int z2 = -this.renderDistance; z2 <= this.renderDistance; ++z2) {
                    var sectionId = this.state.getIndex(chunkX + x2, chunkTop, chunkZ + z2);

                    if (this.state.sections[sectionId] == null || this.isCulledByFrustum(chunkX + x2, chunkTop, chunkZ + z2)) {
                        continue;
                    }

                    sorted.add(sectionId);
                }
            }

            sorted.sort((aId, bId) -> {
                var a = this.state.sections[aId];
                var b = this.state.sections[bId];

                int ax = this.centerChunkX - a.getOriginX();
                int az = this.centerChunkZ - a.getOriginZ();

                int bx = this.centerChunkX - b.getOriginX();
                int bz = this.centerChunkZ - b.getOriginZ();

                int ad = (ax * ax) + (az * az);
                int bd = (bx * bx) + (bz * bz);

                return Integer.compare(bd, ad);
            });

            IntIterator it = sorted.iterator();
            while (it.hasNext()) {
                int id = it.nextInt();
                state.sectionTraversalData[id] |= 0xFFFFL;
                state.sortedSections[state.visibleChunksCount++] = id;
                BitArray.set(state.visible, id);
            }
        }
    }

    private boolean raycast(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1)  {
        if (y1 <= this.bottomSectionCoord || y1 >= this.topSectionCoord) {
            return false;
        }

        final int deltaX = x1 - x0;
        final int deltaY = y1 - y0;
        final int deltaZ = z1 - z0;

        final int lenX = Math.abs(deltaX);
        final int lenY = Math.abs(deltaY);
        final int lenZ = Math.abs(deltaZ);

        final int longest = Math.max(lenX, Math.max(lenY, lenZ));

        final int signX = Integer.compare(deltaX, 0);
        final int signY = Integer.compare(deltaY, 0);
        final int signZ = Integer.compare(deltaZ, 0);

        // Divide by 2
        int errX = longest >> 1;
        int errY = longest >> 1;
        int errZ = longest >> 1;

        int x = x0;
        int y = y0;
        int z = z0;

        int valid = 0;

        for (int step = 0; step < longest; step++) {
            errX -= lenX;
            errY -= lenY;
            errZ -= lenZ;

            if (errX < 0) {
                errX += longest;
                x += signX;
            }

            if (errY < 0) {
                errY += longest;
                y += signY;
            }

            if (errZ < 0) {
                errZ += longest;
                z += signZ;
            }

            if (BitArray.get(this.state.visible, this.state.getIndex(x, y, z))) {
                valid++;
            } else {
                switch (this.frustumCheck(x, y, z)) {
                    case OUTSIDE:
                        return false;
                    case INTERSECT:
                        break;
                    case INSIDE:
                        return true;
                }
            }

            if (valid >= 5) {
                break;
            }
        }

        return false;
    }


    public Collection<String> getDebugStrings() {
        int count = 0;

        long deviceUsed = 0;
        long deviceAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            deviceUsed += region.getDeviceUsedMemory();
            deviceAllocated += region.getDeviceAllocatedMemory();

            count++;
        }

        List<String> list = new ArrayList<>();
        list.add(String.format("Chunk arena allocator: %s", SodiumClientMod.options().advanced.arenaMemoryAllocator.name()));
        list.add(String.format("Device buffer objects: %d", count));
        list.add(String.format("Device memory: %d/%d MiB", MathUtil.toMib(deviceUsed), MathUtil.toMib(deviceAllocated)));
        list.add(String.format("Staging buffer: %s", this.regions.getStagingBuffer().toString()));

        return list;
    }

    private boolean isCulledByFrustum(int chunkX, int chunkY, int chunkZ) {
        return this.frustumCheck(chunkX, chunkY, chunkZ) == OUTSIDE;
    }
    private int regionFrustumCheck(int x, int y, int z) {
        int id = state.getRIndex(x,y,z);
        byte shift = (byte) ((id&3)<<1);
        byte cache = state.regionFrustumCache[id>>2];
        int res = (cache>>shift)&3;
        if (res!=0) {
            return res;
        }

        float fx = (x << 7);
        float fy = (y << 6);
        float fz = (z << 7);
        int frustumResult = this.frustum.testBox(fx, fy, fz, fx + 16.0f*8, fy + 16.0f*4, fz + 16.0f*8);
        state.regionFrustumCache[id>>2] = (byte) (cache|(frustumResult<<shift));
        return frustumResult;
    }
    private int frustumCheck(int chunkX, int chunkY, int chunkZ) {
        int rfc = regionFrustumCheck(chunkX>>3, chunkY>>2, chunkZ>>3);
        if (rfc != INTERSECT) {
            return rfc;
        }
        int id = state.getIndex(chunkX, chunkY, chunkZ);
        byte shift = (byte) ((id&3)<<1);
        byte cache = state.frustumCache[id>>2];
        int res = (cache>>shift)&3;
        if (res!=0) {
            return res;
        }

        float x = (chunkX << 4);
        float y = (chunkY << 4);
        float z = (chunkZ << 4);
        int frustumResult = this.frustum.testBox(x, y, z, x + 16.0f, y + 16.0f, z + 16.0f);
        state.frustumCache[id>>2] = (byte) (cache|(frustumResult<<shift));
        return frustumResult;
    }

    public void loadChunk(int x, int z) {
        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.loadSection(x, y, z);
        }

        this.needsUpdate = true;
    }

    private void loadSection(int x, int y, int z) {
        var id = this.state.getIndex(x, y, z);

        if (this.state.sections[id] != null) {
            throw new IllegalStateException("Section is already loaded [x=%s, y=%s, z=%s]".formatted(x, y, z));
        }

        RenderSection render = new RenderSection(x, y, z);

        this.state.sections[id] = render;
        this.state.sectionTraversalData[id] = 0;

        Chunk chunk = this.world.getChunk(x, z);
        ChunkSection section = chunk.getSectionArray()[this.world.sectionCoordToIndex(y)];

        if (section.isEmpty()) {
            render.setData(ChunkRenderData.EMPTY);
        } else {
            render.markForUpdate(ChunkUpdateType.INITIAL_BUILD);
        }

        this.state.sectionCount++;
    }


    public void unloadChunk(int x, int z) {
        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.unloadSection(x, y, z);
        }

        this.needsUpdate = true;
    }

    private void unloadSection(int x, int y, int z) {
        var id = this.state.getIndex(x, y, z);
        var section = this.state.sections[id];

        if (section == null) {
            throw new IllegalStateException("Section is not loaded " + ChunkSectionPos.from(x, y, z));
        }

        section.cancelRebuild();
        section.dispose();

        var region = this.regions.getRegion(section.getRegionId());

        if (region != null) {
            region.deleteChunk(section.getLocalId());
        }

        this.state.sections[id] = null;
        this.state.sectionCount--;
    }
}
