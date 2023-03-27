package me.cortex.nv.renderers;

import me.cortex.nv.gl.images.DepthOnlyFrameBuffer;
import me.cortex.nv.gl.shader.Shader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL15C.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

public class MipGenerator extends Phase {
    /*
    private final Shader shader = Shader.make()
                .addSource(COMPUTE, ShaderParser.parseShader(new Identifier("cortex", "occlusion/mippers/mip4x4.comp"))).compile();


    public void mip(int depthAttachment, DepthOnlyFrameBuffer mippedDepthBuffer) {
        shader.bind();
        //glUniform2f(2, 1.0f/(mippedDepthBuffer.width*2), 1.0f/(mippedDepthBuffer.height*2));
        //glActiveTexture(GL_TEXTURE0);
        //glBindTexture(GL_TEXTURE_2D, depthAttachment);
        //glActiveTexture(GL_TEXTURE1);
        //glBindImageTexture(1, mippedDepthBuffer.getDepthBuffer(), 0, false,0, GL_WRITE_ONLY, GL_R32F);
        glBindImageTexture(0, mippedDepthBuffer.getDepthBuffer(), 0, false,0, GL_WRITE_ONLY, GL_R32F);
        //glUniform1i(0, 0);
        glDispatchCompute(mippedDepthBuffer.width, mippedDepthBuffer.height, 1);
        //glActiveTexture(GL_TEXTURE0);
    }*/


    private final Shader shader = Shader.make()
                .addSource(VERTEX, ShaderParser.parseShader(new Identifier("cortex", "occlusion/mippers/fullscreen.vert")))
                .addSource(FRAGMENT, ShaderParser.parseShader(new Identifier("cortex", "occlusion/mippers/mipper4x4.frag")))
            .compile();

    private final int vao = glGenVertexArrays();


    public void mip(int depthAttachment, DepthOnlyFrameBuffer mippedDepthBuffer) {
        shader.bind();
        //glUniform2f(2, 1.0f/(mippedDepthBuffer.width*2), 1.0f/(mippedDepthBuffer.height*2));
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, depthAttachment);
        glDepthFunc(GL_ALWAYS);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDepthFunc(GL_LEQUAL);
        //glActiveTexture(GL_TEXTURE0);
    }
}
