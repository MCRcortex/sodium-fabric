package me.cortex.nv.managers;


import me.cortex.nv.gl.UploadingBufferStream;

public class RegionManager {
    public static final int REGION_DIM = 16;
    public static final int SECTION_SHIFT = Integer.numberOfTrailingZeros(REGION_DIM);
    public static final int SECTION_COUNT = REGION_DIM*REGION_DIM*REGION_DIM;

    public int createSectionIndex(UploadingBufferStream uploadStream, int sectionX, int sectionY, int sectionZ) {

    }

    public void removeSectionIndex(UploadingBufferStream uploadStream, int sectionX, int sectionY, int sectionZ, int sectionIdx) {

    }

    public static final class Region {

    }

    public Region createOrGet(int sectionX, int sectionY, int sectionZ) {
        Region region = get(sectionX, sectionY, sectionZ);
        if (region != null) {
            return region;
        }
        return null;
    }

    public Region get(int sectionX, int sectionY, int sectionZ) {
        return null;
    }
}
