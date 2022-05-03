package me.cortex.cullmister;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.region.RegionPos;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.chunk.compile.ChunkBuilder;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildTask;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.caffeinemc.sodium.world.ChunkStatus;
import net.caffeinemc.sodium.world.ChunkTracker;
import net.caffeinemc.sodium.world.slice.WorldSliceData;
import net.caffeinemc.sodium.world.slice.cloned.ClonedChunkSectionCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.stream.Collectors;

//Handels all the regions and dispatches all subchunks in those regions
public class RegionManager {
    Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    public ChunkBuildEngine builder;


    ObjectLinkedOpenHashSet<ChunkSectionPos> chunkSectionsNonImportant = new ObjectLinkedOpenHashSet<>();
    ObjectLinkedOpenHashSet<ChunkSectionPos> chunkSectionsImportant = new ObjectLinkedOpenHashSet<>();
    PriorityQueue<TerrainBuildResult> workResultsLocal = new ObjectArrayFIFOQueue<>();
    public void tick(int frame) {
        if (builder == null) {
            return;
        }
        MinecraftClient.getInstance().getProfiler().push("request rebuilds");

        for (ChunkSectionPos section : chunkSectionsImportant.stream().toList()) {
            if (builder.inflowWorkQueue.size() > 5000)
                break;

            if (!SodiumWorldRenderer.instance().getChunkTracker().hasMergedFlags(section.getX(), section.getZ(), ChunkStatus.FLAG_ALL))
                continue;
            builder.requestRebuild(section, frame, true);
            chunkSectionsImportant.remove(section);
        }

        for (ChunkSectionPos section : chunkSectionsNonImportant.stream().toList()) {
            if (builder.inflowWorkQueue.size() > 5000)
                break;

            if ((Math.hypot(section.getX()-((SodiumWorldRenderer.instance().camBPos.getX())>>4), section.getZ()-((SodiumWorldRenderer.instance().camBPos.getZ())>>4))>(MinecraftClient.getInstance().options.getViewDistance()+0.2))) {
                chunkSectionsNonImportant.remove(section);
                continue;
            }

            if (!SodiumWorldRenderer.instance().getChunkTracker().hasMergedFlags(section.getX(), section.getZ(), ChunkStatus.FLAG_ALL))
                continue;
            builder.requestRebuild(section, frame, false);
            chunkSectionsNonImportant.remove(section);
        }

        MinecraftClient.getInstance().getProfiler().swap("copy build results");

        synchronized (builder.outflowWorkQueue) {
            while (!builder.outflowWorkQueue.isEmpty())
                workResultsLocal.enqueue(builder.outflowWorkQueue.dequeue());
        }

        MinecraftClient.getInstance().getProfiler().swap("mesh update");
        int budget = 50;
        while (!workResultsLocal.isEmpty() && budget!=0) {
            MinecraftClient.getInstance().getProfiler().push("dequeu");
            TerrainBuildResult result = workResultsLocal.dequeue();
            //TODO: FIX, THIS IS A HACK
            if (result.geometry().vertices() == null) {
                result.delete();
                MinecraftClient.getInstance().getProfiler().pop();
                continue;
            }
            MinecraftClient.getInstance().getProfiler().swap("mesh");
            getRegion(result.pos()).updateMeshes(result);
            MinecraftClient.getInstance().getProfiler().swap("delete");
            result.delete();
            //result.geometry().vertices().buffer().free();
            budget--;
            MinecraftClient.getInstance().getProfiler().pop();
        }
        MinecraftClient.getInstance().getProfiler().pop();
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
        chunkSectionsImportant.clear();
        chunkSectionsNonImportant.clear();
        workResultsLocal.clear();
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
        chunkSectionsNonImportant.remove(pos);
        chunkSectionsImportant.remove(pos);
        //TODO: need to remove from workResultsLocal too
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
