package net.caffeinemc.sodium.render.chunk.draw;

import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.shader.Program;
import net.caffeinemc.gfx.api.shader.ShaderDescription;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.render.chunk.occlussion.CommandGeneratorInterface;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

public class ComputeTranslucencySort {

    private final RenderDevice device;
    private Program<ComputeTranslucencySortInterface> sortProgram;

    public ComputeTranslucencySort(RenderDevice device) {
        this.device = device;

        ShaderConstants constants = ShaderConstants.builder().build();
        this.sortProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "translucency/index_translucency_sort.comp"),
                                constants))
                .build(), ComputeTranslucencySortInterface::new);
    }
}
