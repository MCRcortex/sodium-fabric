package me.cortex.nv.managers;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.util.BufferArena;
import me.cortex.nv.util.UploadingBufferStream;
import me.cortex.nv.gl.buffers.Buffer;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;

public class SectionManager {
    public static final int SECTION_SIZE = 32;

    //Sections should be grouped and batched into sizes of the count of sections in a region
    private final RegionManager regionManager;

    //TODO: maybe replace with a int[] using bit masking thing
    private final Long2IntOpenHashMap sectionOffset = new Long2IntOpenHashMap();

    private final Long2IntOpenHashMap terrainDataLocation = new Long2IntOpenHashMap();

    public final UploadingBufferStream uploadStream;

    private final IDeviceMappedBuffer sectionBuffer;
    public final BufferArena terrainAreana;

    private final RenderDevice device;

    private final int formatSize;
    public SectionManager(RenderDevice device, int rd, int height, int frames, int quadVertexSize) {
        this.uploadStream = new UploadingBufferStream(device, frames, 160000000);
        int widthSquared = (rd*2+1)*(rd*2+1);

        this.formatSize = quadVertexSize;
        this.sectionBuffer = device.createDeviceOnlyMappedBuffer((long) widthSquared * height * SECTION_SIZE);
        this.terrainAreana = new BufferArena(device, quadVertexSize);
        this.sectionOffset.defaultReturnValue(-1);
        this.regionManager = new RegionManager(device, (int) Math.ceil(((double) widthSquared/(8*8))*((double) height/4)+1));
        this.device = device;
    }

    private long getSectionKey(int x, int y, int z) {
        return ChunkSectionPos.asLong(x,y,z);
    }

    //FIXME: causes extreame stuttering
    public void uploadSetSection(ChunkBuildResult result) {
        if (result.meshes.isEmpty() || result.data == null) {
            deleteSection(result.render);
            return;
        }
        RenderSection section = result.render;
        long key = getSectionKey(section.getChunkX(), section.getChunkY(), section.getChunkZ());
        int sectionIdx = sectionOffset.computeIfAbsent(//Get or fetch the section meta index
                key,
                a->regionManager.createSectionIndex(uploadStream, section.getChunkX(), section.getChunkY(), section.getChunkZ())
        );



        if (terrainDataLocation.containsKey(key)) {
            terrainAreana.free(terrainDataLocation.get(key));
        }

        int geoSize = result.meshes.values().stream().mapToInt(a->a.getVertexData().getLength()).sum();
        int addr = terrainAreana.allocQuads((geoSize/formatSize)/4);
        terrainDataLocation.put(key, addr);
        long geoUpload = terrainAreana.upload(uploadStream, addr);
        //Upload all the geometry grouped by face
        short[] offsets = new short[8];
        short offset = 0;
        var solid  = result.meshes.get(DefaultTerrainRenderPasses.SOLID);
        var cutout = result.meshes.get(DefaultTerrainRenderPasses.CUTOUT);
        //Do all but translucent
        for (int i = 0; i < 7; i++) {
            if (solid != null) {
                //TODO Optimize from .values()
                var segment = solid.getParts().get(ModelQuadFacing.values()[i]);
                if (segment != null) {
                    MemoryUtil.memCopy(MemoryUtil.memAddress(solid.getVertexData().getDirectBuffer()) + (long) segment.vertexStart() * formatSize,
                            geoUpload + offset * 4L * formatSize,
                            (long) segment.vertexCount() * formatSize);
                    offset += segment.vertexCount() / 4;
                }
            }
            if (cutout != null) {
                var segment = cutout.getParts().get(ModelQuadFacing.values()[i]);
                if (segment != null) {
                    MemoryUtil.memCopy(MemoryUtil.memAddress(cutout.getVertexData().getDirectBuffer()) + (long) segment.vertexStart() * formatSize,
                            geoUpload + offset * 4L * formatSize,
                            (long) segment.vertexCount() * formatSize);
                    offset += segment.vertexCount() / 4;
                }
            }
            offsets[i] = offset;
        }
        //Do translucent
        short translucent = offsets[6];
        offsets[7] = translucent;




        long segment = uploadStream.getUpload(sectionBuffer, (long) sectionIdx * SECTION_SIZE, SECTION_SIZE);
        int mx = (int) (result.data.getBounds().minX - result.render.getOriginX());//Integer.numberOfTrailingZeros(result.data().bounds.rx);
        int my = (int) (result.data.getBounds().minY - result.render.getOriginY());//Integer.numberOfTrailingZeros(result.data().bounds.ry);
        int mz = (int) (result.data.getBounds().minZ - result.render.getOriginZ());//Integer.numberOfTrailingZeros(result.data().bounds.rz);
        int sx = (int)(result.data.getBounds().maxX-result.data.getBounds().minX-1);//32-Integer.numberOfLeadingZeros(result.data().bounds.rx)-mx-1;
        int sy = (int)(result.data.getBounds().maxY-result.data.getBounds().minY-1);//32-Integer.numberOfLeadingZeros(result.data().bounds.ry)-my-1;
        int sz = (int)(result.data.getBounds().maxZ-result.data.getBounds().minZ-1);//32-Integer.numberOfLeadingZeros(result.data().bounds.rz)-mz-1;

        mx = Math.min(15, mx);
        my = Math.min(15, my);
        mz = Math.min(15, mz);
        sx = Math.max(0, sx);
        sy = Math.max(0, sy);
        sz = Math.max(0, sz);

        int px = section.getChunkX()<<8 | sx<<4 | mx;
        int py = section.getChunkY()<<24 | sy<<4 | my;
        int pz = section.getChunkZ()<<8 | sz<<4 | mz;
        int pw = addr;
        new Vector4i(px, py, pz, pw).getToAddress(segment);
        segment += 4*4;

        //Write the geometry offsets, packed into ints
        for (int i = 0; i < 4; i++) {
            int geo = Short.toUnsignedInt(offsets[i*2])|(Short.toUnsignedInt(offsets[i*2+1])<<16);
            MemoryUtil.memPutInt(segment, geo);
            segment += 4;
        }
    }

    public void deleteSection(RenderSection section) {
        long key = getSectionKey(section.getChunkX(), section.getChunkY(), section.getChunkZ());
        int sectionIdx = sectionOffset.remove(key);
        if (sectionIdx != -1) {
            terrainAreana.free(terrainDataLocation.remove(key));
            regionManager.removeSectionIndex(uploadStream, sectionIdx);
            //Clear the segment
            long segment = uploadStream.getUpload(sectionBuffer, (long) sectionIdx * SECTION_SIZE, SECTION_SIZE);
            MemoryUtil.memSet(segment, 0, SECTION_SIZE);
        }
    }

    public void commitChanges() {
        uploadStream.commit();
    }

    public void delete() {
        uploadStream.delete();
        sectionBuffer.delete();
        terrainAreana.delete();
        regionManager.delete();
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public long getSectionDataAddress() {
        return sectionBuffer.getDeviceAddress();
    }
}




