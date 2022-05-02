package me.cortex.cullmister;

import me.cortex.cullmister.commandListStuff.BindlessBuffer;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.CShader;
import me.cortex.cullmister.utils.Shader;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;
import static org.lwjgl.opengl.NVShaderBufferLoad.glUniformui64NV;

public class CullSystem {


    //Plan, dual pass system:
    //visibility test with raster and NV_representative_fragment_test, this just marks a buffer with either a vis flag
    // or current render frame
    //Then compute shader is executed which builds render commands
    //Then execute commands

    //The visibility raster command list is handled when a chunk is added and set to the nop instruction for that
    // sector when the section is freed, the raster command list is a list of GL_DRAW_ELEMENTS or something

    //NOTE: the vis flag is reset by the compute shader (if doing bool vis flag, probably faster due to mem bandwidth/using constant)

    //The compute shader generates render commands to the stream



    Shader rasterPass;
    CShader commandBuilder;

    //Need to create cube IBO
    public CullSystem() {
        loadShaders();
    }

    public void loadShaders() {
        if (rasterPass != null) {
            rasterPass.delete();
            rasterPass = null;
        }
        if (commandBuilder != null) {
            commandBuilder.delete();
            commandBuilder = null;
        }
        rasterPass = new Shader(Shader.get("assets/cullmister/cull/rasterCull.vert"), Shader.get("assets/cullmister/cull/rasterCull.frag"));
        commandBuilder = new CShader(Shader.get("assets/cullmister/cull/commandGenerator.comp"));
    }

    void prep(Region region, ChunkRenderMatrices renderMatrices, Vector3f cam) {
        //Sets region UBO buffer
        long ptr = nglMapNamedBufferRange(region.draw.UBO.id, 0, region.draw.UBO.size, GL_MAP_WRITE_BIT|GL_MAP_UNSYNCHRONIZED_BIT);
        new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView())
                .translate(new Vector3f(cam).negate())
                .translate(region.pos.x()<<Region.WIDTH_BITS, region.pos.y()*Region.HEIGHT, region.pos.z()<<Region.WIDTH_BITS)
                .getToAddress(ptr);
        cam.getToAddress(ptr+4*4*4);
        //Reset all the atomic offset counters
        MemoryUtil.memPutInt(ptr+4*4*4+4*3,0);//Instance data count
        for (int i = 0; i < 4; i++) {
            MemoryUtil.memPutInt(ptr + 4 * 4 * 4 + 4 * 3 + 4 + i*4, 0);//Layer offset
        }
        MemoryUtil.memPutLong(ptr+4*4*4+4*3+4*4,region.draw.drawMeta.addr);//instanceData address
        MemoryUtil.memPutLong(ptr+4*4*4+4*3+4*4+8,region.draw.drawCommandsList[0].addr);//layerCommands layer 0 address
        glUnmapNamedBuffer(region.draw.UBO.id);
    }

    void begin1() {
        rasterPass.bind();
        glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glEnable(GL_DEPTH_TEST);
        //Enable face culling and winding order direction
    }

    void end1() {
        rasterPass.unbind();
        glDisable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glDisable(GL_DEPTH_TEST);
    }

    void process1(Region region) {
        {
            //FAKE set vis
            long ptr = nglMapNamedBufferRange(region.draw.visBuffer.id, 0, 4*4*4+4*3+4+4*4, GL_MAP_WRITE_BIT);
            MemoryUtil.memSet(ptr, 1, 10);
            glUnmapNamedBuffer(region.draw.visBuffer.id);
        }
    }

    void begin2() {
        commandBuilder.bind();
    }

    void end2() {
        commandBuilder.unbind();
    }

    void process2(Region region) {
        glUniformui64NV(0, region.draw.UBO.addr);
        //TODO: see if moving visBuffer and chunkMeta addresses into UBO would have any performance changes
        glUniformui64NV(1, region.draw.chunkMeta.addr);
        glUniformui64NV(2, region.draw.visBuffer.addr);
        commandBuilder.dispatch((int)Math.ceil(region.sectionCount/32f),1,1);
        //commandBuilder.dispatch(1,1,1);
        //glMemoryBarrier(GL_ALL_BARRIER_BITS);

        /*
        long ptr = nglMapNamedBufferRange(region.draw.UBO.id, 0, region.draw.UBO.size, GL_MAP_READ_BIT);
        System.out.println(MemoryUtil.memGetInt(ptr+4*4*4+4*3));
        glUnmapNamedBuffer(region.draw.UBO.id);

         */
    }
}
