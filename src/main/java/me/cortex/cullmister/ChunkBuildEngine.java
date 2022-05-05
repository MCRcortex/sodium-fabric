package me.cortex.cullmister;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.compile.ChunkBuilder;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildTask;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.terrain.TerrainBuildContext;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.world.slice.WorldSliceData;
import net.caffeinemc.sodium.world.slice.cloned.ClonedChunkSectionCache;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class ChunkBuildEngine {
    ClientWorld world;
    ClonedChunkSectionCache sectionCache;
    TerrainVertexType vertexType;
    ChunkRenderPassManager mappings;
    boolean isRunning = true;
    final Semaphore inflowWork = new Semaphore(0);
    final ObjectLinkedOpenHashSet<TerrainBuildTask> inflowWorkQueue = new ObjectLinkedOpenHashSet<>();
    final PriorityQueue<TerrainBuildResult> outflowWorkQueue = new ObjectArrayFIFOQueue<>();
    final Long2ObjectOpenHashMap<TerrainBuildTask> buildTasks = new Long2ObjectOpenHashMap();

    List<Thread> threads = new LinkedList<>();
    public ChunkBuildEngine(ClientWorld world) {
        this.world = world;


        //vertexType = TerrainVertexFormats.COMPACT;
        vertexType = TerrainVertexFormats.OPTIMIZE;



        mappings = ChunkRenderPassManager.createDefaultMappings();
        this.sectionCache = new ClonedChunkSectionCache(world);

        for (int i = 0; i < 14; i++) {
            Thread thread = new Thread(this::worker, "Chunk Render Builder Thread #" + i);
            thread.setPriority(Math.max(0, Thread.NORM_PRIORITY - 2));
            thread.start();

            this.threads.add(thread);
        }
    }

    //TODO: NEED TO ADD A DISCARD THINK FOR filtering/checking if a task is valid,
    // do this with the request frame, and check if the build result is equal to the last build time of the chunk
    public void requestRebuild(ChunkSectionPos pos, int frame, boolean important) {
        synchronized (buildTasks) {
            TerrainBuildTask et;
            boolean inc = true;
            if ((et = buildTasks.get(pos.asLong())) != null) {
                et.canceled = true;
                if (inflowWorkQueue.remove(et)) {
                    inc = false;
                }
            }
            sectionCache.invalidate(pos.getX(), pos.getY(), pos.getZ());
            WorldSliceData data = WorldSliceData.prepare(this.world, pos, this.sectionCache);
            if (data == null) {
                if (!inc)
                    if (!inflowWork.tryAcquire())
                        throw new IllegalStateException();
                return;
            }
            TerrainBuildTask task = new TerrainBuildTask(pos, data, frame);
            synchronized (inflowWorkQueue) {
                if (important) {
                    if (!inflowWorkQueue.addAndMoveToFirst(task))
                        throw new IllegalStateException();
                } else {
                    if (!inflowWorkQueue.add(task))
                        throw new IllegalStateException();
                }
            }
            if (inc)
                inflowWork.release();
            buildTasks.put(pos.asLong(), task);
        }
    }



    private void worker() {
        TerrainBuildContext context = new TerrainBuildContext(world, vertexType, mappings);
        while (isRunning) {
            TerrainBuildTask work;
            try {
                inflowWork.acquire();
            } catch (InterruptedException e) {
                break;
            }
            if (!isRunning)
                break;
            synchronized (buildTasks) {
                synchronized (inflowWorkQueue) {
                    work = inflowWorkQueue.removeFirst();
                }
            }
            try {
                TerrainBuildResult result = work.performBuild(context, () -> work.canceled);
                if (work.canceled) {
                    if (result != null)
                        result.delete();
                    continue;
                }
                synchronized (buildTasks) {
                    synchronized (outflowWorkQueue) {
                        outflowWorkQueue.enqueue(result);
                    }
                    buildTasks.remove(work.pos.asLong());
                }
            } finally {
                context.release();
            }

        }
    }

    public void delete() {
        isRunning = false;
        inflowWork.release(100000);
    }

    public void clear() {
        synchronized (buildTasks) {
            synchronized (inflowWorkQueue) {
                inflowWork.drainPermits();
                inflowWorkQueue.clear();
            }
            for (TerrainBuildTask task : buildTasks.values()) {
                task.canceled = true;
            }
            buildTasks.clear();
            synchronized (outflowWorkQueue) {
                while (!outflowWorkQueue.isEmpty()) {
                    outflowWorkQueue.dequeue().delete();
                }
                outflowWorkQueue.clear();
            }
        }
    }
}
