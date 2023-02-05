package me.cortex.nv.managers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.nv.gl.BatchedUploadStreamer;
import me.cortex.nv.structs.SectionMetaStruct;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.compile.tasks.TerrainBuildResult;

public class SectionManager {
    private static final int SIZE_OF_CLUMP = SectionMetaStruct.SIZE * RegionManager.SECTION_COUNT;

    //Sections should be grouped and batched into sizes of the count of sections in a region
    RegionManager regionManager = new RegionManager();

    private final Int2IntOpenHashMap sectionOffset = new Int2IntOpenHashMap();

    BatchedUploadStreamer uploadStream = new BatchedUploadStreamer();

    private int getSectionKey(int x, int y, int z) {

    }
    public void uploadSetSection(TerrainBuildResult result) {
        //TODO: will probably need to add a refactor method, so that sections are clumped closer together
        if (result.render() == null || result.geometry() == null || result.geometry().vertices() == null || result.data() == null) {
            return;//TODO: should probably delete section
        }
        RenderSection section = result.render();
        int sectionIdx = sectionOffset.computeIfAbsent(//Get or fetch the section meta index
                getSectionKey(section.getSectionX(), section.getSectionY(), section.getSectionZ()),
                a->regionManager.createSectionIndex(uploadStream, section.getSectionX(), section.getSectionY(), section.getSectionZ())
        );
        //Todo need to generate and upload section meta
    }

    public void deleteSection(RenderSection section) {
        int sectionIdx = sectionOffset.remove(getSectionKey(section.getSectionX(), section.getSectionY(), section.getSectionZ()));
        regionManager.removeSectionIndex(uploadStream, section.getSectionX(), section.getSectionY(), section.getSectionZ(), sectionIdx);
        //TODO: need to update section meta
    }

    public void commitChanges() {
        uploadStream.commit();
    }

}
