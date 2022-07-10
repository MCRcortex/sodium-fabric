package net.caffeinemc.sodium.render.chunk.draw;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.array.VertexArrayResourceBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeBinding;
import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.buffer.MappedBufferFlags;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.Pipeline;
import net.caffeinemc.gfx.api.pipeline.PipelineState;
import net.caffeinemc.gfx.api.shader.Program;
import net.caffeinemc.gfx.api.shader.ShaderDescription;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.gfx.api.types.ElementFormat;
import net.caffeinemc.gfx.api.types.PrimitiveType;
import net.caffeinemc.gfx.util.buffer.DualStreamingBuffer;
import net.caffeinemc.gfx.util.buffer.SequenceBuilder;
import net.caffeinemc.gfx.util.buffer.SequenceIndexBuffer;
import net.caffeinemc.gfx.util.buffer.StreamingBuffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.chunk.RenderSection;
import net.caffeinemc.sodium.render.chunk.ViewportInterface;
import net.caffeinemc.sodium.render.chunk.ViewportedData;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderBindingPoints;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.sodium.render.chunk.state.UploadedChunkGeometry;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.TextureUtil;
import net.caffeinemc.sodium.util.UnsafeUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class MdicGPUOcclusionRenderer extends AbstractChunkRenderer {
    public static final int COMMAND_STRUCT_STRIDE = 5 * Integer.BYTES;
    public static final int INSTANCE_STRUCT_STRIDE = 3 * Float.BYTES;
    public static final int CAMERA_MATRICES_SIZE = 192;
    public static final int FOG_PARAMETERS_SIZE = 32;
    public static final int INSTANCE_DATA_SIZE = RenderRegion.REGION_SIZE * INSTANCE_STRUCT_STRIDE;

    protected final StreamingBuffer uniformBufferCameraMatrices;
    protected final StreamingBuffer uniformBufferInstanceData;
    protected final StreamingBuffer uniformBufferFogParameters;
    protected final SequenceIndexBuffer indexBuffer;

    protected final Map<ChunkRenderPass, Pipeline<ChunkShaderInterface, BufferTarget>> pipelines = new Object2ObjectOpenHashMap<>();

    public MdicGPUOcclusionRenderer(RenderDevice device,
                                    ChunkRenderPassManager renderPassManager,
                                    TerrainVertexType vertexType) {
        super(device);
        this.indexBuffer = new SequenceIndexBuffer(device, SequenceBuilder.QUADS_INT);

        int maxInFlightFrames = SodiumClientMod.options().advanced.cpuRenderAheadLimit + 1;

        int uboAlignment = device.properties().values.uniformBufferOffsetAlignment;

        this.uniformBufferCameraMatrices = new DualStreamingBuffer(
                device,
                uboAlignment,
                CAMERA_MATRICES_SIZE,
                maxInFlightFrames,
                EnumSet.of(MappedBufferFlags.EXPLICIT_FLUSH)
        );
        this.uniformBufferInstanceData = new DualStreamingBuffer(
                device,
                uboAlignment,
                1048576, // start with 1 MiB and expand from there if needed
                maxInFlightFrames,
                EnumSet.of(MappedBufferFlags.EXPLICIT_FLUSH)
        );
        this.uniformBufferFogParameters = new DualStreamingBuffer(
                device,
                uboAlignment,
                FOG_PARAMETERS_SIZE,
                maxInFlightFrames,
                EnumSet.of(MappedBufferFlags.EXPLICIT_FLUSH)
        );

        var vertexFormat = vertexType.getCustomVertexFormat();
        var vertexArray = new VertexArrayDescription<>(
                BufferTarget.values(),
                List.of(new VertexArrayResourceBinding<>(
                        BufferTarget.VERTICES,
                        new VertexAttributeBinding[] {
                                new VertexAttributeBinding(
                                        ChunkShaderBindingPoints.ATTRIBUTE_POSITION,
                                        vertexFormat.getAttribute(
                                                TerrainMeshAttribute.POSITION)
                                ),
                                new VertexAttributeBinding(
                                        ChunkShaderBindingPoints.ATTRIBUTE_COLOR,
                                        vertexFormat.getAttribute(
                                                TerrainMeshAttribute.COLOR)
                                ),
                                new VertexAttributeBinding(
                                        ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE,
                                        vertexFormat.getAttribute(
                                                TerrainMeshAttribute.BLOCK_TEXTURE)
                                ),
                                new VertexAttributeBinding(
                                        ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE,
                                        vertexFormat.getAttribute(
                                                TerrainMeshAttribute.LIGHT_TEXTURE)
                                )
                        }
                ))
        );

        for (ChunkRenderPass renderPass : renderPassManager.getAllRenderPasses()) {
            var constants = getShaderConstants(renderPass, vertexType);

            var vertShader = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.vert"),
                    constants
            );
            var fragShader = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.frag"),
                    constants
            );

            var desc = ShaderDescription.builder()
                    .addShaderSource(ShaderType.VERTEX, vertShader)
                    .addShaderSource(ShaderType.FRAGMENT, fragShader)
                    .build();

            Program<ChunkShaderInterface> program = this.device.createProgram(desc, ChunkShaderInterface::new);
            Pipeline<ChunkShaderInterface, BufferTarget> pipeline = this.device.createPipeline(
                    renderPass.pipelineDescription(),
                    program,
                    vertexArray
            );

            this.pipelines.put(renderPass, pipeline);
        }
    }

    @Override
    public void createRenderLists(SortedChunkLists chunks, ChunkCameraContext camera, int frameIndex) {

    }

    @Override
    public void render(ChunkRenderPass renderPass, ChunkRenderMatrices matrices_, int frameIndex) {
        if (renderPass == ChunkRenderPassManager.TRIPWIRE)
            return;
        var regions = ViewportedData.get().visible_regions;

        this.indexBuffer.ensureCapacity(1000000);//FIXME: not hardcoded
        int idi = -1;
        if (renderPass == ChunkRenderPassManager.SOLID) {
            idi = 0;
        } else if (renderPass == ChunkRenderPassManager.CUTOUT_MIPPED) {
            idi = 1;
        } else if (renderPass == ChunkRenderPassManager.CUTOUT) {
            idi = 2;
        } else if (renderPass == ChunkRenderPassManager.TRANSLUCENT) {
            idi = 3;
        }
        int rid = idi;
        ChunkRenderMatrices matrices = matrices_.copy();
        var dat = ViewportedData.get();
        ChunkCameraContext c = new ChunkCameraContext((dat.lastCameraX-dat.cameraX),
                (dat.lastCameraY-dat.cameraY),
                (dat.lastCameraZ-dat.cameraZ));
        float dx = (float) (c.deltaX+c.blockX);
        float dy = (float) (c.deltaY+c.blockY);
        float dz = (float) (c.deltaZ+c.blockZ);
        matrices.modelView().translate(dx,
                dy,
                dz
        );

        var pipeline = pipelines.get(renderPass);

        this.device.usePipeline(pipeline, (cmd, programInterface, pipelineState) -> {
            this.setupTextures(renderPass, pipelineState);
            //TODO: need to make matricies offset the actual chunk data, cause the instanced data passed in is 1 frame old of offset
            // so need to update the delta between the two
            this.setupUniforms(matrices, programInterface, pipelineState, frameIndex);

            cmd.bindElementBuffer(this.indexBuffer.getBuffer());
            //FIXME: reverse region rendering when rendering translucent objects



            //FIXME: find way to properly pass
            ArenaBuffer ab = SodiumWorldRenderer.instance().terrainRenderManager.regions.vertexBuffers;
            cmd.bindVertexBuffer(
                    BufferTarget.VERTICES,
                    ab.getBufferObject(),
                    0,
                    ab.getStride()
            );
            var vdata = ViewportedData.get();
            int count = vdata.cpuCommandCount.view().getInt(rid*4);
            if (count == 0) {
                return;
            }
            if (rid == 0) {
                cmd.bindCommandBuffer(vdata.cmd0buff);
            }
            if (rid == 1) {
                cmd.bindCommandBuffer(vdata.cmd1buff);
            }
            if (rid == 2) {
                cmd.bindCommandBuffer(vdata.cmd2buff);
            }
            if (rid == 3) {
                return;
            }

            pipelineState.bindBufferBlock(
                    programInterface.uniformInstanceData,
                    vdata.instanceBuffer,
                    0,
                    RenderRegion.REGION_SIZE*4*3
            );

            cmd.bindParameterBuffer(vdata.counterBuffer);


            cmd.multiDrawElementsIndirectCount(
                    PrimitiveType.TRIANGLES,
                    ElementFormat.UNSIGNED_INT,
                    0,
                    4+4*rid,//FIXME: need to select the index (0) from the current render layer
                    Math.min(Math.max((int)(count*1.5+Math.log(count)), 0), RenderRegion.REGION_SIZE * 5 * 4 * 6 - 5),
                    //(int)(Math.ceil(region.sectionCount*3.5)),//FIXME: optimize this to be as close bound as possible, maybe even make it dynamic based on previous counts
                    5*4
            );
        });
    }

    @Override
    public int getDeviceBufferObjects() {
        return 0;
    }

    @Override
    public long getDeviceUsedMemory() {
        return 0;
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return 0;
    }



    protected void setupTextures(ChunkRenderPass pass, PipelineState pipelineState) {
        pipelineState.bindTexture(
                0,
                TextureUtil.getBlockAtlasTexture(),
                pass.mipped() ? this.blockTextureMippedSampler : this.blockTextureSampler
        );
        pipelineState.bindTexture(1, TextureUtil.getLightTexture(), this.lightTextureSampler);
    }

    protected void setupUniforms(
            ChunkRenderMatrices renderMatrices,
            ChunkShaderInterface programInterface,
            PipelineState state,
            int frameIndex
    ) {
        StreamingBuffer.WritableSection matricesSection = this.uniformBufferCameraMatrices.getSection(frameIndex);
        long matricesPtr = MemoryUtil.memAddress(matricesSection.getView());

        // We write everything into a temporary buffer and check equality with the existing buffer to avoid unnecessary
        // flushes, which require api calls.
        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            long tempPtr = memoryStack.nmalloc(128);
            renderMatrices.projection().getToAddress(tempPtr);
            renderMatrices.modelView().getToAddress(tempPtr + 64);
            boolean equalContents = UnsafeUtil.nmemEquals(tempPtr, matricesPtr, 128);

            if (!equalContents) {
                MemoryUtil.memCopy(tempPtr, matricesPtr, 128);

                Matrix4f mvpMatrix = new Matrix4f();
                mvpMatrix.set(renderMatrices.projection());
                mvpMatrix.mul(renderMatrices.modelView());
                mvpMatrix.getToAddress(matricesPtr + 128);

                matricesSection.flushFull();
            }
        }

        state.bindBufferBlock(
                programInterface.uniformCameraMatrices,
                this.uniformBufferCameraMatrices.getBufferObject(),
                matricesSection.getDeviceOffset(),
                matricesSection.getView().capacity()
        );

        StreamingBuffer.WritableSection fogParamsSection = this.uniformBufferFogParameters.getSection(frameIndex);
        long fogParamsPtr = MemoryUtil.memAddress(fogParamsSection.getView());

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            long tempPtr = memoryStack.nmalloc(28);
            float[] paramFogColor = RenderSystem.getShaderFogColor();
            MemoryUtil.memPutFloat(tempPtr + 0, paramFogColor[0]);
            MemoryUtil.memPutFloat(tempPtr + 4, paramFogColor[1]);
            MemoryUtil.memPutFloat(tempPtr + 8, paramFogColor[2]);
            MemoryUtil.memPutFloat(tempPtr + 12, paramFogColor[3]);
            MemoryUtil.memPutFloat(tempPtr + 16, RenderSystem.getShaderFogStart());
            MemoryUtil.memPutFloat(tempPtr + 20, RenderSystem.getShaderFogEnd());
            MemoryUtil.memPutInt(tempPtr + 24, RenderSystem.getShaderFogShape().getId());
            boolean equalContents = UnsafeUtil.nmemEquals(tempPtr, fogParamsPtr, 28);

            if (!equalContents) {
                MemoryUtil.memCopy(tempPtr, fogParamsPtr, 28);
                fogParamsSection.flushFull();
            }
        }

        state.bindBufferBlock(
                programInterface.uniformFogParameters,
                this.uniformBufferFogParameters.getBufferObject(),
                fogParamsSection.getDeviceOffset(),
                fogParamsSection.getView().capacity()
        );
    }


    protected static ShaderConstants getShaderConstants(ChunkRenderPass pass, TerrainVertexType vertexType) {
        var constants = ShaderConstants.builder();

        if (pass.isCutout()) {
            constants.add("ALPHA_CUTOFF", String.valueOf(pass.alphaCutoff()));
        }

        if (!MathHelper.approximatelyEquals(vertexType.getVertexRange(), 1.0f)) {
            constants.add("VERT_SCALE", String.valueOf(vertexType.getVertexRange()));
        }

        return constants.build();
    }
}
