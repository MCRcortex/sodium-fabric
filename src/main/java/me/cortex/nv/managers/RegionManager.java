package me.cortex.nv.managers;


import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.util.IdProvider;
import me.cortex.nv.util.UploadingBufferStream;
import net.minecraft.util.math.ChunkSectionPos;
import org.lwjgl.system.MemoryUtil;

import java.util.BitSet;

//8x4x8
public class RegionManager {
    public static final int META_SIZE = 8;

    private final Long2IntOpenHashMap regionMap;
    private final IdProvider idProvider = new IdProvider();
    private final Buffer regionBuffer;
    private final RenderDevice device;

    private final Region[] regions;

    public RegionManager(RenderDevice device, int maxRegions) {
        this.device = device;
        this.regionBuffer = device.createDeviceOnlyBuffer((long) maxRegions * META_SIZE);
        this.regions = new Region[maxRegions];
        this.regionMap = new Long2IntOpenHashMap(maxRegions);
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
        long key = getRegionKey(sectionX, sectionY, sectionZ);
        int idx = regionMap.computeIfAbsent(key, k -> idProvider.provide());
        Region region = regions[idx];
        if (region == null) {
            region = regions[idx] = new Region(key, idx);
        }
        if (region.key != key) {
            throw new IllegalStateException();
        }
        region.count++;

        int sectionId = region.freeIndices.nextSetBit(0);
        if (sectionId<0||255<sectionId) {
            throw new IllegalStateException();
        }
        region.freeIndices.clear(sectionId);

        updateRegion(uploadStream, region);

        return (region.id<<8)|sectionId;//region.id*8+sectionId
    }

    public void removeSectionIndex(UploadingBufferStream uploadStream, int sectionId) {
        Region region = regions[sectionId>>>8];// divide by 256
        if (region == null) {
            throw new IllegalStateException();
        }
        region.count--;
        region.freeIndices.set(sectionId&255);

        if (region.count == 0) {
            idProvider.release(region.id);
            //Note: there is a special-case in region.getPackedData that means when count == 0, it auto nulls
            updateRegion(uploadStream, region);
            regions[region.id] = null;
            regionMap.remove(region.key);
        } else {
            updateRegion(uploadStream, region);
        }
    }

    private void updateRegion(UploadingBufferStream uploadingStream, Region region) {
        long segment = uploadingStream.getUpload(regionBuffer, (long) region.id * META_SIZE, META_SIZE);


        MemoryUtil.memPutLong(segment, region.getPackedData());
    }

    public void delete() {
        regionBuffer.delete();
    }
}
