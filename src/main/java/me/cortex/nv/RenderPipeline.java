package me.cortex.nv;

import me.cortex.nv.managers.SectionManager;
import me.cortex.nv.renderers.*;

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


    private final Resources resources;

    public final SectionManager sectionManager;

    private final PrimaryTerrainRasterizer terrainRasterizer;
    private final MipGenerator mipper;
    private final RegionRasterizer regionRasterizer;
    private final SectionRasterizer sectionRasterizer;
    private final TerrainCompute terrainCompute;

    public RenderPipeline() {
        resources = new Resources(-1);
        sectionManager = new SectionManager(resources.terrainMetaUploadStream,
                resources.regionMetaBuffer,
                resources.sectionMetaBuffer,
                resources.terrainGeometryBuffer);

        mipper = new MipGenerator();
        terrainRasterizer = new PrimaryTerrainRasterizer();
        regionRasterizer = new RegionRasterizer();
        sectionRasterizer = new SectionRasterizer();
        terrainCompute = new TerrainCompute();
    }

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