package net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.ComputePipeline;
import net.caffeinemc.gfx.api.shader.*;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.OcclusionEngine;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.ViewportedData;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
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
        public final BufferBlock commandCounterBuffer;
        public final BufferBlock instancedDataBuffer;
        public final BufferBlock commandOutputBuffer;

        public ComputeInterface(ShaderBindingContext context) {
            scene = context.bindBufferBlock(BufferBlockType.UNIFORM, 0);
            regionArray = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
            regionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
            sectionVisBuff = context.bindBufferBlock(BufferBlockType.STORAGE, 3);
            sectionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 4);
            commandCounterBuffer = context.bindBufferBlock(BufferBlockType.STORAGE, 5);
            instancedDataBuffer = context.bindBufferBlock(BufferBlockType.STORAGE, 6);
            commandOutputBuffer = context.bindBufferBlock(BufferBlockType.STORAGE, 7);
        }
    }

    private final RenderDevice device;
    private final Program<ComputeInterface> computeProgram;
    private final ComputePipeline<ComputeInterface> pipeline;
    public CreateTerrainCommandsComputeShader(RenderDevice device) {
        this.device = device;
        ShaderConstants constants = ShaderConstants.builder()
                .add("LOCAL_SIZE_Y", Integer.toString(LOCAL_SIZE_Y))
                .add("REGION_SECTION_MAX_SIZE", Integer.toString(RenderRegion.REGION_SIZE))
                .add("MAX_COMMAND_COUNT_PER_LAYER", Integer.toString(OcclusionEngine.MAX_RENDER_COMMANDS_PER_LAYER))
                .add("MAX_REGIONS", String.valueOf(OcclusionEngine.MAX_REGIONS))
                .build();
        this.computeProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/compute/terrain_command_generator.comp"), constants))
                .build(), ComputeInterface::new);

        this.pipeline = this.device.createComputePipeline(computeProgram);
    }

    //
    public void execute(Buffer scene, int offset, Buffer dispatchCompute, Buffer regionArray, Buffer regionMeta, Buffer sectionMeta, Buffer sectionVisBuffer, Buffer commandCounter, Buffer instancedDataBuffer, Buffer commandOutputBuffer) {
        this.device.useComputePipeline(pipeline, (cmd, programInterface, state) -> {
            state.bindBufferBlock(programInterface.scene, scene, offset, ViewportedData.SCENE_STRUCT_ALIGNMENT);
            state.bindBufferBlock(programInterface.regionArray, regionArray);
            state.bindBufferBlock(programInterface.regionMeta, regionMeta);
            state.bindBufferBlock(programInterface.sectionVisBuff, sectionVisBuffer);
            state.bindBufferBlock(programInterface.sectionMeta, sectionMeta);
            state.bindBufferBlock(programInterface.commandCounterBuffer, commandCounter);
            state.bindBufferBlock(programInterface.instancedDataBuffer, instancedDataBuffer);
            state.bindBufferBlock(programInterface.commandOutputBuffer, commandOutputBuffer);


            cmd.bindDispatchIndirectBuffer(dispatchCompute);
            cmd.dispatchComputeIndirect(0);
        });
    }

    public void delete() {
        device.deleteProgram(computeProgram);
        device.deleteComputePipeline(pipeline);
    }
}
