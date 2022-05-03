package me.cortex.cullmister;

import me.cortex.cullmister.commandListStuff.BindlessBuffer;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.CShader;
import me.cortex.cullmister.utils.Shader;
import me.cortex.cullmister.utils.VAO;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static me.cortex.cullmister.commandListStuff.CommandListTokenWriter.NVHeader;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31C.nglDrawElementsInstanced;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.NVCommandList.GL_TERMINATE_SEQUENCE_COMMAND_NV;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;
import static org.lwjgl.opengl.NVShaderBufferLoad.glUniformui64NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV;

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
    BindlessBuffer cubeIBO = new BindlessBuffer(6*6, GL_MAP_WRITE_BIT|GL_DYNAMIC_STORAGE_BIT);
    VAO vao;
    public CullSystem() {
        loadShaders();
        vao = new VAO();
        /*
          4_________5
         /.        /|
        6_________7 |
        | 0.......|.1
        |.        |/
        2_________3
        */
        ByteBuffer indices = glMapNamedBuffer(cubeIBO.id, GL_WRITE_ONLY);
        //Front face
        indices.put((byte) 0); indices.put((byte) 1); indices.put((byte) 2);
        indices.put((byte) 2); indices.put((byte) 3); indices.put((byte) 0);

        //right face
        indices.put((byte) 1); indices.put((byte) 5); indices.put((byte) 6);
        indices.put((byte) 6); indices.put((byte) 2); indices.put((byte) 1);

        //Back face
        indices.put((byte) 7); indices.put((byte) 6); indices.put((byte) 5);
        indices.put((byte) 5); indices.put((byte) 4); indices.put((byte) 7);

        //Left face
        indices.put((byte) 4); indices.put((byte) 0); indices.put((byte) 3);
        indices.put((byte) 3); indices.put((byte) 7); indices.put((byte) 4);

        //Bottom face
        indices.put((byte) 4); indices.put((byte) 5); indices.put((byte) 1);
        indices.put((byte) 1); indices.put((byte) 0); indices.put((byte) 4);

        //Top face
        indices.put((byte) 3); indices.put((byte) 2); indices.put((byte) 6);
        indices.put((byte) 6); indices.put((byte) 7); indices.put((byte) 3);
        glUnmapNamedBuffer(cubeIBO.id);
        vao.bind();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, cubeIBO.id);
        vao.unbind();
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
                .translate(region.pos.x()<<(Region.WIDTH_BITS+4), region.pos.y()*Region.HEIGHT*16, region.pos.z()<<(Region.WIDTH_BITS+4))
                .getToAddress(ptr);
        new Vector3f(cam)
                .negate()
                .add(   region.pos.x()<<(Region.WIDTH_BITS+4),
                        region.pos.y()*Region.HEIGHT*16,
                        region.pos.z()<<(Region.WIDTH_BITS+4))
                .getToAddress(ptr+4*4*4);
        //Reset all the atomic offset counters
        MemoryUtil.memPutInt(ptr+4*4*4+4*3,0);//Instance data count
        for (int i = 0; i < 4; i++) {
            //Layer offset
            MemoryUtil.memPutInt(ptr + 4 * 4 * 4 + 4 * 3 + 4 + i*4, region.draw.drawCommandsOffset);
        }
        MemoryUtil.memPutLong(ptr+4*4*4+4*3+4+4*4,region.draw.drawMeta.addr);//instanceData address
        for (int i = 0; i < 4; i++) {
            //layerCommands layer i address
            MemoryUtil.memPutLong(ptr + 4 * 4 * 4 + 4 * 3 + 4 + 4 * 4 + 8 + i*8, region.draw.drawCommandsList[i].addr);
        }
        glUnmapNamedBuffer(region.draw.UBO.id);

        //TEMPORARY
        //TODO: REPLACE
        //glClearNamedBufferSubData(region.draw.drawCommandsList[0].id,  GL_R32UI, region.draw.drawCommandsOffset,1000, GL_RED, GL_UNSIGNED_INT, new int[]{NVHeader(GL_TERMINATE_SEQUENCE_COMMAND_NV)});

        glClearNamedBufferSubData(region.draw.drawCommandsList[0].id,  GL_R8UI, region.draw.drawCommandsOffset,region.draw.drawCommandsList[0].size-region.draw.drawCommandsOffset , GL_RED, GL_UNSIGNED_BYTE, new int[]{0});
        //glClearNamedBufferSubData(region.draw.drawCommandsList[1].id,  GL_R8UI, region.draw.drawCommandsOffset,region.draw.drawCommandsList[1].size-region.draw.drawCommandsOffset , GL_RED, GL_UNSIGNED_BYTE, new int[]{0});
    }

    void begin1() {
        rasterPass.bind();
        glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);
        //glCullFace(GL_BACK);
        glDepthMask(false);
        glColorMask(false, false, false, false);
        vao.bind();
        glEnableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        //Enable face culling and winding order direction
    }

    void end1() {
        //glDepthFunc(GL_ALWAYS);
        glDepthMask(true);
        glColorMask(true, true, true, true);
        glDisableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        vao.unbind();
        rasterPass.unbind();
        glDisable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glDisable(GL_DEPTH_TEST);
    }
    //TODO: THE DEPTH CHECK DOESNT WORK like for some reason its marking it all as seen or some bullshit
    void process1(Region region) {
        //glClearNamedBufferSubData(region.draw.visBuffer.id, GL_R8UI, 0, region.draw.visBuffer.size, GL_RED, GL_UNSIGNED_BYTE, new int[]{-1});
        /*
        if (false){
            //FAKE set vis
            long ptr = nglMapNamedBufferRange(region.draw.visBuffer.id, 0, 200, GL_MAP_WRITE_BIT|GL_MAP_UNSYNCHRONIZED_BIT);
            MemoryUtil.memSet(ptr, 1, 100);
            glUnmapNamedBuffer(region.draw.visBuffer.id);
        }*/

        glUniformui64NV(0, region.draw.UBO.addr);
        glUniformui64NV(1, region.draw.chunkMeta.addr);
        glUniformui64NV(2, region.draw.visBuffer.addr);
        nglDrawElementsInstanced(GL_TRIANGLES, 6*6, GL_UNSIGNED_BYTE, 0, region.sectionCount);
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

        if (false) {
            glMemoryBarrier(GL_ALL_BARRIER_BITS);
            long ptr = nglMapNamedBufferRange(region.draw.drawMeta.id, 0, region.draw.drawMeta.size, GL_MAP_READ_BIT);
            System.out.println(MemoryUtil.memGetFloat(ptr));
            glUnmapNamedBuffer(region.draw.drawMeta.id);
        }


    }
}
