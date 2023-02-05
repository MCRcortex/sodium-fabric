package me.cortex.nv.gl.shader;

import me.cortex.nv.gl.shader.IShaderProcessor;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ShaderLoader {
    public static final Function<String, IShaderProcessor> BASIC_RESOURCE = namespace ->
            (type, source) ->
                    ShaderParser.parseSodiumShader(net.caffeinemc.sodium.render.shader.ShaderLoader.MINECRAFT_ASSETS, new Identifier(namespace, source));
}
