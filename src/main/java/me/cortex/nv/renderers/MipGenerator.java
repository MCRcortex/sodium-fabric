package me.cortex.nv.renderers;

import me.cortex.nv.gl.shader.Shader;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import static me.cortex.nv.gl.shader.ShaderType.COMPUTE;

public class MipGenerator extends Phase {
    private final Shader shader = Shader.make()
                .addSource(COMPUTE, ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                        new Identifier("cortex", "occlusion/mippers/mip4x4.comp"))).compile();


    public void mip() {

    }
}
