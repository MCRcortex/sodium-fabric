package me.cortex.cullmister;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.RegionPos;
import net.caffeinemc.sodium.render.chunk.compile.ChunkBuilder;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildTask;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.caffeinemc.sodium.world.slice.WorldSliceData;
import net.caffeinemc.sodium.world.slice.cloned.ClonedChunkSectionCache;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;

//Handels all the regions and dispatches all subchunks in those regions
public class RegionManager {
    Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    public ChunkBuildEngine builder;

    public RegionManager(ClientWorld world) {
        builder = new ChunkBuildEngine(world);
    }

    public void tick(int frame) {
        synchronized (builder.outflowWorkQueue) {
            while (!builder.outflowWorkQueue.isEmpty()) {
                TerrainBuildResult result = builder.outflowWorkQueue.dequeue();
                getRegion(result.pos()).updateMeshes(result);
                result.delete();
            }
        }
    }


    public Region getRegion(ChunkSectionPos pos) {
        return getRegion(RegionPos.from(pos));
    }

    public Region getRegion(RegionPos pos) {
        return regions.computeIfAbsent(pos.Long(), p->new Region(pos));
    }

    public void enqueueRemoval(ChunkSectionPos pos) {
        if (!regions.containsKey(RegionPos.from(pos).Long()))
            throw new IllegalStateException();
        //TODO: if region chunk count is zero, remove the region
        Region r = getRegion(pos);
        r.remove(pos);
        if (r.sectionCount == 0) {
            System.out.println("Region deletion: "+ RegionPos.from(pos));
            regions.remove(RegionPos.from(pos).Long());
        }
    }

    public void enqueueRebuild(ChunkSectionPos pos, int frame) {
        builder.requestRebuild(pos, frame);
    }

}
