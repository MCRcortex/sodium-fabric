package me.cortex.nv.renderers;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.nv.gl.GlObject;
import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.shader.Shader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import me.jellysquid.mods.sodium.client.util.TextureUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL45C;

import static com.mojang.blaze3d.platform.GlStateManager.glActiveTexture;
import static me.cortex.nv.RenderPipeline.GL_DRAW_INDIRECT_ADDRESS_NV;
import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.*;

public class PrimaryTerrainRasterizer extends Phase {
    private final int sampler = glGenSamplers();
    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderParser.parseShader(new Identifier("cortex", "terrain/task.glsl")))
            .addSource(MESH, ShaderParser.parseShader(new Identifier("cortex", "terrain/mesh.glsl")))
            .addSource(FRAGMENT, ShaderParser.parseShader(new Identifier("cortex", "terrain/frag.frag"))).compile();

    public PrimaryTerrainRasterizer() {
        GL45C.glSamplerParameteri(sampler, GL45C.GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        GL45C.glSamplerParameteri(sampler, GL45C.GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GL45C.glSamplerParameteri(sampler, GL45C.GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    }

    public void raster(int regionCount, IDeviceMappedBuffer commandAddr) {
        shader.bind();

        int id = MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png")).getGlId();
        glActiveTexture(GL32C.GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
        GL45C.glBindTextureUnit(0, id);
        GL45C.glBindSampler(0, sampler);

        glBufferAddressRangeNV(GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandAddr.getDeviceAddress(), regionCount* 8L*7);//Bind the command buffer
        glMultiDrawMeshTasksIndirectNV( 0, regionCount*7, 0);
    }
}
