package net.caffeinemc.sodium.render.chunk.draw;

import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.buffer.arena.sparse.SparseBufferArena;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.region.RenderRegionManager;
import net.caffeinemc.sodium.render.chunk.state.ChunkPassModel;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderBounds;
import net.caffeinemc.sodium.render.terrain.quad.properties.ChunkMeshFace;
import net.caffeinemc.sodium.util.NativeBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkSectionPos;
import org.lwjgl.system.MemoryUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class GeneratingMDICommandSet {
    private final ChunkRenderPassManager renderPassManager;
    private final RenderRegionManager regionManager;
    private final NativeBuffer blob = new NativeBuffer(100000000);
    public GeneratingMDICommandSet(RenderRegionManager regionManager, ChunkRenderPassManager renderPassManager) {
        this.regionManager = regionManager;
        this.renderPassManager = renderPassManager;
    }

    //TODO: JUST HAVE A GLOBAL MEGA NATIVEBUFFER (idk like a few mb 50?) and just write to that, dont need to do anything else, and locality will be through the roof
    //Will need to reorder to be by region, can do this in the collector thin in SodiumWorldRenderer
    public static final class RegionData {
        public RenderRegion region;
        //TODO: this will have to be an abstract class or something, cause data written here will differ on backend (MDI vs MDBV)
        public long[] commandBuffers;//Command buffers for each render layer;
        public int[] commandIndexes;//Command count
        public int instanceIndex;
        public long instanceBuffer;
        public long instanceBufferLocationCopy;
    }

    //TODO: dont hard code regionSize
    RegionData[] renderData = new RegionData[500];//TODO: sort the outputted regionLists for camera distance
    int regionCount = 0;

    //TODO: pass in list of list which is list of sortedRegion for each pass its in, is much faster
    // and should be O(6) to do or something
    public void update(List<RenderSection>[] sortedSectionsAll,
                       ChunkCameraContext camera,
                       int frameId) {
        //MinecraftClient.getInstance().getProfiler().push("reset");
        reset();
        //MinecraftClient.getInstance().getProfiler().pop();

        boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;

        ChunkRenderPass[] passes = renderPassManager.getAllRenderPasses();
        for (int passId = 0; passId < passes.length; passId++) {
            boolean reverse = passes[passId].isTranslucent();
            List<RenderSection> sortedSections = sortedSectionsAll[passId];
            int sectionCount = sortedSections.size();
            if (sectionCount == 0)
                continue;
            MinecraftClient.getInstance().getProfiler().push("list_"+passId);
            for (int sec = 0; sec < sectionCount; sec++) {
                RenderSection section = sortedSections.get(reverse?(sectionCount - sec - 1):sec);
                //MinecraftClient.getInstance().getProfiler().push("inner");
                ChunkPassModel model = section.getData().models[passId];
                //if (model == null) {
                //    continue;
                //}
                //MinecraftClient.getInstance().getProfiler().pop();
                int visibility = model.getVisibilityBits();
                if (useBlockFaceCulling) {
                    visibility &= calculateCameraVisibilityBits(section.getData().bounds, camera);
                }
                //if (visibility == 0) {
                //    continue;
                //}

                RegionData data;
                RenderRegion region = section.getRegion();
                if (region.lastFrameId != frameId) {
                    //MinecraftClient.getInstance().getProfiler().push("new_data");
                    data = renderData[regionCount] = createDataHold(region, useBlockFaceCulling, regionCount);
                    //MinecraftClient.getInstance().getProfiler().pop();
                    region.renderDataIndex = regionCount++;
                    region.lastFrameId = frameId;
                    data.region = region;
                } else {
                    data = renderData[region.renderDataIndex];
                }

                if (section.lastFrameId != frameId) {
                    //TODO: need to write instance camera data to
                    // data.instanceBuffer, maybe abstract to method that can be overriden
                    //TODO: do this alligned and set the instanceCopyStuff, so it can just be memcopied
                    writeInstanceData(data, data.instanceIndex, section, camera);
                    section.instanceIndex = data.instanceIndex++;
                    section.lastFrameId = frameId;
                }
                long[] modelPartSegments = model.getModelPartSegments();
                //TODO: do the reverse order shiz here or something
                int baseVertex = BufferSegment.getOffset(section.getUploadedGeometrySegment());
                //if (true)
                //    continue;
                if (reverse) {
                    while (visibility != 0) {
                        int dirIdx = (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(visibility);
                        writeCommandData(data, section, passId, baseVertex, modelPartSegments[dirIdx], data.commandIndexes[passId]++, section.instanceIndex);
                        visibility ^= 1 << dirIdx;
                    }
                } else {
                    while (visibility != 0) {
                        int dirIdx = Integer.numberOfTrailingZeros(visibility);
                        writeCommandData(data, section, passId, baseVertex, modelPartSegments[dirIdx], data.commandIndexes[passId]++, section.instanceIndex);
                        visibility &= visibility - 1;
                    }
                }
            }
            MinecraftClient.getInstance().getProfiler().pop();
        }
    }

    protected static int calculateCameraVisibilityBits(ChunkRenderBounds bounds, ChunkCameraContext camera) {
        int bits = ChunkMeshFace.UNASSIGNED_BITS;

        if (camera.posY > bounds.y1) {
            bits |= ChunkMeshFace.UP_BITS;
        }

        if (camera.posY < bounds.y2) {
            bits |= ChunkMeshFace.DOWN_BITS;
        }

        if (camera.posX > bounds.x1) {
            bits |= ChunkMeshFace.EAST_BITS;
        }

        if (camera.posX < bounds.x2) {
            bits |= ChunkMeshFace.WEST_BITS;
        }

        if (camera.posZ > bounds.z1) {
            bits |= ChunkMeshFace.SOUTH_BITS;
        }

        if (camera.posZ < bounds.z2) {
            bits |= ChunkMeshFace.NORTH_BITS;
        }

        return bits;
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    private void writeInstanceData(RegionData data, int index, RenderSection section, ChunkCameraContext camera) {
        long addr = data.instanceBuffer + (long) index * AbstractMdChunkRenderer.TRANSFORM_STRUCT_STRIDE;

        float x = getCameraTranslation(
                ChunkSectionPos.getBlockCoord(section.getChunkX()),
                camera.blockX,
                camera.deltaX
        );
        float y = getCameraTranslation(
                ChunkSectionPos.getBlockCoord(section.getChunkY()),
                camera.blockY,
                camera.deltaY
        );
        float z = getCameraTranslation(
                ChunkSectionPos.getBlockCoord(section.getChunkZ()),
                camera.blockZ,
                camera.deltaZ
        );

        MemoryUtil.memPutFloat(addr, x);
        MemoryUtil.memPutFloat(addr+4, y);
        MemoryUtil.memPutFloat(addr+8, z);
    }

    private void writeCommandData(RegionData data, RenderSection section, int passId, int baseVertex, long modelPartSegment, int commandIndex, int instanceDataIndex) {
        long ptr = data.commandBuffers[passId] + (long) commandIndex * MdiChunkRenderer.COMMAND_STRUCT_STRIDE;
        MemoryUtil.memPutInt(ptr + 0, 6 * (BufferSegment.getLength(modelPartSegment) >> 2)); // go from vertex count -> index count
        MemoryUtil.memPutInt(ptr + 4, 1);
        MemoryUtil.memPutInt(ptr + 8, 0);
        MemoryUtil.memPutInt(ptr + 12, baseVertex + BufferSegment.getOffset(modelPartSegment)); // baseVertex
        MemoryUtil.memPutInt(ptr + 16, instanceDataIndex); // baseInstance
    }


    //TODO: make cache of NativeBuffers and find/fill optimal RegionData with them
    private RegionData createDataHold(RenderRegion region, boolean useBlockFaceCulling, int id) {
        //NOTE: only have to fill in the slots where it could possibly have visibility with face culling, so take into account when finding buffers to use
        int sectionCount = region.getSections().size();
        //TODO: if useBlockFaceCulling is true, check the max possible faces it can be, then calculate using that
        int visibilityCount = 7;//7 cause 6 cube + other
        //TODO: have something too keep track of rough counts in each region of how many chunks there are with what passes
        // Assume worse case

        //TODO: have a cache of RenderData objects
        RegionData data = createNew();
        {
            long estimatedInstanceSize = RenderRegion.REGION_SIZE * AbstractMdChunkRenderer.TRANSFORM_STRUCT_STRIDE;
            data.instanceIndex = 0;
            //TODO: native buffer cache
            data.instanceBuffer = blob.getLength()-estimatedInstanceSize*(id+1) + blob.getAddress();
        }

        for (int i = 0; i < renderPassManager.getRenderPassCount(); i++) {
            data.commandIndexes[i] = 0;

            long estimatedCommandSize = 7L * RenderRegion.REGION_SIZE *
                    MdiChunkRenderer.COMMAND_STRUCT_STRIDE *
                    renderPassManager.getRenderPassCount();


            //TODO: native buffer cache
            data.commandBuffers[i] = estimatedCommandSize * id +
                    7L * RenderRegion.REGION_SIZE *
                    MdiChunkRenderer.COMMAND_STRUCT_STRIDE *
                            i + blob.getAddress();
        }

        return data;
    }

    private RegionData createNew() {
        RegionData data = new RegionData();
        data.commandBuffers = new long[renderPassManager.getRenderPassCount()];
        data.commandIndexes = new int[renderPassManager.getRenderPassCount()];
        return data;
    }

    private void reset() {
        regionCount = 0;
    }

}
