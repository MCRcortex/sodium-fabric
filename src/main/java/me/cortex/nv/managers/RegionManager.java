package me.cortex.nv.managers;


import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.nv.util.IdProvider;
import me.cortex.nv.gl.GlBuffer;
import me.cortex.nv.gl.UploadingBufferStream;
import net.minecraft.util.math.ChunkSectionPos;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.BitSet;

//8x4x8
public class RegionManager {
    public static final int META_SIZE = 8;

    private final Long2ObjectOpenHashMap<Region> REGIONS = new Long2ObjectOpenHashMap<>();
    private final IdProvider idProvider = new IdProvider();
    private final GlBuffer regionBuffer;

    public RegionManager(GlBuffer regionBuffer) {
        this.regionBuffer = regionBuffer;
    }

    //IDEA: make it so that sections are packed into regions, that is the local index of a chunk is hard coded to its position, and just 256 sections are processed when a region is visible, this has some overhead but means that the exact amount being processed each time is known and the same
    private static final class Region {
        private final long key;
        private final int id;//This is the location of the region in memory, the sections it belongs to are indexed by region.id*256+section.id
        //private final short[] mapping = new short[256];//can theoretically get rid of this
        private final BitSet freeIndices = new BitSet(256);
        private int count;

        private Region(long key, int id) {
            this.key = key;
            this.id = id;
            //Arrays.fill(mapping, (short) -1);
            freeIndices.set(0,256);
        }


        public long getPackedData() {
            if (count == 0) {
                return 0;//Basically return null
            }
            //TODO: THIS
            return -1;
        }
    }

    private static int getLocalSectionId(int sectionX, int sectionY, int sectionZ) {
        return ((sectionY&3)<<6)|((sectionZ&7)<<3)|(sectionX&7);
    }

    public static long getRegionKey(int sectionX, int sectionY, int sectionZ) {
        return ChunkSectionPos.asLong(sectionX>>3, sectionY>>2, sectionZ>>3);
    }

    public int createSectionIndex(UploadingBufferStream uploadStream, int sectionX, int sectionY, int sectionZ) {
        Region region = REGIONS.computeIfAbsent(getRegionKey(sectionX, sectionY, sectionZ), key -> new Region(key, idProvider.provide()));
        region.count++;

        int sectionId = region.freeIndices.nextSetBit(0);
        region.freeIndices.clear(sectionId);

        updateRegion(uploadStream, region);

        return region.id*8+sectionId;//TODO: i think this is region.id*256+sectionId
    }

    public void removeSectionIndex(UploadingBufferStream uploadStream, int sectionId) {
        Region region = REGIONS.get(sectionId>>3);// divide by 8
        region.count--;
        region.freeIndices.set(sectionId&7);

        if (region.count == 0) {
            idProvider.release(region.id);
            //Note: there is a special-case in region.getPackedData that means when count == 0, it auto nulls
            updateRegion(uploadStream, region);
            REGIONS.remove(region.key);
        } else {
            updateRegion(uploadStream, region);
        }
    }

    private void updateRegion(UploadingBufferStream uploadingStream, Region region) {
        ByteBuffer segment = uploadingStream.getUpload(META_SIZE);
        MemoryUtil.memPutLong(MemoryUtil.memAddress(segment), region.getPackedData());
        uploadingStream.upload(regionBuffer.id(), (long) region.id * META_SIZE);
    }


}
