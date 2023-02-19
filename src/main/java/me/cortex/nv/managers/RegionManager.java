package me.cortex.nv.managers;


import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.nv.IdProvider;
import me.cortex.nv.gl.UploadingBufferStream;
import net.minecraft.util.math.ChunkSectionPos;

//8x4x8
public class RegionManager {
    private final Long2ObjectOpenHashMap<Region> REGIONS = new Long2ObjectOpenHashMap<>();
    private final IdProvider idProvider = new IdProvider();

    //IDEA: make it so that sections are packed into regions, that is the local index of a chunk is hard coded to its position, and just 256 sections are processed when a region is visible, this has some overhead but means that the exact amount being processed each time is known and the same
    private static final class Region {
        private final long key;
        private final int id;//This is the location of the region in memory, the sections it belongs to are indexed by region.id*256+section.id
        private final short[] mapping = new short[256];

        private Region(long key, int id) {
            this.key = key;
            this.id = id;
        }


        public long getPackedData() {

        }

        public boolean isEmpty() {

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

    }

    public void removeSectionIndex(UploadingBufferStream uploadStream, int sectionX, int sectionY, int sectionZ, int sectionIdx) {
        Region region = REGIONS.get(getRegionKey(sectionX, sectionY, sectionZ));

        if (region.isEmpty()) {
            idProvider.release(region.id);
            //TODO: clear region.id data
        }
    }


}
