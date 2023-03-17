package me.cortex.nv.managers;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.util.BufferArena;
import me.cortex.nv.util.UploadingBufferStream;
import me.cortex.nv.gl.buffers.Buffer;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
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
    private final BufferArena terrainAreana;

    private final RenderDevice device;
    public SectionManager(RenderDevice device, int rd, int height, int frames, int quadVertexSize) {
        this.uploadStream = new UploadingBufferStream(device, frames, 160000000);
        int widthSquared = (rd*2+1)*(rd*2+1);

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
    public void uploadSetSection(TerrainBuildResult result) {
        if (result.geometry() == null || result.geometry().vertices() == null || result.data() == null) {
            deleteSection(result.render());
            return;
        }
        RenderSection section = result.render();
        long key = getSectionKey(section.getSectionX(), section.getSectionY(), section.getSectionZ());
        int sectionIdx = sectionOffset.computeIfAbsent(//Get or fetch the section meta index
                key,
                a->regionManager.createSectionIndex(uploadStream, section.getSectionX(), section.getSectionY(), section.getSectionZ())
        );


        if (terrainDataLocation.containsKey(key)) {
            terrainAreana.free(terrainDataLocation.get(key));
        }

        int geoSize = result.geometry().vertices().buffer().getLength();
        int stride = result.geometry().vertices().format().stride();
        int addr = terrainAreana.allocQuads((geoSize/stride)/4);
        terrainDataLocation.put(key, addr);
        long geoUpload = terrainAreana.upload(uploadStream, addr);
        //Upload all the geometry grouped by face
        short[] offsets = new short[8];
        short offset = 0;
        //Do all but translucent
        for (int i = 0; i < 7; i++) {
            for (var pass : SodiumWorldRenderer.instance().renderPassManager.getAllRenderPasses()) {
                if (pass.isTranslucent())
                    continue;
                if (result.geometry().models()[pass.getId()] == null)
                    continue;
                if (result.geometry().models()[pass.getId()].getModelPartSegments()[i] == BufferSegment.INVALID)
                    continue;
                long geo = result.geometry().models()[pass.getId()].getModelPartSegments()[i];
                MemoryUtil.memCopy(result.geometry().vertices().buffer().getAddress() + (long) BufferSegment.getOffset(geo) * stride,
                        geoUpload + offset * 4L * stride,
                        (long) BufferSegment.getLength(geo) * stride);
                offset += BufferSegment.getLength(geo)/4;
            }
            offsets[i] = offset;
        }
        //Do translucent
        short translucent = offsets[7];
        offsets[7] = translucent;




        long segment = uploadStream.getUpload(sectionBuffer, (long) sectionIdx * SECTION_SIZE, SECTION_SIZE);
        int mx = (int)(result.data().bounds.x1+0.5) - (section.getSectionX()<<4);
        int my = (int)(result.data().bounds.y1+0.5) - (section.getSectionY()<<4);
        int mz = (int)(result.data().bounds.z1+0.5) - (section.getSectionZ()<<4);
        int sx = (int)(result.data().bounds.x2-result.data().bounds.x1)-1;
        int sy = (int)(result.data().bounds.y2-result.data().bounds.y1)-1;
        int sz = (int)(result.data().bounds.z2-result.data().bounds.z1)-1;

        int px = section.getSectionX()<<8 | sx<<4 | mx;
        int py = section.getSectionY()<<24 | sy<<4 | my;
        int pz = section.getSectionZ()<<8 | sz<<4 | mz;
        int pw = addr;
        new Vector4i(px, py, pz, pw).getToAddress(segment);
        segment += 4*4;

        //Write the geometry offsets, packed into ints
        for (int i = 0; i < 4; i++) {
            int geo = Short.toUnsignedInt(offsets[i*2])|Short.toUnsignedInt(offsets[i*2+1]);
            MemoryUtil.memPutInt(segment, geo);
            segment += 4;
        }
    }

    public void deleteSection(RenderSection section) {
        long key = getSectionKey(section.getSectionX(), section.getSectionY(), section.getSectionZ());
        int sectionIdx = sectionOffset.remove(key);
        if (sectionIdx != -1) {
            terrainAreana.free(terrainDataLocation.remove(key));
            regionManager.removeSectionIndex(uploadStream, sectionIdx);
            //Clear the segment
            long segment = uploadStream.getUpload(sectionBuffer, (long) sectionIdx * SECTION_SIZE, SECTION_SIZE);
            MemoryUtil.memSet(segment, 0, 32);
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




