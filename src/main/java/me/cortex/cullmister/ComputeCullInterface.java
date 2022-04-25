package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.CShader;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30C;
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
import static org.lwjgl.opengl.GL30C.GL_QUERY_WAIT;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL42.GL_ATOMIC_COUNTER_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glClearBufferData;

//TODO: NEED TO SREIOUSLY REDO THE HIZ SHIT, cause its producing a butttttttttt tone of false positives
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
        glBindTextureUnit(0, hiz.mipDepthTex);
        glBindSampler(0, hiz.sampler);
        baseMat = new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView());
        cam_pos = cam;
    }
    //TODO: OPTIMIZE BINDING PROCESS, cause is slow

    //TODO: FIX BUS usage??? idfk whats going on but i think some shit is stored in client memory

    //Resets all the counters and stuff
    private void prepAndBind(Region region) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, region.chunkMeta.id);

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
        //glBeginConditionalRender(region.query, GL_QUERY_WAIT);
        MinecraftClient.getInstance().getProfiler().push("Binding");
        prepAndBind(region);
        Vector3f rpos = new Vector3f((region.pos.x()<<(Region.WIDTH_BITS+4))-cam_pos.x, (region.pos.y()*Region.HEIGHT*16)-cam_pos.y, (region.pos.z()<<(Region.WIDTH_BITS+4))-cam_pos.z);
        cullShader.setUniform("viewModelProjectionTranslate", baseMat.translate(rpos, new Matrix4f()));
        cullShader.setUniform("regionOffset", rpos);
        MinecraftClient.getInstance().getProfiler().swap("dispatch");
        // TODO: Try different sizes of local workers
        cullShader.dispatch((int)Math.ceil((float)region.sectionCount/64),1,1);
        //long ptr = region.drawData.drawMetaCount.mappedNamedPtrRanged(0,4,GL_MAP_READ_BIT);
        //System.out.println(MemoryUtil.memGetInt(ptr));
        //region.drawData.drawMetaCount.unmapNamed();

        MinecraftClient.getInstance().getProfiler().pop();
        //glEndConditionalRender();

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, 0);
        glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, 1, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, 0);
    }

    //End process
    public void end() {
        cullShader.unbind();
        RenderSystem.disableTexture();
        glBindTextureUnit(0, 0);
        glBindSampler(0, 0);
    }
}
