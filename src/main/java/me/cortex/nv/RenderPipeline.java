package me.cortex.nv;

import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.images.DepthOnlyFrameBuffer;
import me.cortex.nv.managers.SectionManager;
import me.cortex.nv.renderers.*;
import me.cortex.nv.util.UploadingBufferStream;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import me.jellysquid.mods.sodium.client.util.frustum.Frustum;
import net.minecraft.client.MinecraftClient;
import org.joml.*;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.system.MemoryUtil;

import java.lang.Math;

import static me.cortex.nv.gl.buffers.PersistentSparseAddressableBuffer.alignUp;
import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedBufferSubData;
import static org.lwjgl.opengl.ARBSampleShading.glMinSampleShadingARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.NVConservativeRaster.GL_CONSERVATIVE_RASTERIZATION_NV;
import static org.lwjgl.opengl.NVConservativeRaster.glSubpixelPrecisionBiasNV;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.*;

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
    public static boolean cancleClear = false;

    public final SectionManager sectionManager;

    private final PrimaryTerrainRasterizer terrainRasterizer;
    private final MipGenerator mipper;
    private final RegionRasterizer regionRasterizer;
    private final SectionRasterizer sectionRasterizer;

    private final IDeviceMappedBuffer sceneUniform;
    private static final int SCENE_SIZE = (int) alignUp(4*4*4+4*4+4*4+8*6+3, 2);

    private final IDeviceMappedBuffer regionVisibility;
    private final IDeviceMappedBuffer sectionVisibility;
    private final IDeviceMappedBuffer terrainCommandBuffer;

    private DepthOnlyFrameBuffer mippedDepthBuffer;

    public RenderPipeline() {
        sectionManager = new SectionManager(device, 32, 24, SodiumClientMod.options().advanced.cpuRenderAheadLimit+1, CompactChunkVertex.STRIDE);
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


    public void onResize(int newWidth, int newHeight) {
        if (mippedDepthBuffer != null) {
            mippedDepthBuffer.delete();
        }
        mippedDepthBuffer = new DepthOnlyFrameBuffer((int)Math.ceil((float)newWidth/4), (int)Math.ceil((float)newHeight/4));
        //mippedDepthBuffer = new DepthOnlyFrameBuffer(newWidth, newHeight);
    }

    private int prevRegionCount;
    private int frameId;


    long cterrainTime;
    long cregionTime;
    long csectionTime;
    long cotherFrame;

    long otherFrameRecord = System.nanoTime();
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
            Vector3i chunkPos = new Vector3i(cam.blockX>>4, cam.blockY>>4, cam.blockZ>>4);
            Vector3f delta = new Vector3f((cam.blockX-(chunkPos.x<<4))+cam.deltaX, (cam.blockY-(chunkPos.y<<4))+cam.deltaY, (cam.blockZ-(chunkPos.z<<4))+cam.deltaZ);

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
            MemoryUtil.memPutLong(addr, sectionManager.terrainAreana.buffer.getDeviceAddress());
            addr += 8;
            MemoryUtil.memPutShort(addr, (short) visibleRegions);
            addr += 2;
            MemoryUtil.memPutByte(addr, (byte) (frameId++));
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
        //Bind the uniform, it doesnt get wiped between shader changes
        glBufferAddressRangeNV(GL_UNIFORM_BUFFER_ADDRESS_NV, 0, sceneUniform.getDeviceAddress(), SCENE_SIZE + visibleRegions*2L);

        glFinish();
        long otherFrame = System.nanoTime() - otherFrameRecord;
        long t = System.nanoTime();
        //Memory barrier from the last frame
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_COMMAND_BARRIER_BIT);
        if (prevRegionCount != 0) {
            glEnable(GL_DEPTH_TEST);
            //glEnable(GL_CONSERVATIVE_RASTERIZATION_NV);
            //glEnable(GL_SAMPLE_SHADING);
            //glMinSampleShadingARB(0.0f);
            //glDisable(GL_CULL_FACE);
            terrainRasterizer.raster(prevRegionCount, terrainCommandBuffer);
            //glEnable(GL_CULL_FACE);
        }
        glFinish();
        long terrainTime = System.nanoTime() - t;
        t = System.nanoTime();


        /*
        var pfbo = MinecraftClient.getInstance().getFramebuffer();
        if (false) {
            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT);
            mippedDepthBuffer.bind(true);
            mipper.mip(pfbo.getDepthAttachment(), mippedDepthBuffer);
            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT);
        }*/



        //NOTE: For GL_REPRESENTATIVE_FRAGMENT_TEST_NV to work, depth testing must be disabled, or depthMask = false
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glColorMask(false, false, false, false);
        glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        regionRasterizer.raster(visibleRegions);
        glFinish();
        long regionTime = System.nanoTime() - t;
        t = System.nanoTime();
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        sectionRasterizer.raster(visibleRegions);
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


        glFinish();
        long sectionTime = System.nanoTime() - t;
        otherFrameRecord = System.nanoTime();
        cterrainTime += terrainTime;
        cregionTime += regionTime;
        csectionTime += sectionTime;
        cotherFrame += otherFrame;
        if (frameId%1000 == 0) {
            System.out.println("Other frame: " + ((cotherFrame/frameId)/1000)+ " Terrain: " + ((cterrainTime/frameId)/1000) + " Region: " + ((cregionTime/frameId)/1000) + " Section: " + ((csectionTime/frameId)/1000));
        }
        if (frameId%10000==0) {
            frameId = 0;
            cterrainTime = 0;
            cregionTime = 0;
            csectionTime = 0;
            cotherFrame = 0;
        }

        //pfbo.beginWrite(true);
        //TODO: FIXME: THIS FEELS ILLEGAL
        UploadingBufferStream.TickAllUploadingStreams();
    }

    public void delete() {
        sectionManager.delete();
        sceneUniform.delete();
        regionVisibility.delete();
        sectionVisibility.delete();
        terrainCommandBuffer.delete();
        mippedDepthBuffer.delete();
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