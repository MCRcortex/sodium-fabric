package me.cortex.nv;

import me.cortex.nv.gl.GlObject;
import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.DeviceOnlyMappedBuffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.managers.SectionManager;
import me.cortex.nv.renderers.*;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.draw.ChunkCameraContext;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexFormats;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;

import static me.cortex.nv.gl.buffers.PersistentSparseAddressableBuffer.alignUp;
import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedBufferSubData;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV;

public class RenderPipeline {
    public static final int GL_DRAW_INDIRECT_UNIFIED_NV = 0x8F40;
    public static final int GL_DRAW_INDIRECT_ADDRESS_NV = 0x8F41;
    //The rough pipeline outline is

    //Raster terrain via command lists
    //Generate a 4x4 downsampled depth buffer
    //Raster regions using representitve test
    //Raster chunk visibility meshs via command list
    //Generate command lists and delta lists
    //Raster delta list then translucency


    //Memory management is done through a large streaming buffer and gl memory copies
    //The main terrain buffer is a large gpu resident sparse buffer and holds the entire worlds data


    private static final RenderDevice device = new RenderDevice();

    public final SectionManager sectionManager;

    private final PrimaryTerrainRasterizer terrainRasterizer;
    private final MipGenerator mipper;
    private final RegionRasterizer regionRasterizer;
    private final SectionRasterizer sectionRasterizer;

    private final IDeviceMappedBuffer sceneUniform;
    private static final int SCENE_SIZE = (int) alignUp(4*4*4+4*4+4*4+8*5+3, 2);

    private final IDeviceMappedBuffer regionVisibility;
    private final IDeviceMappedBuffer sectionVisibility;
    private final IDeviceMappedBuffer terrainCommandBuffer;


    public RenderPipeline() {
        sectionManager = new SectionManager(device, 32, 24, SodiumClientMod.options().advanced.cpuRenderAheadLimit+1, TerrainVertexFormats.COMPACT.getBufferVertexFormat().stride());
        //TODO:FIXME: CLEANUP of all the types
        terrainRasterizer = new PrimaryTerrainRasterizer();
        mipper = new MipGenerator();
        regionRasterizer = new RegionRasterizer();
        sectionRasterizer = new SectionRasterizer();
        int maxRegions = sectionManager.getRegionManager().maxRegions();
        sceneUniform = device.createDeviceOnlyMappedBuffer(SCENE_SIZE+ maxRegions*2);
        regionVisibility = device.createDeviceOnlyMappedBuffer(maxRegions);
        sectionVisibility = device.createDeviceOnlyMappedBuffer(maxRegions * 256L * 2);
        terrainCommandBuffer = device.createDeviceOnlyMappedBuffer(maxRegions*8L*7);
    }


    private int prevRegionCount;
    private int frameId;
    public void renderFrame(Frustum frustum, ChunkRenderMatrices crm, ChunkCameraContext cam) {//NOTE: can use any of the command list rendering commands to basicly draw X indirects using the same shader, thus allowing for terrain to be rendered very efficently
        if (sectionManager.getRegionManager().regionCount() == 0) return;//Dont render anything if there is nothing to render

        //UPDATE UNIFORM BUFFER HERE
        int visibleRegions = 0;
        //Enqueue all the visible regions
        {
            var rm = sectionManager.getRegionManager();
            //The region data indicies is located at the end of the sceneUniform
            long addr = sectionManager.uploadStream.getUpload(sceneUniform, SCENE_SIZE, rm.regionCount()*2);
            //TODO: Sort the regions from closest to furthest from the camera
            for (int i = 0; i < rm.maxRegionIndex(); i++) {
                if (rm.isRegionVisible(frustum, i)) {
                    visibleRegions++;
                    MemoryUtil.memPutShort(addr, (short) i);
                    addr += 2;
                }
            }
        }
        {
            //TODO: maybe segment the uniform buffer into 2 parts, always updating and static where static holds pointers
            Vec3d pos = cam.getPos();
            Vector3i chunkPos = new Vector3i(cam.getSectionX(), cam.getSectionY(), cam.getSectionZ());
            Vector3f delta = new Vector3f((float) (pos.x-(chunkPos.x<<4)), (float) (pos.y-(chunkPos.y<<4)), (float) (pos.z-(chunkPos.z<<4)));

            long addr = sectionManager.uploadStream.getUpload(sceneUniform, 0, SCENE_SIZE);
            new Matrix4f(crm.projection())
                    .mul(crm.modelView())
                    .translate(delta.negate())//Translate the subchunk position//TODO: THIS
                    .getToAddress(addr);
            addr += 4*4*4;
            new Vector4i(chunkPos.x, chunkPos.y, chunkPos.z, 0).getToAddress(addr);//Chunk the camera is in//TODO: THIS
            addr += 16;
            MemoryUtil.memPutLong(addr, sceneUniform.getDeviceAddress() + SCENE_SIZE);//Put in the location of the region indexs
            addr += 8;
            MemoryUtil.memPutLong(addr, sectionManager.getRegionManager().getRegionDataAddress());
            addr += 8;
            MemoryUtil.memPutLong(addr, sectionManager.getSectionDataAddress());
            addr += 8;
            MemoryUtil.memPutLong(addr, regionVisibility.getDeviceAddress());
            addr += 8;
            MemoryUtil.memPutLong(addr, sectionVisibility.getDeviceAddress());
            addr += 8;
            MemoryUtil.memPutLong(addr, sectionVisibility.getDeviceAddress()+sectionManager.getRegionManager().maxRegions()*256L);
            addr += 8;
            MemoryUtil.memPutLong(addr, terrainCommandBuffer.getDeviceAddress());
            addr += 8;
            MemoryUtil.memPutShort(addr, (short) visibleRegions);
            addr += 2;
            MemoryUtil.memPutByte(addr, (byte) frameId++);
        }
        sectionManager.commitChanges();//Commit all uploads done to the terrain and meta data
        //if (true) return;
        int err;
        if ((err = glGetError()) != 0) {
            throw new IllegalStateException("GLERROR: "+err);
        }
        glEnableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        glEnableClientState(GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        glEnableClientState(GL_ELEMENT_ARRAY_UNIFIED_NV);
        glEnableClientState(GL_DRAW_INDIRECT_UNIFIED_NV);



        //Memory barrier from the last frame
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT);
        if (prevRegionCount != 0) {
            terrainRasterizer.raster(prevRegionCount, sceneUniform.getDeviceAddress(), SCENE_SIZE + prevRegionCount * 2, terrainCommandBuffer);
        }

        mipper.mip();

        //NOTE: For GL_REPRESENTATIVE_FRAGMENT_TEST_NV to work, depth testing must be disabled, or depthMask = false
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glColorMask(false, false, false, false);
        glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        regionRasterizer.raster(visibleRegions, sceneUniform.getDeviceAddress(), SCENE_SIZE + visibleRegions*2);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        sectionRasterizer.raster(visibleRegions, sceneUniform.getDeviceAddress(), SCENE_SIZE + visibleRegions*2);
        glDisable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glDepthMask(true);
        glColorMask(true, true, true, true);

        //glDisable(GL_DEPTH_TEST);

        glDisableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        glDisableClientState(GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        glDisableClientState(GL_ELEMENT_ARRAY_UNIFIED_NV);
        glDisableClientState(GL_DRAW_INDIRECT_UNIFIED_NV);
        if ((err = glGetError()) != 0) {
            throw new IllegalStateException("GLERROR: "+err);
        }
        prevRegionCount = visibleRegions;
    }

    public void delete() {
        sectionManager.delete();
        sceneUniform.delete();
        regionVisibility.delete();
        sectionVisibility.delete();
        terrainCommandBuffer.delete();
        //TODO: Delete rest of the render passes
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