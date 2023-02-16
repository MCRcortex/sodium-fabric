package me.cortex.nv.managers;


import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.nv.gl.UploadingBufferStream;
import net.minecraft.util.math.ChunkSectionPos;

//8x4x8
public class RegionManager {
    private final Long2ObjectOpenHashMap<Region> REGIONS = new Long2ObjectOpenHashMap<>();

    private static final class Region {

    }

    public static long getRegionKey(int sectionX, int sectionY, int sectionZ) {
        return ChunkSectionPos.asLong(sectionX>>3, sectionY>>2, sectionZ>>3);
    }

    public int createSectionIndex(UploadingBufferStream uploadStream, int sectionX, int sectionY, int sectionZ) {

    }

    public void removeSectionIndex(UploadingBufferStream uploadStream, int sectionX, int sectionY, int sectionZ, int sectionIdx) {

    }


}
