package net.caffeinemc.sodium.render.chunk.region;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.List;
import java.util.Set;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.PendingUpload;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs.RegionMeta;
import net.caffeinemc.sodium.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

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

    private final Set<RenderSection> sections = new ObjectOpenHashSet<>(REGION_SIZE);

    private final ArenaBuffer vertexBuffer;
    private final IVertexBufferProvider vbm;
    private final int id;
    public RegionMeta meta;

    public int renderDataIndex;
    public int lastFrameId;

    public RenderRegion(RenderDevice device, IVertexBufferProvider provider, int id) {
        this.vertexBuffer = provider.provide();
        vbm = provider;
        this.id = id;
    }

    /**
     * Uploads the given pending uploads to the buffers, adding sections to this region as necessary.
     */
    public void submitUploads(List<PendingUpload> pendingUploads, int frameIndex) {
        this.vertexBuffer.upload(pendingUploads, frameIndex);

        // Collect the upload results
        for (PendingUpload pendingUpload : pendingUploads) {
            long bufferSegment = pendingUpload.bufferSegmentResult.get();
            RenderSection section = pendingUpload.section;

            section.setGeometry(this, bufferSegment);
            this.sections.add(section);
        }
    }

    /**
     * Removes the given section from the region, and frees the vertex buffer segment associated with the section.
     */
    public void removeSection(RenderSection section) {
        this.vertexBuffer.free(section.getUploadedGeometrySegment());
        this.sections.remove(section);
    }

    public void delete() {
        vbm.remove(vertexBuffer);
    }

    public boolean isEmpty() {
        return this.vertexBuffer.isEmpty();
    }

    public long getDeviceUsedMemory() {
        return this.vertexBuffer.getDeviceUsedMemory();
    }

    public long getDeviceAllocatedMemory() {
        return this.vertexBuffer.getDeviceAllocatedMemory();
    }

    public static long getRegionCoord(int chunkX, int chunkY, int chunkZ) {
        return ChunkSectionPos.asLong(chunkX >> REGION_WIDTH_SH, chunkY >> REGION_HEIGHT_SH, chunkZ >> REGION_LENGTH_SH);
    }

    public Set<RenderSection> getSections() {
        return this.sections;
    }

    public ArenaBuffer getVertexBuffer() {
        return this.vertexBuffer;
    }

    public int getId() {
        return this.id;
    }
}
