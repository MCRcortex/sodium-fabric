package net.caffeinemc.sodium.render.chunk.region;

import it.unimi.dsi.fastutil.ints.*;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.SmartConstAsyncBufferArena;
import net.caffeinemc.sodium.render.buffer.streaming.SectionedStreamingBuffer;
import net.caffeinemc.sodium.render.buffer.streaming.StreamingBuffer;
import net.caffeinemc.sodium.render.chunk.*;
import net.caffeinemc.sodium.render.chunk.occlussion.SectionMeta;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.MathUtil;
import net.caffeinemc.sodium.util.collections.BitArray;
import net.minecraft.util.math.ChunkSectionPos;
import org.antlr.runtime.misc.IntArray;
import org.apache.commons.lang3.Validate;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import java.util.BitSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class RenderRegion {
    public static final int REGION_WIDTH = 16;
    public static final int REGION_HEIGHT = 16;
    public static final int REGION_LENGTH = 16;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    private static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    private static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    private static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final int regionX, regionY, regionZ;

    public final ArenaBuffer vertexBuffers;
    public final StreamingBuffer metaBuffer;
    public final AtomicInteger translucentSections = new AtomicInteger();
    private RenderDevice device;

    public float weight;//Util thing

    public final int id;
    public final long key;

    private final RenderSectionManager sectionManager;
    public RenderRegion(RenderDevice device, RenderSectionManager sectionManager, SectionedStreamingBuffer stagingBuffer, TerrainVertexType vertexType, int id, long regionKey) {
        this.vertexBuffers = new SmartConstAsyncBufferArena(device, stagingBuffer,
                REGION_SIZE * 756,
                vertexType.getBufferVertexFormat().stride());
        this.metaBuffer = new SectionedStreamingBuffer(device, 1, SectionMeta.SIZE, REGION_SIZE,
                Set.of(MappedBufferFlags.EXPLICIT_FLUSH));
        this.id = id;
        ChunkSectionPos csp = ChunkSectionPos.from(regionKey);
        regionX = csp.getSectionX();
        regionY = csp.getSectionY();
        regionZ = csp.getSectionZ();
        this.device = device;
        this.sectionManager = sectionManager;
        this.key = regionKey;
        //GL11.glFinish();
    }

    private boolean disposed = false;
    public void delete() {
        this.vertexBuffers.delete();
        for (RenderRegionInstancedRenderData data : renderData.values()) {
            data.delete();
        }
        renderData.clear();
        disposed = true;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public boolean isEmpty() {
        return this.vertexBuffers.isEmpty() && sectionCount != 0;
    }

    public long getDeviceUsedMemory() {
        return this.vertexBuffers.getDeviceUsedMemory();
    }

    public long getDeviceAllocatedMemory() {
        return this.vertexBuffers.getDeviceAllocatedMemory();
    }

    public static long getRegionCoord(int chunkX, int chunkY, int chunkZ) {
        return ChunkSectionPos.asLong(chunkX >> REGION_WIDTH_SH, chunkY >> REGION_HEIGHT_SH, chunkZ >> REGION_LENGTH_SH);
    }

    public static int getInnerRegionCoord(int chunkX, int chunkY, int chunkZ) {
        return (((chunkX & ((1<<REGION_WIDTH_SH)-1))*REGION_HEIGHT+ (chunkY & ((1<<REGION_HEIGHT_SH)-1))) * REGION_LENGTH + (chunkZ & ((1<<REGION_LENGTH_SH)-1)));
    }

    public Vector3i getMinAsBlock() {
        return new Vector3i(regionX<<(REGION_WIDTH_SH+4), regionY<<(REGION_HEIGHT_SH+4), regionZ<<(REGION_LENGTH_SH+4));
    }


    public int sectionCount = 0;
    private Int2ObjectOpenHashMap<SectionMeta> sectionMetaMap = new Int2ObjectOpenHashMap<>();
    private Int2IntOpenHashMap pos2id = new Int2IntOpenHashMap();
    private IntAVLTreeSet freeIds = new IntAVLTreeSet();

    private int newSectionId() {
        if (freeIds.isEmpty()) {
            if (sectionCount>=REGION_SIZE) {
                //TODO: print error in log/throw/raise exception
                return -1;
            }
            return sectionCount++;//+1;//Offset by 1 so that 0 is always free
        }
        int id = freeIds.firstInt();
        freeIds.remove(id);
        return id;
    }

    public synchronized SectionMeta sectionGeometryUpdated(RenderSection section) {
        //TODO: implement verification that section is still a valid unfreed meta section
        return section.getMeta();
    }

    public synchronized SectionMeta requestNewMetaSection(RenderSection section) {
        if (pos2id.containsKey(section.innerRegionKey)) {
            return sectionMetaMap.get(pos2id.get(section.innerRegionKey));
        }

        int id = newSectionId();
        pos2id.put(section.innerRegionKey, id);
        SectionMeta newMeta = new SectionMeta(id, metaBuffer, section);
        sectionMetaMap.put(id, newMeta);
        return newMeta;
    }

    public synchronized void freeMetaSection(RenderSection section) {
        if (pos2id.containsKey(section.innerRegionKey)) {
            int id = pos2id.remove(section.innerRegionKey);
            SectionMeta sectionMeta = sectionMetaMap.remove(id);
            sectionMeta.delete();

            if (id == sectionCount-1) {
                //Free as many sections as possible
                sectionCount--;
                while ((!freeIds.isEmpty()) && freeIds.lastInt() == sectionCount -1) {
                    freeIds.remove(freeIds.lastInt());
                    sectionCount--;
                }
                if (sectionCount == 0 && !freeIds.isEmpty())
                    throw new IllegalStateException();
            } else {
                //Enqueue id
                freeIds.add(id);
            }
        }
    }

    //FIXME: needs to see if it is in the current camera chunk and return true if it is
    public boolean isSectionVisible(RenderSection section) {
        return isSectionVisible(section.innerRegionKey);
    }

    public boolean isSectionVisible(int key) {
        var dat = ViewportedData.get();
        return getRenderData().cpuSectionVis.view().getInt(pos2id.get(key)*4) == 1 ||
                (dat.cameraRenderRegion == this.key && key == dat.cameraRenderRegionInner);
    }

    public RenderSection getSectionOrNull(int key) {
        if (!doesSectionExist(key))
            return null;
        return sectionMetaMap.get(pos2id.get(key)).theSection;
    }

    public boolean doesSectionExist(int key) {
        if (!pos2id.containsKey(key))
            return false;
        if (freeIds.contains(pos2id.get(key))) {
            return false;
        }
        if (sectionMetaMap.get(pos2id.get(key)) == null) {
            return false;
        }
        return true;
    }

    //FIXME: use better data structure
    private final IntOpenHashSet sectionsRequestingUpdate = new IntOpenHashSet(REGION_SIZE);
    private int count;
    public void markSectionUpdateRequest(RenderSection section) {
        if (!sectionsRequestingUpdate.contains(section.innerRegionKey)) {
            count += 1;
            sectionsRequestingUpdate.add(section.innerRegionKey);
        }
    }

    public void unmarkSectionUpdateRequest(RenderSection section) {
        if (sectionsRequestingUpdate.contains(section.innerRegionKey)) {
            count -= 1;
            sectionsRequestingUpdate.remove(section.innerRegionKey);
        }
    }


    //TODO: clean this up i think
    public void onVisibleTick() {
        if (count == 0)
            return;
        int budget = 1;
        IntList toRemove = new IntArrayList(5);
        for (int key : sectionsRequestingUpdate) {
            if (budget == 0) {
                return;
            }
            if (!doesSectionExist(key)) {
                toRemove.add(key);
                count--;
                return;
            }
            if (isSectionVisible(key)) {
                RenderSection section = sectionMetaMap.get(pos2id.get(key)).theSection;
                var queue = sectionManager.rebuildQueues.get(section.getPendingUpdate());
                if (queue == null)
                    return;

                budget--;
                count--;
                toRemove.add(key);
                queue.enqueue(section);
            }
        }

        if (toRemove.size() != 0) {
            for (int i : toRemove) {
                sectionsRequestingUpdate.remove(i);
            }
        }
    }

    private final Int2ObjectOpenHashMap<RenderRegionInstancedRenderData> renderData = new Int2ObjectOpenHashMap<>(2);
    private RenderRegionInstancedRenderData renderDataCurrent;
    private int currentViewport = -1;
    public RenderRegionInstancedRenderData getRenderData() {
        if (currentViewport != ViewportInterface.CURRENT_VIEWPORT) {
            renderDataCurrent = renderData.computeIfAbsent(ViewportInterface.CURRENT_VIEWPORT, k->new RenderRegionInstancedRenderData(device));
            currentViewport = ViewportInterface.CURRENT_VIEWPORT;
        }
        return renderDataCurrent;
    }

}
