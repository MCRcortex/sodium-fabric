package net.caffeinemc.sodium.render;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.interop.vanilla.mixin.WorldRendererHolder;
import net.caffeinemc.sodium.render.chunk.*;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.draw.ComputeTranslucencySort;
import net.caffeinemc.sodium.render.chunk.occlussion.GPUOcclusionManager;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.TerrainRenderManager;
import net.caffeinemc.sodium.render.terrain.context.ImmediateTerrainRenderCache;
import net.caffeinemc.sodium.util.NativeBuffer;
import net.caffeinemc.sodium.world.ChunkTracker;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;

import java.util.Collection;
import java.util.SortedSet;

/**
 * Provides an extension to vanilla's {@link WorldRenderer}.
 */
public class SodiumWorldRenderer {
    private final MinecraftClient client;

    private ClientWorld world;
    private int renderDistance;

    //FIXME: move to ViewportedData
    private double lastCameraPitch, lastCameraYaw;

    private float lastFogDistance;

    private boolean useEntityCulling;

    private GPUOcclusionManager occlusion;
    public TerrainRenderManager terrainRenderManager;
    private ComputeTranslucencySort translucencySort;
    private ChunkRenderPassManager renderPassManager;
    private ChunkTracker chunkTracker;

    /**
     * @return The SodiumWorldRenderer based on the current dimension
     */
    public static SodiumWorldRenderer instance() {
        var instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active world");
        }

        return instance;
    }

    /**
     * @return The SodiumWorldRenderer based on the current dimension, or null if none is attached
     */
    public static SodiumWorldRenderer instanceNullable() {
        var world = MinecraftClient.getInstance().worldRenderer;

        if (world instanceof WorldRendererHolder) {
            return ((WorldRendererHolder) world).getSodiumWorldRenderer();
        }

        return null;
    }

    public SodiumWorldRenderer(MinecraftClient client) {
        this.client = client;
    }

    public void setWorld(ClientWorld world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    private void loadWorld(ClientWorld world) {
        this.world = world;
        this.chunkTracker = new ChunkTracker();

        ImmediateTerrainRenderCache.createRenderContext(this.world);

        this.initRenderer();
    }

    private void unloadWorld() {
        ImmediateTerrainRenderCache.destroyRenderContext(this.world);

        if (this.terrainRenderManager != null) {
            this.terrainRenderManager.destroy();
            this.terrainRenderManager = null;
        }

        this.chunkTracker = null;
        this.world = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.terrainRenderManager.getVisibleSectionCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.terrainRenderManager != null) {
            this.terrainRenderManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.terrainRenderManager.getBuilder().isBuildQueueEmpty();
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void updateChunks(Camera camera, Frustum frustum, @Deprecated(forRemoval = true) int frame, boolean spectator) {
        NativeBuffer.reclaim(false);

        this.useEntityCulling = SodiumClientMod.options().performance.useEntityCulling;

        if (this.client.options.getClampedViewDistance() != this.renderDistance) {
            this.reload();
        }

        Profiler profiler = this.client.getProfiler();
        profiler.push("camera_setup");

        ClientPlayerEntity player = this.client.player;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }
        var data = ViewportedData.get();

        Vec3d pos = camera.getPos();
        data.lastCameraX = data.cameraX;
        data.lastCameraY = data.cameraY;
        data.lastCameraZ = data.cameraZ;
        data.cameraX = pos.x;
        data.cameraY = pos.y;
        data.cameraZ = pos.z;
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();
        float fogDistance = RenderSystem.getShaderFogEnd();

        boolean dirty = pos.x != data.lastCameraX || pos.y != data.lastCameraY || pos.z != data.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw || fogDistance != this.lastFogDistance;

        if (dirty) {
            this.terrainRenderManager.markGraphDirty();
        }


        data.cameraRenderRegion =  RenderRegion.getRegionCoord((int)Math.floor(pos.x)>>4, (int)Math.floor(pos.y)>>4, (int)Math.floor(pos.z)>>4);
        data.cameraRenderRegionInner =  RenderRegion.getInnerRegionCoord((int)Math.floor(pos.x)>>4, (int)Math.floor(pos.y)>>4, (int)Math.floor(pos.z)>>4);
        data.frustum = frustum;

        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;
        this.lastFogDistance = fogDistance;

        profiler.swap("chunk_update");

        this.chunkTracker.update();

        this.terrainRenderManager.setFrameIndex(frame);
        this.terrainRenderManager.updateChunks();

        if (this.terrainRenderManager.isGraphDirty()) {
            profiler.swap("chunk_graph_rebuild");
            if (!MinecraftClient.getInstance().player.isSneaking()) {
                //this.terrainRenderManager.update(new ChunkCameraContext(camera), frustum, spectator);
            } else {

            }
        }

        profiler.swap("visible_chunk_tick");

        this.terrainRenderManager.tickVisibleRenders();

        profiler.pop();

        Entity.setRenderDistanceMultiplier(MathHelper.clamp((double) this.client.options.getClampedViewDistance() / 8.0D, 1.0D, 2.5D) * this.client.options.getEntityDistanceScaling().getValue());
    }

    /**
     * Performs a render pass for the given {@link RenderLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(RenderLayer renderLayer, MatrixStack matrixStack, ChunkCameraContext cameraContext) {

        ChunkRenderPass renderPass = this.renderPassManager.getRenderPassForLayer(renderLayer);
        this.terrainRenderManager.renderLayer(ChunkRenderMatrices.from(matrixStack), renderPass/*, cameraContext*/);
        //NOTE: works fine if its done before solid, so could reporject depth buffer and use that
        if (renderLayer == RenderLayer.getTranslucent() && !MinecraftClient.getInstance().player.isSneaking()) {
            var dat = ViewportedData.get();
            doOcclusion(matrixStack, cameraContext, dat.frustum);
        }
    }


    public void doOcclusion(MatrixStack stack, ChunkCameraContext cameraContext, Frustum frustum) {
        MinecraftClient.getInstance().getProfiler().push("gpu occluder");
        this.occlusion.computeOcclusionVis(terrainRenderManager, ChunkRenderMatrices.from(stack), cameraContext, frustum);
        MinecraftClient.getInstance().getProfiler().pop();
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        this.initRenderer();
    }

    private void initRenderer() {
        if (this.terrainRenderManager != null) {
            this.terrainRenderManager.destroy();
            this.terrainRenderManager = null;
        }

        this.renderDistance = this.client.options.getClampedViewDistance();

        this.renderPassManager = ChunkRenderPassManager.createDefaultMappings();

        this.terrainRenderManager = new TerrainRenderManager(SodiumClientMod.DEVICE, this, this.renderPassManager, this.world, this.renderDistance);
        this.terrainRenderManager.reloadChunks(this.chunkTracker);

        this.occlusion = new GPUOcclusionManager(SodiumClientMod.DEVICE);
        this.translucencySort = new ComputeTranslucencySort(SodiumClientMod.DEVICE);
    }

    public void renderTileEntities(MatrixStack matrices, BufferBuilderStorage bufferBuilders, Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
                                   Camera camera, float tickDelta) {
        VertexConsumerProvider.Immediate immediate = bufferBuilders.getEntityVertexConsumers();

        Vec3d cameraPos = camera.getPos();
        double x = cameraPos.getX();
        double y = cameraPos.getY();
        double z = cameraPos.getZ();

        BlockEntityRenderDispatcher blockEntityRenderer = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntity blockEntity : this.terrainRenderManager.getVisibleBlockEntities()) {
            BlockPos pos = blockEntity.getPos();

            matrices.push();
            matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

            VertexConsumerProvider consumer = immediate;
            SortedSet<BlockBreakingInfo> breakingInfos = blockBreakingProgressions.get(pos.asLong());

            if (breakingInfos != null && !breakingInfos.isEmpty()) {
                int stage = breakingInfos.last().getStage();

                if (stage >= 0) {
                    MatrixStack.Entry entry = matrices.peek();
                    VertexConsumer transformer = new OverlayVertexConsumer(bufferBuilders.getEffectVertexConsumers().getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)), entry.getPositionMatrix(), entry.getNormalMatrix());
                    consumer = (layer) -> layer.hasCrumbling() ? VertexConsumers.union(transformer, immediate.getBuffer(layer)) : immediate.getBuffer(layer);
                }
            }


            blockEntityRenderer.render(blockEntity, tickDelta, matrices, consumer);

            matrices.pop();
        }

        for (BlockEntity blockEntity : this.terrainRenderManager.getGlobalBlockEntities()) {
            BlockPos pos = blockEntity.getPos();

            matrices.push();
            matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

            blockEntityRenderer.render(blockEntity, tickDelta, matrices, immediate);

            matrices.pop();
        }
    }

    public void onChunkAdded(int x, int z) {
        if (this.chunkTracker.loadChunk(x, z)) {
            this.terrainRenderManager.onChunkAdded(x, z);
        }
    }

    public void onChunkLightAdded(int x, int z) {
        this.chunkTracker.onLightDataAdded(x, z);
    }

    public void onChunkRemoved(int x, int z) {
        if (this.chunkTracker.unloadChunk(x, z)) {
            this.terrainRenderManager.onChunkRemoved(x, z);
        }
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        if (this.client.hasOutline(entity) || entity.shouldRenderName()) {
            return true;
        }

        Box box = entity.getVisibilityBoundingBox();

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public boolean doesChunkHaveFlag(int x, int z, int status) {
        return this.chunkTracker.hasMergedFlags(x, z, status);
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Boxes outside the valid world height will never map to a rendered chunk
        // Always render these boxes or they'll be culled incorrectly!
        if (y2 < this.world.getBottomY() + 0.5D || y1 > this.world.getTopY() - 0.5D) {
            return true;
        }

        int minX = ChunkSectionPos.getSectionCoord(x1 - 0.5D);
        int minY = ChunkSectionPos.getSectionCoord(y1 - 0.5D);
        int minZ = ChunkSectionPos.getSectionCoord(z1 - 0.5D);

        int maxX = ChunkSectionPos.getSectionCoord(x2 + 0.5D);
        int maxY = ChunkSectionPos.getSectionCoord(y2 + 0.5D);
        int maxZ = ChunkSectionPos.getSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.terrainRenderManager.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getChunksDebugString() {
        // C: visible/total
        // TODO: add dirty and queued counts
        return String.format("C: %s/%s", this.terrainRenderManager.getVisibleSectionCount(), this.terrainRenderManager.getTotalSections());
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.terrainRenderManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<String> getMemoryDebugStrings() {
        return this.terrainRenderManager.getDebugStrings();
    }

    public ChunkTracker getChunkTracker() {
        return this.chunkTracker;
    }


    public GPUOcclusionManager getOccluder() {
        return occlusion;
    }

    public RenderSection getSectionInOrNull() {
        //return renderSectionManager.getSectionInOrNull();
        return null;
    }
}
