package me.cortex.nv.managers;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.nv.util.BufferArena;
import me.cortex.nv.util.UploadingBufferStream;
import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.structs.SectionMetaStruct;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;
import net.minecraft.util.math.ChunkSectionPos;

public class SectionManager {
    //Sections should be grouped and batched into sizes of the count of sections in a region
    private final RegionManager regionManager;

    private final Long2IntOpenHashMap sectionOffset = new Long2IntOpenHashMap();

    private final UploadingBufferStream uploadStream;

    private final Buffer sectionBuffer;

    private final BufferArena terrainBuffer;

    public SectionManager(UploadingBufferStream uploadStream, Buffer regionBuffer, Buffer sectionBuffer, BufferArena terrainBuffer) {
        this.regionManager = new RegionManager(regionBuffer);
        this.uploadStream = uploadStream;
        this.sectionBuffer = sectionBuffer;
        this.terrainBuffer = terrainBuffer;
        this.sectionOffset.defaultReturnValue(-1);
    }

    private long getSectionKey(int x, int y, int z) {
        return ChunkSectionPos.asLong(x,y,z);
    }

    public void uploadSetSection(TerrainBuildResult result) {
        if (result.geometry() == null || result.geometry().vertices() == null || result.data() == null) {
            deleteSection(result.render());
            return;
        }
        RenderSection section = result.render();
        int sectionIdx = sectionOffset.computeIfAbsent(//Get or fetch the section meta index
                getSectionKey(section.getSectionX(), section.getSectionY(), section.getSectionZ()),
                a->regionManager.createSectionIndex(uploadStream, section.getSectionX(), section.getSectionY(), section.getSectionZ())
        );



        //ByteBuffer geometryUpload = uploadStream.getUpload(result.geometry().vertices().buffer().getLength());







        //Todo need to generate and upload section meta
        updateSection(sectionIdx);
    }

    public void deleteSection(RenderSection section) {
        int sectionIdx = sectionOffset.remove(getSectionKey(section.getSectionX(), section.getSectionY(), section.getSectionZ()));
        if (sectionIdx != -1) {
            regionManager.removeSectionIndex(uploadStream, sectionIdx);
            //TODO: need to clear section meta
            updateSection(sectionIdx);
        }
    }

    private void updateSection(int sectionIdx) {
        long segment = uploadStream.getUpload(sectionBuffer, sectionIdx * SectionMetaStruct.SIZE, SectionMetaStruct.SIZE);
        //TODO: UPDATE segment
    }

    public void commitChanges() {
        uploadStream.commit();
    }

}
