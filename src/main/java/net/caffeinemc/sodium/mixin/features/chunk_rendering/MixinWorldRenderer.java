package net.caffeinemc.sodium.mixin.features.chunk_rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.cortex.cullmister.CoreRenderer;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.FrustumAdapter;
import net.caffeinemc.sodium.interop.vanilla.mixin.WorldRendererHolder;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.world.ChunkStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer implements WorldRendererHolder {
    @Shadow
    @Final
    private BufferBuilderStorage bufferBuilders;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Shadow
    private Frustum frustum;
    private SodiumWorldRenderer renderer;

    @Unique
    private int frame;

    @Override
    public SodiumWorldRenderer getSodiumWorldRenderer() {
        return this.renderer;
    }

    @Redirect(method = "reload()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getViewDistance()I", ordinal = 1))
    private int nullifyBuiltChunkStorage(GameOptions options) {
        // Do not allow any resources to be allocated
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(MinecraftClient client, BufferBuilderStorage bufferBuilders, CallbackInfo ci) {
        this.renderer = new SodiumWorldRenderer(client);
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void onWorldChanged(ClientWorld world, CallbackInfo ci) {
        this.renderer.setWorld(world);
    }

    /**
     * @reason Redirect to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int getCompletedChunkCount() {
        return this.renderer.getVisibleChunkCount();
    }

    /**
     * @reason Redirect the check to our renderer
     * @author JellySquid
     */
    @Overwrite
    public boolean isTerrainRenderComplete() {
        return this.renderer.isTerrainRenderComplete();
    }

    @Inject(method = "scheduleTerrainUpdate", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void renderLayer(RenderLayer renderLayer, MatrixStack matrices, double x, double y, double z, Matrix4f matrix) {
        if (renderLayer == RenderLayer.getCutout()) {
            CoreRenderer.INSTANCE.debugRender(ChunkRenderMatrices.from(matrices), new Vector3f((float)x,(float)y,(float) z), FrustumAdapter.adapt(frustum));
        }

        this.renderer.drawChunkLayer(renderLayer, matrices);

        // VANILLA BUG: Binding a RenderLayer for chunk rendering will result in setShaderColor being called,
        // which is accidentally depended on by tile entity rendering. Since we don't bind a render layer, we need
        // to set this back to the expected state, or colors will be corrupted.
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator) {
        this.renderer.updateChunks(camera, FrustumAdapter.adapt(frustum), this.frame++, spectator);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void scheduleBlockRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void scheduleBlockRenders(int x, int y, int z) {
        this.renderer.scheduleRebuildForChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void scheduleSectionRender(BlockPos pos, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void scheduleChunkRender(int x, int y, int z, boolean important) {
        this.renderer.scheduleRebuildForChunk(x, y, z, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public boolean isRenderingReady(BlockPos pos) {
        return this.renderer.doesChunkHaveFlag(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FLAG_ALL);
    }

    @Inject(method = "reload()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        CoreRenderer.INSTANCE.reload();
        this.renderer.reload();
    }

    @Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/WorldRenderer;noCullingBlockEntities:Ljava/util/Set;", shift = At.Shift.BEFORE, ordinal = 0))
    private void onRenderTileEntities(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
        this.renderer.renderTileEntities(matrices, this.bufferBuilders, this.blockBreakingProgressions, camera, tickDelta);
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite
    public String getChunksDebugString() {
        return this.renderer.getChunksDebugString();
    }
}
