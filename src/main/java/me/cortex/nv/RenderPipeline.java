package me.cortex.nv;

import me.cortex.nv.managers.SectionManager;
import me.cortex.nv.renderers.*;

import static org.lwjgl.opengl.GL42C.glMemoryBarrier;

public class RenderPipeline {
    //The rough pipeline outline is

    //Raster terrain via command lists
    //Generate a 4x4 downsampled depth buffer
    //Raster regions using representitve test
    //Raster chunk visibility meshs via command list
    //Generate command lists and delta lists
    //Raster delta list then translucency


    //Memory management is done through a large streaming buffer and gl memory copies
    //The main terrain buffer is a large gpu resident sparse buffer and holds the entire worlds data


    public final SectionManager sectionManager = new SectionManager();

    private final PrimaryTerrainRasterizer terrainRasterizer = new PrimaryTerrainRasterizer();
    private final MipGenerator mipper = new MipGenerator();
    private final RegionRasterizer regionRasterizer = new RegionRasterizer();
    private final SectionRasterizer sectionRasterizer = new SectionRasterizer();
    private final TerrainCompute terrainCompute = new TerrainCompute();



    private void renderFrame() {//NOTE: can use any of the command list rendering commands to basicly draw X indirects using the same shader, thus allowing for terrain to be rendered very efficently
        sectionManager.commitChanges();//Commit all uploads done to the terrain and meta data

    }
}

//V1
//rasterTerrain();
//generateMip();
//rasterRegions();
//generateSectionRaster();
//rasterSections();
//generateTerrain();
//rasterTerrainDelta();
//rasterTranslucency();



//V2
//rasterTerrain();
//generateMip();
//rasterRegions();
//rasterSections();//This is done via a task mesh which should mean its ultrafast and inline
//generateTerrain();
//rasterTerrainDelta();
//rasterTranslucency();

//Note:can basicly merge the first 4 into a DrawCommandsStatesAddressNV