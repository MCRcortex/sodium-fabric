package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.util.collections.BitArray;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HeightLimitView;

import java.util.BitSet;

public class SectionState {
    private static final long VIS_DATA_MASK = ~(-1L << DirectionUtil.COUNT);
    public static final int NULL_INDEX = -1;

    public final BitArray sectionExistenceBits;

    public final int renderDistance;

    public final double squaredFogDistance;

    public final int idxYShift;
    public final int idxZShift;
    public final int idxXMask;
    public final int idxYMask;
    public final int idxZMask;

    public final int sectionWidthMask;
    public final int sectionWidth;
    public final int sectionWidthSquared;

    public final int sectionHeight;
    public final int sectionHeightOffset;
    public final int sectionHeightMin;
    public final int sectionHeightMax;

    public final int sectionTableSize;

    public final int maxDepth;

    public final byte[] sectionVisibilityData;
    public final byte[] sectionFlagData;
    public final RenderSection[] sections;

    public SectionState(int renderDistance, HeightLimitView heightLimitView) {
        this.sectionHeightMin = heightLimitView.getBottomSectionCoord();
        this.sectionHeightMax = heightLimitView.getTopSectionCoord();

        this.renderDistance = renderDistance;
        this.squaredFogDistance = MathHelper.square((renderDistance + 1) * 16.0);

        // Make the width (in sections) a power-of-two, so we can exploit bit-wise math when computing indices
        this.sectionWidth = MathHelper.smallestEncompassingPowerOfTwo(renderDistance * 2 + 1);
        this.sectionWidthSquared = this.sectionWidth * this.sectionWidth;

        this.sectionWidthMask = this.sectionWidth - 1;
        this.idxZShift = Integer.numberOfTrailingZeros(this.sectionWidth);
        this.idxYShift = this.idxZShift * 2;
        this.idxYMask = -(1 << this.idxYShift);
        this.idxXMask = this.sectionWidthMask;
        this.idxZMask = this.sectionWidthMask << this.idxZShift;

        this.maxDepth = Math.min(3, Integer.SIZE - Integer.numberOfLeadingZeros(this.sectionWidth) - 1);

        this.sectionHeight = heightLimitView.countVerticalSections();
        this.sectionHeightOffset = -heightLimitView.getBottomSectionCoord();

        this.sectionTableSize = this.sectionWidthSquared * this.sectionHeight;

        this.sections = new RenderSection[this.sectionTableSize];
        this.sectionFlagData = new byte[this.sectionTableSize];
        this.sectionVisibilityData = new byte[this.sectionTableSize * DirectionUtil.COUNT];
        this.sectionExistenceBits = new BitArray(this.sectionTableSize);

    }

    public void setChunkDataFlags(RenderSection render) {
        byte flags = (byte) render.flags;
        if (render.getPendingUpdate() == ChunkUpdateType.IMPORTANT_REBUILD) {
            flags |= ChunkDataFlags.NEEDS_UPDATE_IMPORTANT;
        } else if (render.getPendingUpdate() != null) {
            flags |= ChunkDataFlags.NEEDS_UPDATE;
        }
        sectionFlagData[getSectionIdx(render.getChunkX(),render.getChunkY(),render.getChunkZ())] = flags;
    }

    public void markLoaded(int x, int y, int z, RenderSection section) {
        int id = getSectionIdx(x, y, z);
        if (sections[id] != null)
            throw new IllegalStateException();
        sections[id] = section;
        sectionExistenceBits.set(id);
    }

    public RenderSection unmarkLoaded(int x, int y, int z) {
        int id = getSectionIdx(x, y, z);
        if (sections[id] == null)
            throw new IllegalStateException();
        RenderSection ret = sections[id];
        sections[id] = null;
        sectionExistenceBits.unset(id);
        setChunkVisibility(x,y,z, ChunkRenderData.EMPTY.getOcclusionData());
        return ret;
    }

    public void setChunkVisibility(int x, int y, int z, ChunkOcclusionData data) {
        int sectionIdx = getSectionIdx(x, y, z);

        long bits = 0;

        // The underlying data is already formatted to what we need, so we can just grab the long representation and work with that
        if (data != null) {
            BitSet bitSet = data.visibility;
            if (!bitSet.isEmpty()) {
                bits = bitSet.toLongArray()[0];
            }
        }

        for (int fromIdx = 0; fromIdx < DirectionUtil.COUNT; fromIdx++) {
            byte toBits = (byte) (bits & VIS_DATA_MASK);
            bits >>= DirectionUtil.COUNT;

            this.sectionVisibilityData[(sectionIdx * DirectionUtil.COUNT) + fromIdx] = toBits;
        }
    }



    public byte getSectionFlagData(int sectionIdx) {
        return sectionFlagData[sectionIdx];
    }

    public short getVisibilityData(int neighborSectionIdx, int inboundDir) {
        return sectionVisibilityData[neighborSectionIdx*DirectionUtil.COUNT + inboundDir];
    }

    public int getLoadedSections() {
        return sectionExistenceBits.count();
    }

    public int getSectionIdx(int x, int y, int z) {
        int tableY = y + this.sectionHeightOffset;
        int tableZ = z & this.sectionWidthMask;
        int tableX = x & this.sectionWidthMask;
        return (tableY << this.idxYShift)
                | (tableZ << this.idxZShift)
                | tableX;
    }
}
