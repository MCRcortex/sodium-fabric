package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.Shader;
import me.cortex.cullmister.utils.VAO;
import net.caffeinemc.sodium.interop.vanilla.mixin.LightmapTextureManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.cortex.cullmister.commandListStuff.CommandListTokenWriter.NVHeader;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33.glSamplerParameteri;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.NVCommandList.GL_TERMINATE_SEQUENCE_COMMAND_NV;
import static org.lwjgl.opengl.NVCommandList.nglDrawCommandsAddressNV;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV;

public class RenderLayerSystem {
    Shader genericRenderShader;
    int mipsampler = glGenSamplers();
    int texsampler = glGenSamplers();
    int lightsampler = glGenSamplers();
    VAO vao;
    long heapData = MemoryUtil.nmemCalloc(1,1000);
    public RenderLayerSystem() {
        glSamplerParameteri(mipsampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(mipsampler,GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);

        glSamplerParameteri(texsampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        glSamplerParameteri(texsampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);

        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        glSamplerParameteri(lightsampler,GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);


        genericRenderShader = new Shader(Shader.get("assets/cullmister/render/Render.vert"), Shader.get("assets/cullmister/render/Render.frag"));

        //TODO: add IndexBuffer resize, cause atm it will just break

        //Enable IBO access on gpu
        RenderSystem.IndexBuffer ibo = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS, (1<<16)/4);
        glMakeNamedBufferResidentNV(ibo.getId(), GL_READ_WRITE);

        vao = new VAO();
        vao.bind();
        glVertexAttribFormat(1, 3, GL_SHORT, true, 0);
        glVertexAttribFormat(2, 4, GL_UNSIGNED_BYTE, true, 8);
        glVertexAttribFormat(3, 2, GL_UNSIGNED_SHORT, true, 12);
        glVertexAttribFormat(4, 2, GL_UNSIGNED_SHORT, true, 16);

        glVertexAttribBinding(1, 0);
        glVertexAttribBinding(2, 0);
        glVertexAttribBinding(3, 0);
        glVertexAttribBinding(4, 0);

        glVertexAttribFormat(0, 3, GL_FLOAT,false, 0);

        glVertexAttribBinding(0, 1);
        glVertexBindingDivisor(1, 1);

        glBindVertexBuffer(1, 0, 0, 3*4);
        glBindVertexBuffer(0, 0, 0, 20);
        vao.unbind();
    }

    public void begin() {
        genericRenderShader.bind();
        RenderSystem.enableTexture();
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        GL45C.glBindTextureUnit(0, tm.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlId());
        LightmapTextureManagerAccessor lightmapTextureManager =
                ((LightmapTextureManagerAccessor) MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager());
        GL45C.glBindTextureUnit(1, lightmapTextureManager.getTexture().getGlId());
        GL45C.glBindSampler(1, lightsampler);
        GL45C.glBindSampler(0, mipsampler);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        vao.bind();

        glEnableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        glEnableClientState(GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        glEnableClientState(GL_ELEMENT_ARRAY_UNIFIED_NV);

        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);
        glEnableVertexAttribArray(4);

        glEnableVertexAttribArray(0);
    }

    //TODO: need to add layers
    public void render(Region region) {
        //TODO: make VAO for this

        //TODO: See if these are even needed

        //glBindBufferBase(GL_UNIFORM_BUFFER, 0, uboBuffer);
        //glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, IBO);



        if (false) {
            glMemoryBarrier(GL_ALL_BARRIER_BITS);
            long ptr = nglMapNamedBufferRange( region.draw.UBO.id, 0 + 4 * 4 * 4 + 4 * 3 + 4, 4, GL_MAP_READ_BIT);
            region.draw.bsizeTEMPHOLDER = MemoryUtil.memGetInt(ptr);
            glUnmapNamedBuffer(region.draw.UBO.id);
            //System.out.println(region.draw.bsizeTEMPHOLDER);

            ptr = nglMapNamedBufferRange( region.draw.drawCommandsList[0].id, region.draw.bsizeTEMPHOLDER, 4, GL_MAP_WRITE_BIT);
            NVHeader(ptr, GL_TERMINATE_SEQUENCE_COMMAND_NV);
            glUnmapNamedBuffer(region.draw.drawCommandsList[0].id);
        }
        //if (region.draw.bsizeTEMPHOLDER == 48)
        //    return;
        //glDisable(GL_CULL_FACE);
        MemoryUtil.memPutLong(heapData, region.draw.drawCommandsList[0].addr);
        MemoryUtil.memPutInt(heapData+8, 200000);
        nglDrawCommandsAddressNV(GL_TRIANGLES, heapData, heapData+8, 1);


    }

    public void end() {
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);
        glDisableVertexAttribArray(3);
        glDisableVertexAttribArray(4);

        glDisableVertexAttribArray(0);
        glDisableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        glDisableClientState(GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        glDisableClientState(GL_ELEMENT_ARRAY_UNIFIED_NV);
        vao.unbind();
        glDisable(GL_DEPTH_TEST);
        GL45C.glBindSampler(0, 0);
        GL45C.glBindSampler(1, 0);
        GL45C.glBindTextureUnit(0,0);
        GL45C.glBindTextureUnit(1,0);
        RenderSystem.disableTexture();
        genericRenderShader.unbind();
    }
}
