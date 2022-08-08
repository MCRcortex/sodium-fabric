package net.caffeinemc.sodium.render.chunk.occlusion.gpu.systems;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.ComputePipeline;
import net.caffeinemc.gfx.api.pipeline.RenderPipelineDescription;
import net.caffeinemc.gfx.api.pipeline.state.WriteMask;
import net.caffeinemc.gfx.api.shader.*;
import net.caffeinemc.sodium.render.chunk.occlusion.gpu.ViewportedData;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.minecraft.util.Identifier;


//TODO: maybe also write to another buffer with the region ids that are visible and use dispatch indirects
// to optimize dim counts
public class CreateRasterSectionCommandsComputeShader {
    public static final int LOCAL_SIZE_X = 32;

    private static final class ComputeInterface {
        public final BufferBlock scene;
        public final BufferBlock regionArray;
        public final BufferBlock regionMeta;
        public final BufferBlock regionVisArray;
        public final BufferBlock sectionCommandBuff;
        public final BufferBlock dispatchCompute;
        public final BufferBlock regionArrayOut;
        public ComputeInterface(ShaderBindingContext context) {
            scene = context.bindBufferBlock(BufferBlockType.UNIFORM, 0);
            regionArray = context.bindBufferBlock(BufferBlockType.STORAGE, 1);
            regionMeta = context.bindBufferBlock(BufferBlockType.STORAGE, 2);
            regionVisArray = context.bindBufferBlock(BufferBlockType.STORAGE, 3);
            sectionCommandBuff = context.bindBufferBlock(BufferBlockType.STORAGE, 4);
            dispatchCompute = context.bindBufferBlock(BufferBlockType.STORAGE, 5);
            regionArrayOut = context.bindBufferBlock(BufferBlockType.STORAGE, 6);
        }
    }

    private final RenderDevice device;
    private final Program<ComputeInterface> computeProgram;
    private final ComputePipeline<ComputeInterface> pipeline;
    public CreateRasterSectionCommandsComputeShader(RenderDevice device) {
        this.device = device;

        ShaderConstants constants = ShaderConstants.builder()
                .add("LOCAL_SIZE_X", Integer.toString(LOCAL_SIZE_X))
                .add("TERRAIN_LOCAL_SIZE_Y", Integer.toString(CreateTerrainCommandsComputeShader.LOCAL_SIZE_Y))
                .add("REGION_SECTION_MAX_SIZE", Integer.toString(RenderRegion.REGION_SIZE))
                .build();
        this.computeProgram = this.device.createProgram(ShaderDescription.builder()
                .addShaderSource(ShaderType.COMPUTE,
                        ShaderParser.parseSodiumShader(ShaderLoader.MINECRAFT_ASSETS,
                                new Identifier("sodium", "occlusion/compute/section_command_generator.comp"), constants))
                .build(), ComputeInterface::new);

        this.pipeline = this.device.createComputePipeline(computeProgram);
    }

    public void execute(int regionCount, Buffer scene, int offset, Buffer regionMeta, Buffer regionArray, Buffer regionVisArray, Buffer sectionCommandBuff, Buffer dispatchComputeBuffer, Buffer regionArrayOut) {
        this.device.useComputePipeline(pipeline, (cmd, programInterface, state) -> {
            state.bindBufferBlock(programInterface.scene, scene, offset, ViewportedData.SCENE_STRUCT_ALIGNMENT);
            state.bindBufferBlock(programInterface.regionArray, regionArray);
            state.bindBufferBlock(programInterface.regionMeta, regionMeta);
            state.bindBufferBlock(programInterface.regionVisArray, regionVisArray);
            state.bindBufferBlock(programInterface.sectionCommandBuff, sectionCommandBuff);
            state.bindBufferBlock(programInterface.dispatchCompute, dispatchComputeBuffer);
            state.bindBufferBlock(programInterface.regionArrayOut, regionArrayOut);
            cmd.dispatchCompute((int)(Math.ceil((double) regionCount/LOCAL_SIZE_X)),1,1);
        });
    }

    public void delete() {
        device.deleteProgram(computeProgram);
        device.deleteComputePipeline(pipeline);
    }
}
