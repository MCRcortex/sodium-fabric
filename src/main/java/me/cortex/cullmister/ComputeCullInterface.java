package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.CShader;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL42.GL_ATOMIC_COUNTER_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glClearBufferData;

public class ComputeCullInterface {
    CShader cullShader;
    HiZ hiz;
    Matrix4f baseMat;
    Vector3f cam_pos;
    public ComputeCullInterface(HiZ hiZ) {
        this.hiz = hiZ;
        cullShader = CShader.fromResource("assets/cullmister/culler/occlusionCompute.comp");
    }

    //Begin batch processing
    public void begin(ChunkRenderMatrices renderMatrices, Vector3f cam, int renderId) {
        cullShader.bind();
        cullShader.setUniformU("renderId", renderId);
        RenderSystem.enableTexture();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, hiz.mipDepthTex);
        baseMat = new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView());
        cam_pos = cam;
    }
    //Resets all the counters and stuff
    private void prepAndBind(Region region) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, region.chunkMeta.id);

        //glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, visibleChunkList.id);

        //glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, 0, visibleChunkListCount.id);
        //glClearBufferData(GL_ATOMIC_COUNTER_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, (ByteBuffer) null);

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, region.drawData.drawCounts.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, region.drawData.drawCommands.id);

        glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, 1, region.drawData.drawMetaCount.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, region.drawData.drawMeta.id);

        nglClearNamedBufferData(region.drawData.drawCounts.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        nglClearNamedBufferData(region.drawData.drawMetaCount.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
    }

    //Enqueue process
    public void process(Region region) {
        prepAndBind(region);
        cullShader.setUniform("viewModelProjectionTranslate", baseMat.translate((region.pos.x()<<9)-cam_pos.x, (region.pos.y()*5*16)-cam_pos.y, (region.pos.z()<<9)-cam_pos.z, new Matrix4f()));
        cullShader.dispatch((int) Math.ceil((double) region.sectionCount/64),1,1);
        //long ptr = region.drawData.drawMetaCount.mappedNamedPtrRanged(0,4,GL_MAP_READ_BIT);
        //System.out.println(MemoryUtil.memGetInt(ptr));
        //region.drawData.drawMetaCount.unmapNamed();
    }

    //End process
    public void end() {
        cullShader.unbind();
        RenderSystem.disableTexture();
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}
