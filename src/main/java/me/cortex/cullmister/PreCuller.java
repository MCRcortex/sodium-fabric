package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.Shader;
import me.cortex.cullmister.utils.VAO;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30C.glBeginConditionalRender;
import static org.lwjgl.opengl.GL33.GL_ANY_SAMPLES_PASSED;
import static org.lwjgl.opengl.GL40C.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;

public class PreCuller {
    Shader tester = new Shader("""
            #version 460
            uniform mat4 viewModelProjectionTranslate;
            void main() {
                gl_Position = viewModelProjectionTranslate * vec4(vec3(gl_VertexID&1, (gl_VertexID>>1)&1, (gl_VertexID>>2)&1)*vec3(1<<9, 5*16, 1<<9),1);
            }
            """, """
            """);
    VAO vao = new VAO();
    Matrix4f vm;
    Vector3f pos;
    public PreCuller() {
        int cubeIndexBuff = glGenBuffers();
        vao.bind();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, cubeIndexBuff);
        vao.unbind();
        glNamedBufferData(cubeIndexBuff, 3*2*6, GL_STATIC_DRAW);
        //TODO: create index buffer that will render a cube

    }

    public void begin(ChunkRenderMatrices renderMatrices, Vector3f pos) {
        this.pos = pos;
        vm = new Matrix4f(renderMatrices.projection()).mul(renderMatrices.modelView());
        glColorMask(false, false, false,false);
        glDepthMask(false);
        RenderSystem.enableDepthTest();
        tester.bind();
        vao.bind();
    }

    public void process(Region region) {
        tester.setUniform("viewModelProjectionTranslate", vm.translate(new Vector3f(region.pos.x()<<9, region.pos.y()*Region.HEIGHT, region.pos.z()<<9).sub(pos), new Matrix4f()));
        glBeginQuery(GL_ANY_SAMPLES_PASSED, region.query);
        //glDrawElements(GL_TRIANGLES, 3*2*6, GL_UNSIGNED_BYTE, 0);
        glEndQuery(GL_ANY_SAMPLES_PASSED);
    }

    public void end() {
        vao.unbind();
        RenderSystem.disableDepthTest();
        glColorMask(true, true, true, true);
        glDepthMask(true);
        tester.unbind();
    }
}
