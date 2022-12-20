package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.util.MathUtil;
import me.jellysquid.mods.sodium.client.util.collections.BitArray;
import me.jellysquid.mods.sodium.client.util.frustum.Frustum;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class SectionCulling {
    public final SectionState state;
    public final SortedSectionList sortedSectionList;
    private final BitArray sectionVisibilityBitsPass1;
    private final BitArray sectionVisibilityBitsPass2;
    private final short[] sectionTraversalData;
    private final int[] sortedSections;

    private final Vector3d camera = new Vector3d();


    public SectionCulling(SectionState sectionState) {
        state = sectionState;
        sortedSectionList = new SortedSectionList(sectionState.sectionTableSize);//TODO: OPTIMIZE THE SIZE PASSED IT/possibly make it dynamic/automatic
        sectionVisibilityBitsPass1 = new BitArray(sectionState.sectionTableSize);
        sectionVisibilityBitsPass2 = new BitArray(sectionState.sectionTableSize);
        sectionTraversalData = new short[sectionState.sectionTableSize];
        sortedSections = new int[sectionState.sectionTableSize];
    }

    public void calculateVisibleSections(
            Frustum frustum,
            boolean useOcclusionCulling,
            Vector3d camera
    ) {
        this.camera.set(camera);
        this.sortedSectionList.reset();
        this.sectionVisibilityBitsPass1.fill(false);
        this.sectionVisibilityBitsPass2.fill(false);

        if (this.state.getLoadedSections() != 0) {
            // Start with corner section of the fog distance.
            // To do this, we have to reverse the function to check if a chunk is in bounds by doing pythagorean's
            // theorem, then doing some math.
            double cameraX = this.camera.x;
            double cameraZ = this.camera.z;
            double sectionCenterDistX = MathUtil.floorMod(cameraX, 16.0) - 8.0;
            double sectionCenterDistZ = MathUtil.floorMod(cameraZ, 16.0) - 8.0;
            double distX = Math.sqrt(this.state.squaredFogDistance - (sectionCenterDistZ * sectionCenterDistZ));
            double distZ = Math.sqrt(this.state.squaredFogDistance - (sectionCenterDistX * sectionCenterDistX));

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
            this.occlusionCullAndFillLists(
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
        int nodeSectionLength = 1 << this.state.maxDepth;

        int yIdxIncrement = nodeSectionLength * this.state.sectionWidthSquared;
        int zIdxIncrement = nodeSectionLength * this.state.sectionWidth;
        int xIdxIncrement = nodeSectionLength;

        // Z and X table indices *are* restricted to the table.
        final int tableZStart = sectionZStart & this.state.sectionWidthMask;
        final int tableXStart = sectionXStart & this.state.sectionWidthMask;

        final int zIdxStart = tableZStart * this.state.sectionWidth;
        final int xIdxStart = tableXStart;
        final int zIdxWrap = this.state.sectionWidthSquared;
        final int xIdxWrap = this.state.sectionWidth;

        int sectionZSplit = Math.min(sectionZStart + this.state.sectionWidth - tableZStart, sectionZEnd);
        int sectionXSplit = Math.min(sectionXStart + this.state.sectionWidth - tableXStart, sectionXEnd);

        for (int sectionY = this.state.sectionHeightMin, yIdxOffset = 0; sectionY < this.state.sectionHeightMax; sectionY += nodeSectionLength, yIdxOffset += yIdxIncrement) {
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
                            this.state.maxDepth,
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

        final int sectionYEnd = Math.min(sectionY + nodeSectionLength, this.state.sectionHeightMax);
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
                for (int newSectionY = sectionY, yIdxOffset = 0; newSectionY < sectionYEnd; newSectionY++, yIdxOffset += this.state.sectionWidthSquared) {
                    for (int newSectionZ = sectionZ, zIdxOffset = 0; newSectionZ < sectionZEnd; newSectionZ++, zIdxOffset += this.state.sectionWidth) {
                        this.sectionVisibilityBitsPass1.copy(
                                this.state.sectionExistenceBits,
                                sectionIdx + yIdxOffset + zIdxOffset,
                                sectionIdx + yIdxOffset + zIdxOffset + sectionXEnd - sectionX
                        );
                    }
                }
            } else {
                int childDepth = depth - 1;
                int childSectionLength = nodeSectionLength >> 1;

                int yIdxIncrement = childSectionLength * this.state.sectionWidthSquared;
                int zIdxIncrement = childSectionLength * this.state.sectionWidth;

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
        if (!this.state.sectionExistenceBits.get(sectionIdx)) {
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
            this.sectionVisibilityBitsPass1.set(sectionIdx);
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
        int zIdxIncrement = this.state.sectionWidth;
        int xIdxIncrement = 1;

        // Table indices *are* restricted to the table.
        final int tableZStart = sectionZStart & this.state.sectionWidthMask;
        final int tableXStart = sectionXStart & this.state.sectionWidthMask;

        final int zIdxStart = tableZStart * this.state.sectionWidth;
        final int xIdxStart = tableXStart;
        final int zIdxWrap = this.state.sectionWidthSquared;
        final int xIdxWrap = this.state.sectionWidth;

        int sectionZSplit = Math.min(sectionZStart + this.state.sectionWidth - tableZStart, sectionZEnd);
        int sectionXSplit = Math.min(sectionXStart + this.state.sectionWidth - tableXStart, sectionXEnd);

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
                    int yIdxIncrement = this.state.sectionWidthSquared;
                    int yIdxOffset = 0;
                    for (int sectionY = this.state.sectionHeightMin; sectionY < this.state.sectionHeightMax; sectionY++, yIdxOffset += yIdxIncrement) {
                        this.sectionVisibilityBitsPass1.unset(yIdxOffset + zIdxOffset + xIdxOffset);
                    }
                }

                sectionX++;
                xIdxOffset += xIdxIncrement;
            }

            sectionZ++;
            zIdxOffset += zIdxIncrement;
        }
    }

    public boolean isChunkInDrawDistance(int x, int z) {
        double centerX = ChunkSectionPos.getBlockCoord(x) + 8.0;
        double centerZ = ChunkSectionPos.getBlockCoord(z) + 8.0;
        double xDist = camera.x - centerX;
        double zDist = camera.z - centerZ;

        return (xDist * xDist) + (zDist * zDist) <= this.state.squaredFogDistance;
    }

    private void occlusionCullAndFillLists(
            boolean useOcclusion,
            final int sectionZStart,
            final int sectionXStart,
            final int sectionZEnd,
            final int sectionXEnd
    ) {
        int visibleChunksCount = 1;
        int visibleChunksQueue = 0;

        short traversalOverride = (short) (useOcclusion ? 0 : -1);
        int startSectionIdx = this.state.getSectionIdx(
                ((int) camera.x)>>4,
                ((int) camera.y)>>4,
                ((int) camera.z)>>4
        );

        boolean fallback = startSectionIdx == SectionState.NULL_INDEX;

        if (fallback) {
            //TODO: THIS
        } else {
            this.sortedSections[0] = startSectionIdx;
            this.sectionTraversalData[startSectionIdx] = -1; // All outbound directions
        }

        // TODO:FIXME: FALLBACK
        while (visibleChunksQueue != visibleChunksCount) {
            int sectionIdx = this.sortedSections[visibleChunksQueue++];

            byte flags = this.state.getSectionFlagData(sectionIdx);
            if (flags != 0) {
                sortedSectionList.enqueueSection(flags, sectionIdx);
            }

            this.sectionVisibilityBitsPass1.unset(sectionIdx); // Performance hack, means it ignores sections if its already visited them
            this.sectionVisibilityBitsPass2.set(sectionIdx);
            short traversalData = (short) (this.sectionTraversalData[sectionIdx] | traversalOverride);
            this.sectionTraversalData[sectionIdx] = 0; // Reset the traversalData, meaning don't need to fill the array
            traversalData &= ((traversalData >> 8) & 0xFF) | 0xFF00; // Apply inbound chunk filter to prevent backwards traversal

            for (int outgoingDir = 0; outgoingDir < DirectionUtil.COUNT; outgoingDir++) {
                if ((traversalData & (1 << outgoingDir)) == 0) {
                    continue;
                }

                //TODO: check that the direction is facing away from the camera (dot product less positive or something)
                int neighborSectionIdx = this.getAdjacentIdx(sectionIdx, outgoingDir, sectionZStart, sectionXStart, sectionZEnd, sectionXEnd);
                if (neighborSectionIdx == SectionState.NULL_INDEX || !this.sectionVisibilityBitsPass1.get(neighborSectionIdx)) {
                    continue;
                }

                short neighborTraversalData = this.sectionTraversalData[neighborSectionIdx];

                if (neighborTraversalData == 0) {
                    this.sortedSections[visibleChunksCount++] = neighborSectionIdx;
                    neighborTraversalData |= (short) (1 << 15) | (traversalData & 0xFF00);
                }

                int inboundDir = DirectionUtil.getOppositeId(outgoingDir);
                neighborTraversalData |= this.state.getVisibilityData(neighborSectionIdx, inboundDir);
                neighborTraversalData &= ~(1 << (8 + inboundDir)); // Un mark incoming direction
                this.sectionTraversalData[neighborSectionIdx] = neighborTraversalData;
            }
        }
        int x = 0;
        x+=1;
    }

    public int getAdjacentIdx(
            int sectionIdx,
            int directionId,
            int tableZStart,
            int tableXStart,
            int tableZEnd,
            int tableXEnd
    ) {
        int tableY = sectionIdx >> this.state.idxYShift;
        int tableZ = (sectionIdx >> this.state.idxZShift) & this.state.sectionWidthMask;
        int tableX = sectionIdx & this.state.sectionWidthMask;

        // do some terrible bit hacks to decrement and increment the index in the correct direction
        return switch (directionId) {
            case DirectionUtil.X_MIN -> tableX == tableXStart
                    ? SectionState.NULL_INDEX
                    : (sectionIdx & ~this.state.idxXMask) | ((sectionIdx - 1) & this.state.idxXMask);
            case DirectionUtil.X_PLUS -> tableX == tableXEnd - 1
                    ? SectionState.NULL_INDEX
                    : (sectionIdx & ~this.state.idxXMask) | ((sectionIdx + 1) & this.state.idxXMask);
            case DirectionUtil.Z_MIN -> tableZ == tableZStart
                    ? SectionState.NULL_INDEX
                    : (sectionIdx & ~this.state.idxZMask) | ((sectionIdx - this.state.sectionWidth) & this.state.idxZMask);
            case DirectionUtil.Z_PLUS -> tableZ == tableZEnd - 1
                    ? SectionState.NULL_INDEX
                    : (sectionIdx & ~this.state.idxZMask) | ((sectionIdx + this.state.sectionWidth) & this.state.idxZMask);

            case DirectionUtil.Y_MIN -> tableY == 0
                    ? SectionState.NULL_INDEX
                    : (sectionIdx & ~this.state.idxYMask) | ((sectionIdx - this.state.sectionWidthSquared) & this.state.idxYMask);
            case DirectionUtil.Y_PLUS -> tableY == this.state.sectionHeight - 1
                    ? SectionState.NULL_INDEX
                    : (sectionIdx & ~this.state.idxYMask) | ((sectionIdx + this.state.sectionWidthSquared) & this.state.idxYMask);
            default -> throw new IllegalStateException("Unknown direction ID: " + directionId);
        };
    }

}
