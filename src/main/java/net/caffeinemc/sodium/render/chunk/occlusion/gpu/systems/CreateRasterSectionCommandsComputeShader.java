package net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.ComputePipeline;
import net.caffeinemc.gfx.api.pipeline.RenderPipelineDescription;
import net.caffeinemc.gfx.api.pipeline.state.WriteMask;
import net.caffeinemc.gfx.api.shader.*;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

public class CreateRasterSectionCommandsComputeShader {
    private static final int LOCAL_SIZE_X = 32;
    private static final class ComputeInterface {
        public final BufferBlock scene;
        public final BufferBlock regionLUT;
        public final BufferBlock regionMeta;
        public final BufferBlock regionVisArray;
        public final BufferBlock sectionCommandBuff;
        public ComputeInterface(ShaderBindingContext context) {
            scene = context.bindBufferBlock(BufferBlockType.STORAGE, 0);
            regionLUT = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
            regionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
            regionVisArray = context.bindBufferBlock(BufferBlockType.STORAGE, 3);
            sectionCommandBuff = context.bindBufferBlock(BufferBlockType.STORAGE, 4);
        }
    }

    private final RenderDevice device;
    private final Program<ComputeInterface> computeProgram;
    private final ComputePipeline<ComputeInterface> pipeline;
    public CreateRasterSectionCommandsComputeShader(RenderDevice device) {
        this.device = device;

        ShaderConstants constants = ShaderConstants.builder()
                .add("LOCAL_SIZE_X", Integer.toString(LOCAL_SIZE_X))
                .build();
        this.computeProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/compute/section_command_generator.comp"), constants))
                .build(), ComputeInterface::new);

        this.pipeline = this.device.createComputePipeline(computeProgram);
    }

    public void execute(int regionCount, Buffer scene, Buffer regionMeta, Buffer regionLUT, Buffer regionVisArray, Buffer sectionCommandBuff) {
        this.device.useComputePipeline(pipeline, (cmd, programInterface, state) -> {
            state.bindBufferBlock(programInterface.scene, scene);
            state.bindBufferBlock(programInterface.regionLUT, regionLUT);
            state.bindBufferBlock(programInterface.regionMeta, regionMeta);
            state.bindBufferBlock(programInterface.regionVisArray, regionVisArray);
            state.bindBufferBlock(programInterface.sectionCommandBuff, sectionCommandBuff);
            cmd.dispatchCompute((int)(Math.ceil((double) regionCount/LOCAL_SIZE_X)),1,1);
        });
    }

}
