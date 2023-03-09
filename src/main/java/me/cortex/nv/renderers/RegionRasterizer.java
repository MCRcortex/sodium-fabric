package me.cortex.nv.renderers;

import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.shader.Shader;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

public class RegionRasterizer extends Phase {
    private final Shader shader = Shader.make()
                    .addSource(MESH, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                            new Identifier("cortex", "occlusion/region_raster/mesh.glsl")))
                    .addSource(FRAGMENT, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                            new Identifier("cortex", "occlusion/region_raster/fragment.frag")))
                    .compile();

    public void raster(int regionCount, long uniformAddr, int uniformLen) {
        //TODO: make a binding system so i can just do shader.bind(shader parameters)
        shader.bind();
        glBufferAddressRangeNV(GL_UNIFORM_BUFFER_ADDRESS_NV, 0, uniformAddr, uniformLen);
        glDrawMeshTasksNV(0,regionCount);
    }
}
