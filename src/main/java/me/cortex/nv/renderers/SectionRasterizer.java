package me.cortex.nv.renderers;

import me.cortex.nv.gl.shader.Shader;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

//TODO:FIXME: CLEANUP
public class SectionRasterizer extends Phase {

    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "occlusion/section_raster/task.glsl")))
            .addSource(MESH, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "occlusion/section_raster/mesh.glsl")))
            .addSource(FRAGMENT, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "occlusion/section_raster/fragment.glsl"))).compile();



    public void raster(int regionCount, long uniformAddr, int uniformLen) {
        shader.bind();
        glBufferAddressRangeNV(GL_UNIFORM_BUFFER_ADDRESS_NV, 0, uniformAddr, uniformLen);
        glDrawMeshTasksNV(0,regionCount);
    }

}
