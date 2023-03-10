package me.cortex.nv.renderers;

import me.cortex.nv.gl.GlObject;
import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nv.gl.shader.Shader;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.RenderPipeline.GL_DRAW_INDIRECT_ADDRESS_NV;
import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL46C.GL_PARAMETER_BUFFER;
import static org.lwjgl.opengl.NVCommandList.glDrawCommandsAddressNV;
import static org.lwjgl.opengl.NVCommandList.nglDrawCommandsAddressNV;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectCountNV;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectNV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.*;

public class PrimaryTerrainRasterizer extends Phase {
    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "terrain/task.glsl")))
            .addSource(MESH, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "terrain/mesh.glsl")))
            .addSource(FRAGMENT, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "terrain/frag.frag"))).compile();


    public void raster(int regionCount, long uniformAddr, int uniformLen, IDeviceMappedBuffer commandAddr) {
        shader.bind();
        glBufferAddressRangeNV(GL_UNIFORM_BUFFER_ADDRESS_NV, 0, uniformAddr, uniformLen);//Bind the normal uniform buffer
        glBufferAddressRangeNV(GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandAddr.getDeviceAddress(), regionCount* 8L);//Bind the command buffer
        glMultiDrawMeshTasksIndirectNV( 0, regionCount, 0);
    }
}
