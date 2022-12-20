package me.jellysquid.mods.sodium.client.render.chunk;

public class SortedSectionList {
    public final int[] terrainSectionIdxs;
    public int terrainSectionCount;

    public final int[] blockEntitySectionIdxs;
    public int blockEntitySectionCount;

    public final int[] tickingTextureSectionIdxs;
    public int tickingTextureSectionCount;

    public final int[] importantUpdatableSectionIdxs;
    public int importantUpdateSectionCount;

    public final int[] secondaryUpdatableSectionIdxs;
    public int secondaryUpdateSectionCount;

    public SortedSectionList(int max) {
        terrainSectionIdxs = new int[max];
        blockEntitySectionIdxs = new int[max];
        tickingTextureSectionIdxs = new int[max];
        importantUpdatableSectionIdxs = new int[max];
        secondaryUpdatableSectionIdxs = new int[max];
    }

    public void reset() {
        terrainSectionCount = 0;
        blockEntitySectionCount = 0;
        tickingTextureSectionCount = 0;
        importantUpdateSectionCount = 0;
        secondaryUpdateSectionCount = 0;
    }

    public static boolean has(byte flags, int flag) {
        return (flags & (flag)) != 0;
    }

    public void enqueueSection(byte flags, int sectionIdx) {
        if (has(flags, ChunkDataFlags.HAS_BLOCK_GEOMETRY)) {
            this.terrainSectionIdxs[this.terrainSectionCount++] = sectionIdx;
        }

        if (has(flags, ChunkDataFlags.HAS_BLOCK_ENTITIES)) {
            this.blockEntitySectionIdxs[this.blockEntitySectionCount++] = sectionIdx;
        }

        if (has(flags, ChunkDataFlags.HAS_ANIMATED_SPRITES)) {
            this.tickingTextureSectionIdxs[this.tickingTextureSectionCount++] = sectionIdx;
        }

        if (has(flags, ChunkDataFlags.NEEDS_UPDATE)) {
            if (has(flags, ChunkDataFlags.NEEDS_UPDATE_IMPORTANT)) {
                this.importantUpdatableSectionIdxs[this.importantUpdateSectionCount++] = sectionIdx;
            } else {
                this.secondaryUpdatableSectionIdxs[this.secondaryUpdateSectionCount++] = sectionIdx;
            }
        }
    }
}
