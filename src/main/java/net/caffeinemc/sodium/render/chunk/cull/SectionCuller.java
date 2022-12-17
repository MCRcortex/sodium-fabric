package net.caffeinemc.sodium.render.chunk.cull;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.Iterator;
import net.caffeinemc.gfx.util.misc.MathUtil;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.util.DirectionUtil;
import net.caffeinemc.sodium.util.collections.BitArray;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SectionCuller {

    private final SectionTree sectionTree;
    private final int chunkViewDistance;
    private final double squaredFogDistance;

    private final BitArray sectionVisibilityBits;
    private final BitArray sectionOcclusionVisibilityBits;

    private final byte[] sectionDirVisibilityData;

    private final byte[] sectionFlagData;


    private final IntList[] fallbackSectionLists;

    // Chunks are grouped by manhattan distance to the start chunk, and given
    // the fact that the chunk graph is bipartite, it's possible to simply
    // alternate the lists to form a queue
    private final int[] queue1;
    private final int[] queue2;

    private final int[] sortedVisibleSections;
    private int visibleSectionCount;

    public SectionCuller(SectionTree sectionTree, int chunkViewDistance) {
        this.sectionTree = sectionTree;
        this.chunkViewDistance = chunkViewDistance;
        this.squaredFogDistance = MathHelper.square((chunkViewDistance + 1) * 16.0);

        int maxSize = (MathHelper.square(chunkViewDistance * 2 + 1) * sectionTree.sectionHeight);
        this.queue1 = new int[maxSize];
        this.queue2 = new int[maxSize];
        this.sortedVisibleSections = new int[maxSize];

        this.fallbackSectionLists = new IntList[chunkViewDistance * 2 + 1];
        // the first list will have a known size of 1 always
        this.fallbackSectionLists[0] = new IntArrayList(1);
        for (int i = 1; i < this.fallbackSectionLists.length; i++) {
            this.fallbackSectionLists[i] = new IntArrayList();
        }

        this.sectionDirVisibilityData = new byte[sectionTree.getSectionTableSize() * DirectionUtil.COUNT];
        this.sectionVisibilityBits = new BitArray(sectionTree.getSectionTableSize());
        this.sectionOcclusionVisibilityBits = new BitArray(sectionTree.getSectionTableSize());

        this.sectionFlagData = new byte[sectionTree.getSectionTableSize()];
        this.cullMeta = new short[sectionTree.getSectionTableSize()];
        this.visibleChunks = new int[sectionTree.getSectionTableSize()];
        this.stuffChunks = new int[sectionTree.getSectionTableSize()];
    }
    short[] cullMeta;
    int[] visibleChunks;
    int stuffCount;
    int[] stuffChunks;

    public void calculateVisibleSections(
            Frustum frustum,
            boolean useOcclusionCulling
    ) {
        this.sectionVisibilityBits.fill(false);
        this.sectionOcclusionVisibilityBits.fill(false);

        if (this.sectionTree.getLoadedSections() != 0) {
            // Start with corner section of the fog distance.
            // To do this, we have to reverse the function to check if a chunk is in bounds by doing pythagorean's
            // theorem, then doing some math.
            double cameraX = this.sectionTree.camera.getPosX();
            double cameraZ = this.sectionTree.camera.getPosZ();
            double sectionCenterDistX = MathUtil.floorMod(cameraX, 16.0) - 8.0;
            double sectionCenterDistZ = MathUtil.floorMod(cameraZ, 16.0) - 8.0;
            double distX = Math.sqrt(this.squaredFogDistance - (sectionCenterDistZ * sectionCenterDistZ));
            double distZ = Math.sqrt(this.squaredFogDistance - (sectionCenterDistX * sectionCenterDistX));

            int sectionZStart = ChunkSectionPos.getSectionCoord(cameraZ - distZ - 8.0);
            int sectionXStart = ChunkSectionPos.getSectionCoord(cameraX - distX - 8.0);
            int sectionZEnd = ChunkSectionPos.getSectionCoord(cameraZ + distZ + 8.0);
            int sectionXEnd = ChunkSectionPos.getSectionCoord(cameraX + distX + 8.0);

            this.frustumCull(
                    frustum,
                    sectionZStart,
                    sectionXStart,
                    sectionZEnd,
                    sectionXEnd
            );

            this.fogCull(
                    sectionZStart,
                    sectionXStart,
                    sectionZEnd,
                    sectionXEnd
            );

            // still need to do this to maintain ordering between sections, even if useOcclusionCulling is false

            this.occlusionCull(
                    useOcclusionCulling,
                    sectionZStart,
                    sectionXStart,
                    sectionZEnd,
                    sectionXEnd
            );
        }
    }

    // inlining the locals makes it harder to read
    @SuppressWarnings("UnnecessaryLocalVariable")
    private void frustumCull(
            Frustum frustum,
            final int sectionZStart,
            final int sectionXStart,
            final int sectionZEnd,
            final int sectionXEnd
    ) {
        int nodeSectionLength = 1 << this.sectionTree.maxDepth;

        int yIdxIncrement = nodeSectionLength * this.sectionTree.sectionWidthSquared;
        int zIdxIncrement = nodeSectionLength * this.sectionTree.sectionWidth;
        int xIdxIncrement = nodeSectionLength;

        // Z and X table indices *are* restricted to the table.
        final int tableZStart = sectionZStart & this.sectionTree.sectionWidthMask;
        final int tableXStart = sectionXStart & this.sectionTree.sectionWidthMask;

        final int zIdxStart = tableZStart * this.sectionTree.sectionWidth;
        final int xIdxStart = tableXStart;
        final int zIdxWrap = this.sectionTree.sectionWidthSquared;
        final int xIdxWrap = this.sectionTree.sectionWidth;

        int sectionZSplit = Math.min(sectionZStart + this.sectionTree.sectionWidth - tableZStart, sectionZEnd);
        int sectionXSplit = Math.min(sectionXStart + this.sectionTree.sectionWidth - tableXStart, sectionXEnd);

        for (int sectionY = this.sectionTree.sectionHeightMin, yIdxOffset = 0; sectionY < this.sectionTree.sectionHeightMax; sectionY += nodeSectionLength, yIdxOffset += yIdxIncrement) {
            int sectionZ = sectionZStart;
            int sectionZMax = sectionZSplit;
            int zIdxOffset = zIdxStart;
            while (true) {
                if (zIdxOffset >= zIdxWrap && sectionZMax != sectionZEnd) {
                    zIdxOffset = 0;
                    sectionZ = sectionZMax;
                    sectionZMax = sectionZEnd;
                }

                if (sectionZ >= sectionZEnd) {
                    break;
                }

                int sectionX = sectionXStart;
                int sectionXMax = sectionXSplit;
                int xIdxOffset = xIdxStart;
                while (true) {
                    if (xIdxOffset >= xIdxWrap && sectionXMax != sectionXEnd) {
                        xIdxOffset = 0;
                        sectionX = sectionXMax;
                        sectionXMax = sectionXEnd;
                    }

                    if (sectionX >= sectionXEnd) {
                        break;
                    }

                    this.checkNode(
                            frustum,
                            sectionY,
                            sectionZ,
                            sectionX,
                            sectionZMax,
                            sectionXMax,
                            this.sectionTree.maxDepth,
                            yIdxOffset + zIdxOffset + xIdxOffset,
                            Frustum.BLANK_RESULT
                    );

                    sectionX += nodeSectionLength;
                    xIdxOffset += xIdxIncrement;
                }

                sectionZ += nodeSectionLength;
                zIdxOffset += zIdxIncrement;
            }
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void checkNode(
            Frustum frustum,
            int sectionY,
            int sectionZ,
            int sectionX,
            int sectionZMax,
            int sectionXMax,
            int depth,
            int sectionIdx,
            int parentTestResult
    ) {
        final int nodeSectionLength = 1 << depth;

        final int sectionYEnd = Math.min(sectionY + nodeSectionLength, this.sectionTree.sectionHeightMax);
        final int sectionZEnd = Math.min(sectionZ + nodeSectionLength, sectionZMax);
        final int sectionXEnd = Math.min(sectionX + nodeSectionLength, sectionXMax);

        float minY = (float) ChunkSectionPos.getBlockCoord(sectionY);
        float minZ = (float) ChunkSectionPos.getBlockCoord(sectionZ);
        float minX = (float) ChunkSectionPos.getBlockCoord(sectionX);
        float maxY = (float) ChunkSectionPos.getBlockCoord(sectionYEnd);
        float maxZ = (float) ChunkSectionPos.getBlockCoord(sectionZEnd);
        float maxX = (float) ChunkSectionPos.getBlockCoord(sectionXEnd);

        int frustumTestResult = frustum.intersectBox(minX, minY, minZ, maxX, maxY, maxZ, parentTestResult);

        if (frustumTestResult != Frustum.OUTSIDE) {
            if (frustumTestResult == Frustum.INSIDE) {
                for (int newSectionY = sectionY, yIdxOffset = 0; newSectionY < sectionYEnd; newSectionY++, yIdxOffset += this.sectionTree.sectionWidthSquared) {
                    for (int newSectionZ = sectionZ, zIdxOffset = 0; newSectionZ < sectionZEnd; newSectionZ++, zIdxOffset += this.sectionTree.sectionWidth) {
                        this.sectionVisibilityBits.copy(
                                this.sectionTree.sectionExistenceBits,
                                sectionIdx + yIdxOffset + zIdxOffset,
                                sectionIdx + yIdxOffset + zIdxOffset + sectionXEnd - sectionX
                        );
                    }
                }
            } else {
                int childDepth = depth - 1;
                int childSectionLength = nodeSectionLength >> 1;

                int yIdxIncrement = childSectionLength * this.sectionTree.sectionWidthSquared;
                int zIdxIncrement = childSectionLength * this.sectionTree.sectionWidth;

                for (int newSectionY = sectionY, yIdxOffset = 0; newSectionY < sectionYEnd; newSectionY += childSectionLength, yIdxOffset += yIdxIncrement) {
                    for (int newSectionZ = sectionZ, zIdxOffset = 0; newSectionZ < sectionZEnd; newSectionZ += childSectionLength, zIdxOffset += zIdxIncrement) {
                        for (int newSectionX = sectionX, xIdxOffset = 0; newSectionX < sectionXEnd; newSectionX += childSectionLength, xIdxOffset += childSectionLength) {

                            int newSectionIdx = sectionIdx + yIdxOffset + zIdxOffset + xIdxOffset;
                            // check should get moved outside of loop
                            if (childDepth == 0) {
                                this.checkSection(
                                        frustum,
                                        newSectionY,
                                        newSectionZ,
                                        newSectionX,
                                        newSectionIdx,
                                        frustumTestResult
                                );
                            } else {
                                this.checkNode(
                                        frustum,
                                        newSectionY,
                                        newSectionZ,
                                        newSectionX,
                                        sectionZMax,
                                        sectionXMax,
                                        childDepth,
                                        newSectionIdx,
                                        frustumTestResult
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkSection(
            Frustum frustum,
            int sectionY,
            int sectionZ,
            int sectionX,
            int sectionIdx,
            int parentTestResult
    ) {
        // skip if the section is empty
        if (!this.sectionTree.sectionExistenceBits.get(sectionIdx)) {
            return;
        }

        float minY = (float) ChunkSectionPos.getBlockCoord(sectionY);
        float minZ = (float) ChunkSectionPos.getBlockCoord(sectionZ);
        float minX = (float) ChunkSectionPos.getBlockCoord(sectionX);
        float maxY = minY + 16.0f;
        float maxZ = minZ + 16.0f;
        float maxX = minX + 16.0f;

        if (frustum.containsBox(minX, minY, minZ, maxX, maxY, maxZ, parentTestResult)) {
            // we already tested that it does exist, so we can unconditionally set
            this.sectionVisibilityBits.set(sectionIdx);
        }
    }

    // always use a cylindrical cull for fog.
    // we don't want to cull above and below the player for various reasons.
    //
    // inlining the locals makes it harder to read
    @SuppressWarnings({"UnnecessaryLocalVariable", "SuspiciousNameCombination"})
    private void fogCull(
            final int sectionZStart,
            final int sectionXStart,
            final int sectionZEnd,
            final int sectionXEnd
    ) {
        int zIdxIncrement = this.sectionTree.sectionWidth;
        int xIdxIncrement = 1;

        // Table indices *are* restricted to the table.
        final int tableZStart = sectionZStart & this.sectionTree.sectionWidthMask;
        final int tableXStart = sectionXStart & this.sectionTree.sectionWidthMask;

        final int zIdxStart = tableZStart * this.sectionTree.sectionWidth;
        final int xIdxStart = tableXStart;
        final int zIdxWrap = this.sectionTree.sectionWidthSquared;
        final int xIdxWrap = this.sectionTree.sectionWidth;

        int sectionZSplit = Math.min(sectionZStart + this.sectionTree.sectionWidth - tableZStart, sectionZEnd);
        int sectionXSplit = Math.min(sectionXStart + this.sectionTree.sectionWidth - tableXStart, sectionXEnd);

        int sectionZ = sectionZStart;
        int sectionZMax = sectionZSplit;
        int zIdxOffset = zIdxStart;
        while (true) {
            if (zIdxOffset >= zIdxWrap && sectionZMax != sectionZEnd) {
                zIdxOffset = 0;
                sectionZ = sectionZMax;
                sectionZMax = sectionZEnd;
            }

            if (sectionZ >= sectionZEnd) {
                break;
            }

            int sectionX = sectionXStart;
            int sectionXMax = sectionXSplit;
            int xIdxOffset = xIdxStart;
            while (true) {
                if (xIdxOffset >= xIdxWrap && sectionXMax != sectionXEnd) {
                    xIdxOffset = 0;
                    sectionX = sectionXMax;
                    sectionXMax = sectionXEnd;
                }

                if (sectionX >= sectionXEnd) {
                    break;
                }

                if (!this.isChunkInDrawDistance(sectionX, sectionZ)) {
                    int yIdxIncrement = this.sectionTree.sectionWidthSquared;
                    int yIdxOffset = 0;
                    for (int sectionY = this.sectionTree.sectionHeightMin; sectionY < this.sectionTree.sectionHeightMax; sectionY++, yIdxOffset += yIdxIncrement) {
                        this.sectionVisibilityBits.unset(yIdxOffset + zIdxOffset + xIdxOffset);
                    }
                }

                sectionX++;
                xIdxOffset += xIdxIncrement;
            }

            sectionZ++;
            zIdxOffset += zIdxIncrement;
        }
    }


    private void vertifyVisiblityData(int sectionIdx, ChunkOcclusionData data) {
        if (sectionIdx == SectionTree.OUT_OF_BOUNDS_INDEX) {
            return;
        }

        for (var from : DirectionUtil.ALL_DIRECTIONS) {
            int bits = 0;

            if (data != null) {
                for (var to : DirectionUtil.ALL_DIRECTIONS) {
                    if (data.isVisibleThrough(from, to)) {
                        bits |= 1 << to.ordinal();
                    }
                }
            }

            boolean same = this.sectionDirVisibilityData[(sectionIdx * DirectionUtil.COUNT) + from.ordinal()] == (byte) bits;
            if (!same) {
                System.err.println("ERROR");
            }
        }
    }

    int visibleChunksCount;
    int visibleChunksQueue;
    private void occlusionCull(
            boolean useOcclusion,
            final int sectionZStart,
            final int sectionXStart,
            final int sectionZEnd,
            final int sectionXEnd) {

        visibleChunksCount = 1;
        visibleChunksQueue = 0;
        stuffCount = 0;

        visibleChunks[0] = this.sectionTree.getSectionIdx(sectionTree.camera.getSectionX(),
                sectionTree.camera.getSectionY(),
                sectionTree.camera.getSectionZ());
        cullMeta[visibleChunks[0]] = -1;//All outbound directions

        while (visibleChunksQueue != visibleChunksCount) {
            int sectionId = visibleChunks[visibleChunksQueue++];

            byte flags = sectionFlagData[sectionId];//TODO: compact into 4 bits
            if (flags != 0) {
                stuffChunks[stuffCount++] = sectionId;//TODO: make this sort into the 4 different arrays
            }

            sectionVisibilityBits.unset(sectionId);//Performance hack, means it ignores sections if its already visited them
            short meta = cullMeta[sectionId];
            cullMeta[sectionId] = 0;//Reset the meta, meaning dont need to fill the array
            meta &= ((meta>>8)&0xFF)|0xFF00;//Apply inbound chunk filter to prevent backwards traversal
            for (int outgoingDir = 0; outgoingDir < DirectionUtil.COUNT; outgoingDir++) {
                if ((meta&(1<<outgoingDir))==0) continue;

                //TODO: check that the direction is facing away from the camera (dot product less positive or something)
                int neighborChunk = getAdjacentIdx(sectionId, outgoingDir, sectionZStart, sectionXStart, sectionZEnd, sectionXEnd);
                if (neighborChunk == SectionTree.OUT_OF_BOUNDS_INDEX || !sectionVisibilityBits.get(neighborChunk))
                    continue;

                short neighborMeta = cullMeta[neighborChunk];
                if (sectionTree.sections[neighborChunk] != null) {
                    vertifyVisiblityData(neighborChunk, sectionTree.sections[neighborChunk].getData().occlusionData);
                }
                if (neighborMeta == 0) {
                    visibleChunks[visibleChunksCount++] = neighborChunk;
                    neighborMeta |= (short) (1<<15)|(meta&0xFF00);
                }
                int inboundDir = DirectionUtil.getOppositeId(outgoingDir);
                neighborMeta |= getVisibilityData(neighborChunk, inboundDir);
                neighborMeta &= ~(1<<(8+inboundDir));//Un mark incoming direction
                cullMeta[neighborChunk] = neighborMeta;
            }
        }
        int x = 5;
        x+=0;
    }

    private static final int XP = 1 << DirectionUtil.X_PLUS;
    private static final int XN = 1 << DirectionUtil.X_MIN;
    private static final int ZP = 1 << DirectionUtil.Z_PLUS;
    private static final int ZN = 1 << DirectionUtil.Z_MIN;

    private void getStartingNodesFallback(int sectionX, int sectionY, int sectionZ) {
        int direction = sectionY < this.sectionTree.sectionHeightMin ? Direction.UP.getId() : Direction.DOWN.getId();
        int inDirection = DirectionUtil.getOppositeId(direction);
        // in theory useless
        int mask = 1 << direction;

        // clear out lists before running
        for (IntList sectionList : this.fallbackSectionLists) {
            sectionList.clear();
        }

        // M M M B J J J
        // M M I B F J J
        // M I I B F F J
        // E E E A C C C
        // L H H D G G K
        // L L H D G K K
        // L L L D K K K

        // A
        this.tryAddFallbackNode(
                this.sectionTree.getSectionIdx(sectionX, sectionY, sectionZ),
                inDirection,
                (byte) -1,
                this.fallbackSectionLists[0]
        );

        for (int distance = 1; distance <= this.chunkViewDistance; distance++) {
            IntList inner = this.fallbackSectionLists[distance];

            // nodes are checked at the following distances:
            // . . . 3 . . .
            // . . . 2 . . .
            // . . . 1 . . .
            // 3 2 1 . 1 2 3
            // . . . 1 . . .
            // . . . 2 . . .
            // . . . 3 . . .

            {
                // handle the mayor axis
                // B (north z-)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX, sectionY, sectionZ - distance),
                        inDirection,
                        (byte) (mask | XN | ZN | XP),
                        inner
                );
                // C (east x+)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX + distance, sectionY, sectionZ),
                        inDirection,
                        (byte) (mask | XP | ZN | ZP),
                        inner
                );
                // D (south z+)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX, sectionY, sectionZ + distance),
                        inDirection,
                        (byte) (mask | XP | ZP | XN),
                        inner
                );
                // E (west x-)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX - distance, sectionY, sectionZ),
                        inDirection,
                        (byte) (mask | XN | ZP | ZN),
                        inner
                );
            }

            // nodes are checked at the following distances:
            // . . . . . . .
            // . . 3 . 3 . .
            // . 3 2 . 2 3 .
            // . . . . . . .
            // . 3 2 . 2 3 .
            // . . 3 . 3 . .
            // . . . . . . .

            for (int dx = 1; dx < distance; dx++) {
                // handle the inside of the corners areas
                int dz = distance - dx;

                // F (northeast x+ z-)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX + dx, sectionY, sectionZ - dz),
                        inDirection,
                        (byte) (mask | XP | ZN),
                        inner
                );
                // G (southeast x+ z+)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX + dx, sectionY, sectionZ + dz),
                        inDirection,
                        (byte) (mask | XP | ZP),
                        inner
                );
                // H (southwest x- z+)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX - dx, sectionY, sectionZ + dz),
                        inDirection,
                        (byte) (mask | XN | ZP),
                        inner
                );
                // I (northwest x- z-)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX - dx, sectionY, sectionZ - dz),
                        inDirection,
                        (byte) (mask | XN | ZN),
                        inner
                );
            }
        }

        for (int distance = 1; distance <= this.chunkViewDistance; distance++) {
            // nodes are checked at the following distances:
            // 1 2 3 . 3 2 1
            // 2 3 . . . 3 2
            // 3 . . . . . 3
            // . . . . . . .
            // 3 . . . . . 3
            // 2 3 . . . 3 2
            // 1 2 3 . 3 2 1

            IntList outer = this.fallbackSectionLists[2 * this.chunkViewDistance - distance + 1];

            for (int i = 0; i < distance; i++) {
                int dx = this.chunkViewDistance - i;
                int dz = this.chunkViewDistance - distance + i + 1;

                // J (northeast x+ z-)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX + dx, sectionY, sectionZ - dz),
                        inDirection,
                        (byte) (mask | XP | ZN),
                        outer
                );
                // K (southeast x+ z+)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX + dx, sectionY, sectionZ + dz),
                        inDirection,
                        (byte) (mask | XP | ZP),
                        outer
                );
                // L (southwest x- z+)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX - dx, sectionY, sectionZ + dz),
                        inDirection,
                        (byte) (mask | XN | ZP),
                        outer
                );
                // M (northwest x- z-)
                this.tryAddFallbackNode(
                        this.sectionTree.getSectionIdx(sectionX - dx, sectionY, sectionZ - dz),
                        inDirection,
                        (byte) (mask | XN | ZN),
                        outer
                );
            }
        }
    }

    private void tryAddFallbackNode(int sectionIdx, int direction, byte directionMask, IntList sectionList) {
        if (sectionIdx != SectionTree.OUT_OF_BOUNDS_INDEX && this.sectionVisibilityBits.get(sectionIdx)) {
            sectionList.add(sectionIdx);

            int visible = this.getVisibilityData(sectionIdx, direction);
            //TODO: THIS
            //this.allowedTraversalDirections[sectionIdx] = directionMask;
            //this.visibleTraversalDirections[sectionIdx] = (byte) (directionMask & visible);
        }
    }

    public Iterator<RenderSection> getVisibleSectionIterator() {
        return new SortedVisibleSectionIterator();
//        return new VisibleSectionIterator();
    }

    private class VisibleSectionIterator implements Iterator<RenderSection> {
        private int nextIdx;

        private VisibleSectionIterator() {
            this.nextIdx = SectionCuller.this.sectionVisibilityBits.nextSetBit(0);
        }

        @Override
        public boolean hasNext() {
            return this.nextIdx != -1;
        }

        @Override
        public RenderSection next() {
            RenderSection section = SectionCuller.this.sectionTree.sections[this.nextIdx];
            this.nextIdx = SectionCuller.this.sectionVisibilityBits.nextSetBit(this.nextIdx + 1);
            return section;
        }
    }

    private class SortedVisibleSectionIterator implements Iterator<RenderSection> {
        private int idx;

        private SortedVisibleSectionIterator() {
        }

        @Override
        public boolean hasNext() {
            return this.idx < SectionCuller.this.stuffCount;
        }

        @Override
        public RenderSection next() {
            int trueIdx = SectionCuller.this.stuffChunks[this.idx++];
            return SectionCuller.this.sectionTree.sections[trueIdx];
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        int sectionIdx = this.sectionTree.getSectionIdx(x, y, z);

        if (sectionIdx == SectionTree.OUT_OF_BOUNDS_INDEX) {
            return false;
        }

        return this.sectionOcclusionVisibilityBits.get(sectionIdx);
    }

    public boolean isChunkInDrawDistance(int x, int z) {
        double centerX = ChunkSectionPos.getBlockCoord(x) + 8.0;
        double centerZ = ChunkSectionPos.getBlockCoord(z) + 8.0;
        Vec3d cameraPos = this.sectionTree.camera.getPos();
        double xDist = cameraPos.getX() - centerX;
        double zDist = cameraPos.getZ() - centerZ;

        return (xDist * xDist) + (zDist * zDist) <= this.squaredFogDistance;
    }

    public void markUpdateNeeded(int x, int y, int z) {
        int sectionIdx = this.sectionTree.getSectionIdx(x, y, z);
        if (sectionIdx == SectionTree.OUT_OF_BOUNDS_INDEX) {
            return;
        }
        sectionFlagData[sectionIdx] |= 1<<7;
    }

    public void unmarkUpdateNeeded(int x, int y, int z) {
        int sectionIdx = this.sectionTree.getSectionIdx(x, y, z);
        if (sectionIdx == SectionTree.OUT_OF_BOUNDS_INDEX) {
            return;
        }
        sectionFlagData[sectionIdx] &= ~(1<<7);
    }

    public void setFlagData(int x, int y, int z, int flags) {
        int sectionIdx = this.sectionTree.getSectionIdx(x, y, z);
        if (sectionIdx == SectionTree.OUT_OF_BOUNDS_INDEX) {
            return;
        }
        sectionFlagData[sectionIdx] = (byte) flags;
    }

    public void setVisibilityData(int x, int y, int z, ChunkOcclusionData data) {
        int sectionIdx = this.sectionTree.getSectionIdx(x, y, z);

        if (sectionIdx == SectionTree.OUT_OF_BOUNDS_INDEX) {
            return;
        }

        for (var from : DirectionUtil.ALL_DIRECTIONS) {
            int bits = 0;

            if (data != null) {
                for (var to : DirectionUtil.ALL_DIRECTIONS) {
                    if (data.isVisibleThrough(from, to)) {
                        bits |= 1 << to.ordinal();
                    }
                }
            }

            this.sectionDirVisibilityData[(sectionIdx * DirectionUtil.COUNT) + from.ordinal()] = (byte) bits;
        }
    }

    private int getVisibilityData(int sectionIdx, int incomingDirection) {
        return this.sectionDirVisibilityData[(sectionIdx * DirectionUtil.COUNT) + incomingDirection];
    }

    public int getAdjacentIdx(
            int sectionIdx,
            int directionId,
            int tableZStart,
            int tableXStart,
            int tableZEnd,
            int tableXEnd
    ) {
        int tableY = sectionIdx >> this.sectionTree.idxYShift;
        int tableZ = (sectionIdx >> this.sectionTree.idxZShift) & this.sectionTree.sectionWidthMask;
        int tableX = sectionIdx & this.sectionTree.sectionWidthMask;

        return switch (directionId) {
            case DirectionUtil.X_MIN -> tableX == tableXStart
                    ? SectionTree.OUT_OF_BOUNDS_INDEX
                    : (sectionIdx & ~this.sectionTree.idxXMask) | ((sectionIdx - 1) & this.sectionTree.idxXMask);
            case DirectionUtil.X_PLUS -> tableX == tableXEnd - 1
                    ? SectionTree.OUT_OF_BOUNDS_INDEX
                    : (sectionIdx & ~this.sectionTree.idxXMask) | ((sectionIdx + 1) & this.sectionTree.idxXMask);

            case DirectionUtil.Z_MIN -> tableZ == tableZStart
                    ? SectionTree.OUT_OF_BOUNDS_INDEX
                    : (sectionIdx & ~this.sectionTree.idxZMask) | ((sectionIdx - this.sectionTree.sectionWidth) & this.sectionTree.idxZMask);
            case DirectionUtil.Z_PLUS -> tableZ == tableZEnd - 1
                    ? SectionTree.OUT_OF_BOUNDS_INDEX
                    : (sectionIdx & ~this.sectionTree.idxZMask) | ((sectionIdx + this.sectionTree.sectionWidth) & this.sectionTree.idxZMask);

            case DirectionUtil.Y_MIN -> tableY == 0
                    ? SectionTree.OUT_OF_BOUNDS_INDEX
                    : (sectionIdx & ~this.sectionTree.idxYMask) | ((sectionIdx - this.sectionTree.sectionWidthSquared) & this.sectionTree.idxYMask);
            case DirectionUtil.Y_PLUS -> tableY == this.sectionTree.sectionHeight - 1
                    ? SectionTree.OUT_OF_BOUNDS_INDEX
                    : (sectionIdx & ~this.sectionTree.idxYMask) | ((sectionIdx + this.sectionTree.sectionWidthSquared) & this.sectionTree.idxYMask);
            default -> throw new IllegalStateException("Unknown direction ID: " + directionId);
        };
    }
}