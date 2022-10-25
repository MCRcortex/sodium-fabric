package net.caffeinemc.sodium.render.chunk.draw;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongList;
import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetsPooled;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorSetLayoutBuilder;
import me.cortex.vulkanitelib.memory.buffer.VGlVkBuffer;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.memory.image.VGlVkImage;
import me.cortex.vulkanitelib.memory.image.VVkFramebuffer;
import me.cortex.vulkanitelib.other.VVkCommandBuffer;
import me.cortex.vulkanitelib.other.VVkCommandPool;
import me.cortex.vulkanitelib.other.VVkQueue;
import me.cortex.vulkanitelib.pipelines.VVkPipeline;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;
import me.cortex.vulkanitelib.pipelines.VVkShader;
import me.cortex.vulkanitelib.pipelines.builders.GraphicsPipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.RenderPassBuilder;
import me.cortex.vulkanitelib.sync.VGlVkSemaphore;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.state.BlendFunc;
import net.caffeinemc.gfx.opengl.buffer.VkGlImmutableBuffer;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.vk.VulkanContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanChunkRenderer implements ChunkRenderer {
    public static class IndexBuffer {
        public VVkBuffer indexBuffer;
        public IndexBuffer(int quadCount) {//TODO: NEED TO MAP AND FILL BUFFER WITH THE INDEX DATA ssljkhfgdfjk NOTHING WILL WORK WITHOUT IT
            this(quadCount, 0);
        }
        public IndexBuffer(int quadCount, int flags) {
            ByteBuffer buffer = genQuadIdxs(quadCount*4);
            indexBuffer = VulkanContext.device.allocator.createBuffer(buffer.capacity(),
                    VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT | flags,
                    VK_MEMORY_HEAP_DEVICE_LOCAL_BIT);
            //TODO: NEED TO MAP AND FILL BUFFER WITH THE INDEX DATA ssljkhfgdfjk NOTHING WILL WORK WITHOUT IT
        }

        public static ByteBuffer genQuadIdxs(int vertexCount) {
            //short[] idxs = {0, 1, 2, 0, 2, 3};

            int indexCount = vertexCount * 3 / 2;
            ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Integer.BYTES);
            IntBuffer idxs = buffer.asIntBuffer();
            //short[] idxs = new short[indexCount];

            int j = 0;
            for(int i = 0; i < vertexCount; i += 4) {

                idxs.put(j, i);
                idxs.put(j + 1, (i + 1));
                idxs.put(j + 2, (i + 2));
                idxs.put(j + 3, (i));
                idxs.put(j + 4, (i + 2));
                idxs.put(j + 5, (i + 3));

                j += 6;
            }

            return buffer;
        }
    }

    private final RenderDevice glDevice;
    private final VVkDevice device = VulkanContext.device;
    protected final VVkPipeline[] renderPipelines;
    protected final int maxInFlightFrames;
    protected final VVkCommandPool commandPool;
    protected final VVkCommandBuffer[][] terrainCommandBuffers;//TODO: make these secondary buffers so that then
    protected final VVkDescriptorSetsPooled descriptorSets;
    protected final ChunkCameraContext cameraContext;
    protected final ChunkRenderPassManager renderPassManager;

    protected final VGlVkSemaphore[][] waitSemaphores;
    protected final VGlVkSemaphore[][] signalSemaphores;

    protected final VVkQueue queue;

    protected VVkFramebuffer theFrameBuffer;
    protected final int glFb;

    protected IndexBuffer indexBuffer;

    //protected final VVkBuffer[] uniformCameraData;
    //protected final VVkBuffer[] uniformFogData;


    public VulkanChunkRenderer(RenderDevice glDevice, ChunkCameraContext camera, ChunkRenderPassManager renderPassManager, TerrainVertexType vertexType) {
        this.glDevice = glDevice;
        cameraContext = camera;
        this.renderPassManager = renderPassManager;
        maxInFlightFrames = SodiumClientMod.options().advanced.cpuRenderAheadLimit + 1;
        terrainCommandBuffers = new VVkCommandBuffer[maxInFlightFrames][renderPassManager.getRenderPassCount()];
        waitSemaphores = new VGlVkSemaphore[maxInFlightFrames][renderPassManager.getRenderPassCount()];
        signalSemaphores = new VGlVkSemaphore[maxInFlightFrames][renderPassManager.getRenderPassCount()];

        queue = device.fetchQueue();

        indexBuffer = new IndexBuffer(100000);


        //TODO: (from: https://developer.nvidia.com/blog/vulkan-dos-donts/)
        //Use L * T + N pools. (L = the number of buffered frames, T = the number of threads that record command buffers, N = extra pools for secondary command buffers).
        //Call vkResetCommandPool before reusing it in another frame. Otherwise, the pool will keep on growing until you run out of memory
        commandPool = device.createCommandPool(0, VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        VVkCommandBuffer[] allCmds = commandPool.createCommandBuffers(maxInFlightFrames*renderPassManager.getRenderPassCount());
        for (int i = 0; i < maxInFlightFrames; i++) {
            for (int j = 0; j < renderPassManager.getRenderPassCount(); j++) {
                terrainCommandBuffers[i][j] = allCmds[i*renderPassManager.getRenderPassCount() + j];
                waitSemaphores[i][j] = device.createSharedSemaphore();
                signalSemaphores[i][j] = device.createSharedSemaphore();
            }
        }


        renderPipelines = new VVkPipeline[renderPassManager.getRenderPassCount()];
        VVkRenderPass renderPass = device.build(new RenderPassBuilder()
                .attachment(VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_ATTACHMENT_LOAD_OP_LOAD)//FIXME: the ops are probably wrong, like LOAD CLEAR is not what we want
                .attachment(VK_FORMAT_D24_UNORM_S8_UINT, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VK_ATTACHMENT_LOAD_OP_LOAD)//FIXME: the ops are probably wrong, like LOAD CLEAR is not what we want
                .subpass(VK_PIPELINE_BIND_POINT_GRAPHICS, 1,0));

        VVkDescriptorSetLayout uniformLayout = device.build(new DescriptorSetLayoutBuilder()
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT)//CameraMatrices
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT)//ChunkTransforms
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT)//FogParameters
                .binding(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)//tex_diffuse
                .binding(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_VERTEX_BIT)//tex_light
        );

        descriptorSets = uniformLayout.createDescriptorSetsAndPool(maxInFlightFrames);

        GraphicsPipelineBuilder pipelineBuilder = new GraphicsPipelineBuilder()
                //Doing compact format
                .addVertexInput(0, vertexType.getBufferVertexFormat().stride(), input->
                    input.attribute(VK_FORMAT_R16G16B16_SNORM,0)//POSITION
                            .attribute(VK_FORMAT_R8G8B8A8_UNORM, 8)//COLOR
                            .attribute(VK_FORMAT_R16G16_UNORM, 12)//BLOCK_TEXTURE
                            .attribute(VK_FORMAT_R16G16_SINT,16)//LIGHT_TEXTURE
                )
                .set(renderPass)
                .add(uniformLayout)
                .addDynamicStates(VK_DYNAMIC_STATE_DEPTH_BIAS, VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)
                .rasterization(true, false, VK_CULL_MODE_BACK_BIT)
                .inputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .multisampling()
                .addViewport()
                .addScissor()
                .depthStencil();

        for (ChunkRenderPass chunkRenderPass : renderPassManager.getAllRenderPasses()) {

            var constants = this.addAdditionalShaderConstants(getBaseShaderConstants(chunkRenderPass, vertexType)).build();

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

            VVkShader vert = device.compileShader(vertShader, VK_SHADER_STAGE_VERTEX_BIT);
            VVkShader frag = device.compileShader(fragShader, VK_SHADER_STAGE_FRAGMENT_BIT);
            pipelineBuilder.clearShaders()
                    .add(vert).add(frag);

            BlendFunc bf = chunkRenderPass.getPipelineDescription().blendFunc;//Ignoring the others atm cause not used
            if (bf != null) {
                pipelineBuilder.colourBlending().attachment();//FIXME: NEED TO ADD MAPPING OF TYPE
            } else
                pipelineBuilder.colourBlending().attachment();

            this.renderPipelines[chunkRenderPass.getId()] = device.build(pipelineBuilder);
        }



        VGlVkImage gc = device.exportedAllocator.createShared2DImage(800,800, 1, VK_FORMAT_R8G8B8A8_UNORM, GL_RGBA8, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        VGlVkImage gd = device.exportedAllocator.createShared2DImage(800,800, 1, VK_FORMAT_D24_UNORM_S8_UINT, GL_DEPTH24_STENCIL8, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT , VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        theFrameBuffer = device.createFramebuffer(renderPass, gc.createView(VK_IMAGE_ASPECT_COLOR_BIT), gd.createView(VK_IMAGE_ASPECT_DEPTH_BIT|VK_IMAGE_ASPECT_STENCIL_BIT));
        glFb = glCreateFramebuffers();
        glNamedFramebufferTexture(glFb, GL_COLOR_ATTACHMENT0, gc.glId, 0);
    }

    protected static ShaderConstants.Builder getBaseShaderConstants(ChunkRenderPass pass, TerrainVertexType vertexType) {
        var constants = ShaderConstants.builder();

        if (pass.isCutout()) {
            constants.add("ALPHA_CUTOFF", String.valueOf(pass.getAlphaCutoff()));
        }

        if (!MathHelper.approximatelyEquals(vertexType.getVertexRange(), 1.0f)) {
            constants.add("VERT_SCALE", String.valueOf(vertexType.getVertexRange()));
        }

        return constants;
    }

    protected ShaderConstants.Builder addAdditionalShaderConstants(ShaderConstants.Builder constants) {
        constants.add("BASE_INSTANCE_INDEX");
        constants.add("MAX_BATCH_SIZE", String.valueOf(RenderRegion.REGION_SIZE));
        return constants; // NOOP, override if needed
    }

    protected static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraDeltaPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraDeltaPos;
    }

    @Override
    public void createRenderLists(SortedTerrainLists lists, int frameIndex) {

        BlockPos cameraBlockPos = this.cameraContext.getBlockPos();
        float cameraDeltaX = this.cameraContext.getDeltaX();
        float cameraDeltaY = this.cameraContext.getDeltaY();
        float cameraDeltaZ = this.cameraContext.getDeltaZ();

        frameIndex %= terrainCommandBuffers.length;
        VVkCommandBuffer[] cmds = terrainCommandBuffers[frameIndex];//TODO:FIXME: this can be a primary level as each render pass renders different terrain so needs different buffer anyway
        for (ChunkRenderPass renderPass : renderPassManager.getAllRenderPasses()) {//Doing it renderPass wise not region wise
            VVkCommandBuffer cmd = cmds[renderPass.getId()];
            _CHECK_(vkResetCommandBuffer(cmd.buffer, 0));
            cmd.begin();
            cmd.beginRenderPass(theFrameBuffer);//TODO: IT MUST NOT CLEAR THE FRAMEBUFFER
            cmd.bind(renderPipelines[renderPass.getId()]);
            cmd.bind(descriptorSets, frameIndex);

            vkCmdBindIndexBuffer(cmd.buffer, indexBuffer.indexBuffer.buffer, 0, VK_INDEX_TYPE_UINT32);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // Update dynamic viewport state
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .height(800)
                        .width(800)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                vkCmdSetViewport(cmd.buffer, 0, viewport);

                // Update dynamic scissor state
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.extent().set(800, 800);
                scissor.offset().set(0, 0);

                vkCmdSetScissor(cmd.buffer, 0, scissor);
                vkCmdSetDepthBias(cmd.buffer, 0, 0.0f, 0);
            }




            IntList passRegionIndices = lists.regionIndices[renderPass.getId()];
            List<IntList> passModelPartCounts = lists.modelPartCounts[renderPass.getId()];
            List<LongList> passModelPartSegments = lists.modelPartSegments[renderPass.getId()];
            List<IntList> passSectionIndices = lists.sectionIndices[renderPass.getId()];
            int passRegionCount = passRegionIndices.size();

            boolean reverseOrder = renderPass.isTranslucent();

            int regionIdx = reverseOrder ? passRegionCount - 1 : 0;
            while (reverseOrder ? (regionIdx >= 0) : (regionIdx < passRegionCount)) {
                IntList regionPassModelPartCounts = passModelPartCounts.get(regionIdx);
                LongList regionPassModelPartSegments = passModelPartSegments.get(regionIdx);
                IntList regionPassSectionIndices = passSectionIndices.get(regionIdx);

                int fullRegionIdx = passRegionIndices.getInt(regionIdx);
                RenderRegion region = lists.regions.get(fullRegionIdx);
                IntList regionSectionCoords = lists.sectionCoords.get(fullRegionIdx);
                LongList regionUploadedSegments = lists.uploadedSegments.get(fullRegionIdx);
                int regionPassSectionCount = regionPassSectionIndices.size();
                if (reverseOrder) {
                    regionIdx--;
                } else {
                    regionIdx++;
                }

                cmd.bindVertexs((VGlVkBuffer)((VkGlImmutableBuffer)region.getVertexBuffer().getBufferObject()).data);





                int regionPassModelPartIdx = reverseOrder ? regionPassModelPartSegments.size() - 1 : 0;
                int regionPassModelPartCount = 0;
                int sectionIdx = reverseOrder ? regionPassSectionCount - 1 : 0;
                while (reverseOrder ? (sectionIdx >= 0) : (sectionIdx < regionPassSectionCount)) {
                    int sectionModelPartCount = regionPassModelPartCounts.getInt(sectionIdx);

                    int fullSectionIdx = regionPassSectionIndices.getInt(sectionIdx);
                    long sectionUploadedSegment = regionUploadedSegments.getLong(fullSectionIdx);

                    int sectionCoordsIdx = fullSectionIdx * 3;
                    int sectionCoordX = regionSectionCoords.getInt(sectionCoordsIdx);
                    int sectionCoordY = regionSectionCoords.getInt(sectionCoordsIdx + 1);
                    int sectionCoordZ = regionSectionCoords.getInt(sectionCoordsIdx + 2);
                    // don't use fullSectionIdx or sectionIdx past here
                    if (reverseOrder) {
                        sectionIdx--;
                    } else {
                        sectionIdx++;
                    }

                    // this works because the segment is in units of vertices
                    int baseVertex = BufferSegment.getOffset(sectionUploadedSegment);

                    float x = getCameraTranslation(
                            ChunkSectionPos.getBlockCoord(sectionCoordX),
                            cameraBlockPos.getX(),
                            cameraDeltaX
                    );
                    float y = getCameraTranslation(
                            ChunkSectionPos.getBlockCoord(sectionCoordY),
                            cameraBlockPos.getY(),
                            cameraDeltaY
                    );
                    float z = getCameraTranslation(
                            ChunkSectionPos.getBlockCoord(sectionCoordZ),
                            cameraBlockPos.getZ(),
                            cameraDeltaZ
                    );

                    for (int i = 0; i < sectionModelPartCount; i++) {
                        long modelPartSegment = regionPassModelPartSegments.getLong(regionPassModelPartIdx);

                        // don't use regionPassModelPartIdx past here (in this loop)
                        if (reverseOrder) {
                            regionPassModelPartIdx--;
                        } else {
                            regionPassModelPartIdx++;
                        }

                        /*
                        long ptr = transformsBufferSectionAddress + transformsBufferPosition;
                        MemoryUtil.memPutFloat(ptr, x);
                        MemoryUtil.memPutFloat(ptr + 4, y);
                        MemoryUtil.memPutFloat(ptr + 8, z);
                        //transformsBufferPosition += TRANSFORM_STRUCT_STRIDE;*/
                        vkCmdDrawIndexed(cmd.buffer,
                                6 * (BufferSegment.getLength(modelPartSegment) >> 2),
                                1,
                                0,
                                baseVertex + BufferSegment.getOffset(modelPartSegment),
                                0);
                    }

                    regionPassModelPartCount += sectionModelPartCount;

                    //largestVertexIndex = Math.max(largestVertexIndex, BufferSegment.getLength(sectionUploadedSegment));
                }

            }

            cmd.endRenderPass();
            cmd.end();
        }
    }


    //NOTE: SYNCING IS VERY VERY SLOW!!!!!!!!!!!! NEED TO BATCH ALL VULKAN STUFF TOGETHER INTO 1 SYNC CALL
    @Override
    public void render(ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex) {
        frameIndex %= terrainCommandBuffers.length;
        VGlVkSemaphore waitSem = waitSemaphores[frameIndex][renderPass.getId()];
        VGlVkSemaphore signalSem = signalSemaphores[frameIndex][renderPass.getId()];

        waitSem.glSignal();//TODO: provide the framebuffer depth and colour texture
        queue.submit(terrainCommandBuffers[frameIndex][renderPass.getId()], waitSem, VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT, signalSem, null);//TODO: need to basicly submit all the layers at once with correct ordering, that way it can work on multiple render passes at the same time
        //queue.submit(terrainCommandBuffers[frameIndex][renderPass.getId()]);
        //vkQueueWaitIdle(queue.queue);
        signalSem.glSignal();//TODO: provide the framebuffer depth and colour texture

        if (true){
            glBindFramebuffer(GL_READ_FRAMEBUFFER, glFb);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, MinecraftClient.getInstance().getFramebuffer().fbo);
            glBlitFramebuffer(0, 0, 800, 800,
                    0, 0, 800, 800,
                    GL_COLOR_BUFFER_BIT,
                    GL_LINEAR);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        }
    }

    @Override
    public void delete() {

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

    @Override
    public String getDebugName() {
        return "Vulkan";
    }
}
