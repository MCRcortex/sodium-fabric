package me.cortex.nv.renderers;

import me.cortex.nv.gl.shader.Shader;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;

public class RegionRasterizer extends Phase {
    private final Shader shader = Shader.make()
            .addSource(MESH, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "occlusion/region_raster/mesh.glsl")))
            .addSource(FRAGMENT, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("cortex", "occlusion/region_raster/fragment.frag")))
            .compile();
    public void raster() {
        shader.bind();
        glDrawMeshTasksNV(0,10);//10 regions
    }
}
