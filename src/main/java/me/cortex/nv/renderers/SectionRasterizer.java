package me.cortex.nv.renderers;

import me.cortex.nv.gl.shader.Shader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

//TODO:FIXME: CLEANUP
public class SectionRasterizer extends Phase {

    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderParser.parseShader(new Identifier("cortex", "occlusion/section_raster/task.glsl")))
            .addSource(MESH, ShaderParser.parseShader(new Identifier("cortex", "occlusion/section_raster/mesh.glsl")))
            .addSource(FRAGMENT, ShaderParser.parseShader(new Identifier("cortex", "occlusion/section_raster/fragment.glsl"))).compile();



    public void raster(int regionCount) {
        shader.bind();
        glDrawMeshTasksNV(0,regionCount);
    }

}
