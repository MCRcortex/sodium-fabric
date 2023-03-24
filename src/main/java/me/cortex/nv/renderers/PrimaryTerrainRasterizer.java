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
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.*;

public class PrimaryTerrainRasterizer extends Phase {
    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderParser.parseShader(new Identifier("cortex", "terrain/task.glsl")))
            .addSource(MESH, ShaderParser.parseShader(new Identifier("cortex", "terrain/mesh.glsl")))
            .addSource(FRAGMENT, ShaderParser.parseShader(new Identifier("cortex", "terrain/frag.frag"))).compile();


    public void raster(int regionCount, IDeviceMappedBuffer commandAddr) {
        shader.bind();

        glActiveTexture(GL32C.GL_TEXTURE0);
        int id = MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png")).getGlId();
        glBindTexture(GL_TEXTURE_2D, id);//GL45C.glBindTextureUnit(0, TextureUtil.getBlockTextureId());
        //GL45C.glBindSampler(0, GlSampler.getHandle(sampler));

        glBufferAddressRangeNV(GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandAddr.getDeviceAddress(), regionCount* 8L*7);//Bind the command buffer
        glMultiDrawMeshTasksIndirectNV( 0, regionCount*7, 0);
    }
}
