package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.CShader;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

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
    public ComputeCullInterface(HiZ hiZ) {
        this.hiz = hiZ;
        cullShader = CShader.fromResource("assets/cullmister/culler/occlusionCompute.comp");
    }

    //Begin batch processing
    public void begin(int renderId) {
        cullShader.bind();
        cullShader.setUniformU("renderId", renderId);
        RenderSystem.enableTexture();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, hiz.mipDepthTex);
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
    }

    //Enqueue process
    public void process(Region region) {
        prepAndBind(region);


        cullShader.dispatch((int) Math.ceil((double) region.sectionCount/64),1,1);


    }

    //End process
    public void end() {
        cullShader.unbind();
        RenderSystem.disableTexture();
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}
