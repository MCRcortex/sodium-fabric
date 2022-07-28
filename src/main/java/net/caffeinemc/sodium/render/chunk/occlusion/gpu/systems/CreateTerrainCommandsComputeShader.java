package net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.ComputePipeline;
import net.caffeinemc.gfx.api.shader.*;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;

//TODO: maybe do via dispatch indirect or something that is set via the RasterSection compute shader
// simply add 1 to x dim and atomic max the y dim
public class CreateTerrainCommandsComputeShader {
    public static final int LOCAL_SIZE_Y = 32;
    private static final class ComputeInterface {
        public final BufferBlock scene;
        public final BufferBlock regionArray;
        public final BufferBlock regionMeta;
        public final BufferBlock sectionVisBuff;
        public final BufferBlock sectionMeta;
        public ComputeInterface(ShaderBindingContext context) {
            scene = context.bindBufferBlock(BufferBlockType.STORAGE, 0);
            regionArray = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
            regionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
            sectionVisBuff = context.bindBufferBlock(BufferBlockType.STORAGE, 3);
            sectionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 4);
        }
    }

    private final RenderDevice device;
    private final Program<ComputeInterface> computeProgram;
    private final ComputePipeline<ComputeInterface> pipeline;
    public CreateTerrainCommandsComputeShader(RenderDevice device) {
        this.device = device;
        ShaderConstants constants = ShaderConstants.builder()
                .add("LOCAL_SIZE_Y", Integer.toString(LOCAL_SIZE_Y))
                .build();
        this.computeProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/compute/terrain_command_generator.comp"), constants))
                .build(), ComputeInterface::new);

        this.pipeline = this.device.createComputePipeline(computeProgram);
    }

    //
    public void execute(Buffer scene, Buffer dispatchCompute, Buffer regionArray, Buffer regionMeta, Buffer sectionMeta, Buffer sectionVisBuffer) {
        this.device.useComputePipeline(pipeline, (cmd, programInterface, state) -> {
            state.bindBufferBlock(programInterface.scene, scene);
            state.bindBufferBlock(programInterface.regionArray, regionArray);
            state.bindBufferBlock(programInterface.regionMeta, regionMeta);
            state.bindBufferBlock(programInterface.sectionVisBuff, sectionVisBuffer);
            state.bindBufferBlock(programInterface.sectionMeta, sectionMeta);


            cmd.bindDispatchIndirectBuffer(dispatchCompute);
            cmd.dispatchComputeIndirect(0);
        });
    }
}
