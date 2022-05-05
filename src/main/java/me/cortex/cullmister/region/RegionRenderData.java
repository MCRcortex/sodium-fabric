package me.cortex.cullmister.region;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.commandListStuff.BindlessBuffer;
import me.cortex.cullmister.textures.BindlessTextureManager;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.SpriteAtlasTexture;

import static me.cortex.cullmister.commandListStuff.CommandListTokenWriter.*;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_COPY;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30.nglMapBufferRange;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.NVBindlessTexture.glUniformHandleui64NV;
import static org.lwjgl.opengl.NVCommandList.*;
import static org.lwjgl.opengl.NVShaderBufferLoad.GL_BUFFER_GPU_ADDRESS_NV;
import static org.lwjgl.opengl.NVShaderBufferLoad.glGetNamedBufferParameterui64vNV;

public class RegionRenderData {
    public long bsizeTEMPHOLDER;

    public BindlessBuffer visBuffer = new BindlessBuffer((1<<(Region.WIDTH_BITS*2))*Region.HEIGHT*Section.SIZE, GL_DYNAMIC_STORAGE_BIT|GL_MAP_WRITE_BIT);//the GL_DYNAMIC_STORAGE_BIT|GL_MAP_WRITE_BIT are just for testing
    public BindlessBuffer[] drawCommandsList = new BindlessBuffer[4];
    public BindlessBuffer rasterCommands;
    public BindlessBuffer drawMeta = new BindlessBuffer((1<<(Region.WIDTH_BITS*2))*Region.HEIGHT*3*4, GL_MAP_WRITE_BIT|GL_DYNAMIC_STORAGE_BIT|GL_MAP_READ_BIT);

    public BindlessBuffer chunkMeta = new BindlessBuffer((1<<(Region.WIDTH_BITS*2))*Region.HEIGHT*Section.SIZE, GL_MAP_WRITE_BIT|GL_MAP_READ_BIT|GL_DYNAMIC_STORAGE_BIT);
    public BindlessBuffer UBO = new BindlessBuffer(4*(4*4+3) + 4*(1+4)+8*(5), GL_MAP_WRITE_BIT|GL_DYNAMIC_STORAGE_BIT|GL_MAP_READ_BIT);

    public int drawCommandsOffset;
    public RegionRenderData() {
        for (int i = 0; i < 4; i++) {
            drawCommandsList[i] = new BindlessBuffer(1000000,GL_MAP_READ_BIT|GL_DYNAMIC_STORAGE_BIT|GL_MAP_WRITE_BIT);
            glClearNamedBufferSubData(drawCommandsList[i].id,  GL_R8UI, drawCommandsOffset,drawCommandsList[i].size-drawCommandsOffset , GL_RED, GL_UNSIGNED_BYTE, new int[]{0});
        }

        //TODO: need to prep all the drawCommandsList and rasterCommands to bind to the IBO and UBO
        // also need to store the offset to add to all draw command counts as an offset into the buffer
        // also also need to figure out how to add a sequence terminator
        drawCommandsOffset = 2*NVSize(GL_UNIFORM_ADDRESS_COMMAND_NV) + NVSize(GL_ELEMENT_ADDRESS_COMMAND_NV) + NVSize(GL_ATTRIBUTE_ADDRESS_COMMAND_NV);

        RenderSystem.IndexBuffer ibo = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS, 0);
        //Get IBO address
        long[] holder = new long[1];
        glGetNamedBufferParameterui64vNV(ibo.getId(), GL_BUFFER_GPU_ADDRESS_NV, holder);
        long iboAddr = holder[0];

        long textureLUTAddr = BindlessTextureManager.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getPointerBuffer().addr;

        for (int i = 0; i < 4; i++) {
            long ptr = nglMapNamedBufferRange(drawCommandsList[i].id, 0, drawCommandsOffset, GL_MAP_WRITE_BIT);
            ptr = NVTokenIBO(ptr, GL_UNSIGNED_SHORT, iboAddr);
            ptr = NVTokenUBO(ptr, 0, GL_VERTEX_SHADER, UBO.addr);
            ptr = NVTokenUBO(ptr, 1, GL_FRAGMENT_SHADER, textureLUTAddr);
            ptr = NVTokenVBO(ptr, 1, drawMeta.addr);
            glUnmapNamedBuffer(drawCommandsList[i].id);
        }


    }

    public void delete() {
        UBO.delete();
        chunkMeta.delete();
        visBuffer.delete();
        drawMeta.delete();
        for (int i = 0; i < 4; i++) {
            if (drawCommandsList[i] != null)
                drawCommandsList[i].delete();
            drawCommandsList[i] = null;
        }
    }
}
