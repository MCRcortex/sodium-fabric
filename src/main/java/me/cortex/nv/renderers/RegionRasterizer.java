package me.cortex.nv.renderers;

import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.shader.Shader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

public class RegionRasterizer extends Phase {
    private final Shader shader = Shader.make()
                    .addSource(MESH, ShaderParser.parseShader(new Identifier("cortex", "occlusion/region_raster/mesh.glsl")))
                    .addSource(FRAGMENT, ShaderParser.parseShader(new Identifier("cortex", "occlusion/region_raster/fragment.frag")))
                    .compile();

    public void raster(int regionCount) {
        //TODO: make a binding system so i can just do shader.bind(shader parameters)
        shader.bind();
        glDrawMeshTasksNV(0,regionCount);
    }
}
