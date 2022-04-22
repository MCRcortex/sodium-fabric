package me.cortex.cullmister;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.RegionPos;
import net.caffeinemc.sodium.render.chunk.compile.ChunkBuilder;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildTask;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.caffeinemc.sodium.world.ChunkTracker;
import net.caffeinemc.sodium.world.slice.WorldSliceData;
import net.caffeinemc.sodium.world.slice.cloned.ClonedChunkSectionCache;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;

//Handels all the regions and dispatches all subchunks in those regions
public class RegionManager {
    Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    public ChunkBuildEngine builder;


    ObjectOpenHashSet<ChunkSectionPos> chunkSectionsNonImportant = new ObjectOpenHashSet<>();
    ObjectOpenHashSet<ChunkSectionPos> chunkSectionsImportant = new ObjectOpenHashSet<>();
    PriorityQueue<TerrainBuildResult> workResultsLocal = new ObjectArrayFIFOQueue<>();
    public void tick(int frame) {
        if (builder == null) {
            return;
        }
        chunkSectionsImportant.forEach(pos->builder.requestRebuild(pos, frame, true));
        chunkSectionsNonImportant.forEach(pos->builder.requestRebuild(pos, frame, false));
        chunkSectionsImportant.clear();
        chunkSectionsNonImportant.clear();

        synchronized (builder.outflowWorkQueue) {
            while (!builder.outflowWorkQueue.isEmpty())
                workResultsLocal.enqueue(builder.outflowWorkQueue.dequeue());
        }

        while (!workResultsLocal.isEmpty()) {
            TerrainBuildResult result = workResultsLocal.dequeue();
            //TODO: FIX, THIS IS A HACK
            if (result.geometry().vertices() == null) {
                result.delete();
                continue;
            }
            getRegion(result.pos()).updateMeshes(result);
            result.delete();
        }
    }

    public void setWorld(ClientWorld world) {
        if (world == null)
            return;
        if (builder != null) {
            builder.delete();
        }
        builder = new ChunkBuildEngine(world);
    }

    public void reset() {
        for (Region r : regions.values()) {
            r.delete();
        }
        regions.clear();
        if (builder != null) {
            builder.clear();
        }
    }

    public Region getRegion(ChunkSectionPos pos) {
        return getRegion(RegionPos.from(pos));
    }

    public Region getRegion(RegionPos pos) {
        Region r = regions.computeIfAbsent(pos.Long(), p->new Region(pos));
        if (!r.pos.equals(pos))
            throw new IllegalStateException();
        return r;
    }

    public void enqueueRemoval(ChunkSectionPos pos) {
        if (false)
            return;
        if (!regions.containsKey(RegionPos.from(pos).Long())) {
            return;
            //throw new IllegalStateException();
        }

        Region r = getRegion(pos);
        r.remove(pos);
        if (r.sectionCount == 0) {
            //System.out.println("Region deletion: "+ RegionPos.from(pos));
            regions.remove(RegionPos.from(pos).Long());
            r.delete();
        }
    }

    public void enqueueRebuild(ChunkSectionPos pos, boolean important) {
        (important?chunkSectionsImportant:chunkSectionsNonImportant).add(pos);
    }

}
