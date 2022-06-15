package net.caffeinemc.sodium.render.chunk.region;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBuffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.arena.AsyncBufferArena;
import net.caffeinemc.sodium.render.arena.BufferArena;
import net.caffeinemc.sodium.render.buffer.StreamingBuffer;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.occlussion.SectionMeta;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Set;

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

    public final BufferArena vertexBuffers;
    public final StreamingBuffer metaBuffer;
    public final ImmutableBuffer visBuffer;
    //public final MappedBuffer computeDispatchIndirectBuffer;//Nice way to not actually call 90% of compute
    public final MappedBuffer sceneBuffer;

    public final MappedBuffer dodgyThing;
    public final ImmutableBuffer counterBuffer;
    //public final MappedBuffer counterBuffer;

    //FIXME: can probably move this to be a bigger buffer for all lists, note will need to be 1 PER render layer
    // steal from RenderListBuilder
    /*
    public final ImmutableBuffer countBuffer;
    public final ImmutableBuffer commandBuffer;
     */
    public final ImmutableBuffer instanceBuffer;
    //public final MappedBuffer instanceBuffer;

    //public final MappedBuffer cmd0buff;//just for testing will be moved
    public final ImmutableBuffer cmd0buff;//just for testing will be moved
    public final ImmutableBuffer cmd1buff;//just for testing will be moved
    public final ImmutableBuffer cmd2buff;//just for testing will be moved

    public float weight;//Util thing

    public final int id;

    public RenderRegion(RenderDevice device, StreamingBuffer streamingBuffer, TerrainVertexType vertexType, int id, long regionKey) {
        this.vertexBuffers = new AsyncBufferArena(device, streamingBuffer, REGION_SIZE * 756, vertexType.getBufferVertexFormat().stride());
        this.metaBuffer = new StreamingBuffer(device, 1, SectionMeta.SIZE, REGION_SIZE,
                MappedBufferFlags.EXPLICIT_FLUSH);//FIXME: add relevant flags
        this.visBuffer = device.createBuffer(REGION_SIZE*4, Set.of());
        this.id = id;
        sceneBuffer = device.createMappedBuffer(4*4*4+3*4, Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));
        ChunkSectionPos csp = ChunkSectionPos.from(regionKey);
        regionX = csp.getSectionX();
        regionY = csp.getSectionY();
        regionZ = csp.getSectionZ();
        //this.counterBuffer = device.createMappedBuffer(5*4, Set.of(MappedBufferFlags.READ));//FIXME: add relevant flags
        this.counterBuffer = device.createBuffer(5*4, Set.of());
        this.dodgyThing = device.createMappedBuffer(5*4, Set.of(MappedBufferFlags.READ));
        //this.computeDispatchIndirectBuffer = device.createMappedBuffer(3*4, Set.of(MappedBufferFlags.WRITE, MappedBufferFlags.EXPLICIT_FLUSH));

        this.instanceBuffer = device.createBuffer(REGION_SIZE*4*3, Set.of());
        //this.instanceBuffer = device.createMappedBuffer(REGION_SIZE*4*3, Set.of(MappedBufferFlags.READ));//FIXME: add relevant flags

        //this.cmd0buff = device.createMappedBuffer(REGION_SIZE*5*4*6, Set.of(MappedBufferFlags.WRITE));//FIXME: TUNE BUFFER SIZE
        //FIXME: nvidia driver bug, causes instant hard crash due to OOM
        //If empty memory buffer is specified, this fixes it
        this.cmd0buff = device.createBuffer(ByteBuffer.allocateDirect(REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd1buff = device.createBuffer(ByteBuffer.allocateDirect(REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
        this.cmd2buff = device.createBuffer(ByteBuffer.allocateDirect(REGION_SIZE*5*4*6), Set.of());//FIXME: TUNE BUFFER SIZE
    }

    public void delete() {
        this.vertexBuffers.delete();
    }

    public boolean isEmpty() {
        return this.vertexBuffers.isEmpty();
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
        SectionMeta newMeta = new SectionMeta(id, metaBuffer);
        sectionMetaMap.put(id, newMeta);
        return newMeta;
    }

    public synchronized void freeMetaSection(RenderSection section) {
        if (pos2id.containsKey(section.innerRegionKey)) {
            int id = pos2id.remove(section.innerRegionKey);
            SectionMeta sectionMeta = sectionMetaMap.remove(id);
            //sectionMeta.free();//TODO: this, need to set the id of the meta to like -1

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
}
