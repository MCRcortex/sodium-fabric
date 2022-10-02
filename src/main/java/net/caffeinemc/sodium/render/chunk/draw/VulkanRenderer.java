package net.caffeinemc.sodium.render.chunk.draw;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.array.VertexArrayResourceBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeBinding;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.pipeline.RenderPipeline;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.gfx.opengl.buffer.GlBuffer;
import net.caffeinemc.gfx.opengl.texture.GlTexture;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.render.SodiumWorldRenderer;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPassManager;
import net.caffeinemc.sodium.render.chunk.region.GlobalSingleBufferProvider;
import net.caffeinemc.sodium.render.chunk.region.RenderRegion;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderBindingPoints;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.util.TextureUtil;
import net.caffeinemc.sodium.util.collections.BitArray;
import net.caffeinemc.sodium.vkinterop.Sync;
import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import net.caffeinemc.sodium.vkinterop.vk.SDescriptorDescription;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.SVkGraphicsPipelineBuilder;
import net.caffeinemc.sodium.vkinterop.vk.cq.SVkCommandBuffer;
import net.caffeinemc.sodium.vkinterop.vk.cq.SVkCommandPool;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkGlBuffer;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.*;
import net.caffeinemc.sodium.vkinterop.vk.pipeline.*;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.List;

import static net.caffeinemc.sodium.render.chunk.draw.AbstractMdChunkRenderer.getBaseShaderConstants;
import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.opengl.EXTSemaphore.GL_LAYOUT_GENERAL_EXT;
import static org.lwjgl.opengl.EXTSemaphore.glSignalSemaphoreEXT;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanRenderer implements ChunkRenderer {
    public static final int TRANSFORM_STRUCT_STRIDE = 4 * Float.BYTES;
    public static final int CAMERA_MATRICES_SIZE = 192;
    public static final int FOG_PARAMETERS_SIZE = 32;
    public static final Int2ObjectOpenHashMap<SVkGlImage> TEXTURE_MAP = new Int2ObjectOpenHashMap<>();
    public final int TRANSFORM_SIZE = RenderRegion.REGION_SIZE * TRANSFORM_STRUCT_STRIDE;

    protected final RenderDevice glDevice;
    protected final SVkDevice device;
    protected final ChunkCameraContext camera;
    protected final ChunkRenderPassManager renderPassManager;
    SVkPipeline[] renderPipelines;
    Sync.GlVkSemaphore[] readySemaphores;
    Sync.GlVkSemaphore[] finishSemaphores;
    SVkCommandPool commandPool;
    VkCommandBuffer[] commandBuffers;
    SVkRenderPass renderPass;
    int maxInFlightFrames;

    SVkDescriptorPool[] descriptorPool;
    SVkDescriptorSet[][] descriptorSets;

    SVkGlBuffer[][] uniforms;

    IndexBufferVK indexBuffer;

    SVkFramebuffer theFrameBuffer;

    public VulkanRenderer(RenderDevice glDevice, ChunkCameraContext camera, ChunkRenderPassManager renderPassManager, TerrainVertexType vertexType) {
        this.glDevice = glDevice;
        this.camera = camera;
        this.device = SVkDevice.INSTANCE;
        this.renderPassManager = renderPassManager;
        indexBuffer = new IndexBufferVK(1000000);

        maxInFlightFrames = SodiumClientMod.options().advanced.cpuRenderAheadLimit + 1;
        readySemaphores = new Sync.GlVkSemaphore[maxInFlightFrames];
        finishSemaphores = new Sync.GlVkSemaphore[maxInFlightFrames];
        uniforms = new SVkGlBuffer[maxInFlightFrames][3];
        for (int i = 0; i < maxInFlightFrames; i++) {
            readySemaphores[i] = new Sync.GlVkSemaphore();
            finishSemaphores[i] = new Sync.GlVkSemaphore();
        }

        renderPass = new SVkRenderPass(device, VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_D24_UNORM_S8_UINT);

        SVkGlImage colour = device.m_alloc_e.createVkGlImage(800,800, VK_FORMAT_R8G8B8A8_UNORM, GL_RGBA8,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        SVkGlImage depth = device.m_alloc_e.createVkGlImage(800,800, VK_FORMAT_D24_UNORM_S8_UINT, GL_DEPTH_COMPONENT24,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        theFrameBuffer = new SVkFramebuffer(device, renderPass, 800, 800,
                    new SVkImageView(device, colour, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1),
                    new SVkImageView(device, depth, VK_FORMAT_D24_UNORM_S8_UINT, VK_IMAGE_ASPECT_DEPTH_BIT, 1)
                );

        commandPool = new SVkCommandPool(device, VkContextTEMP.findQueueFamilies(VkContextTEMP.getDevice().getPhysicalDevice()).graphicsFamily);//FIXME: make use a class instead
        commandBuffers = SVkCommandBuffer.createCommandBuffers(device, commandPool, maxInFlightFrames);


        renderPipelines = new SVkPipeline[renderPassManager.getRenderPassCount()];
        descriptorPool = new SVkDescriptorPool[renderPassManager.getRenderPassCount()];
        descriptorSets = new SVkDescriptorSet[renderPassManager.getRenderPassCount()][maxInFlightFrames];
        var vertexFormat = vertexType.getCustomVertexFormat();
        var vertexArray = new VertexArrayDescription<>(
                AbstractChunkRenderer.BufferTarget.values(),
                List.of(new VertexArrayResourceBinding<>(
                        AbstractChunkRenderer.BufferTarget.VERTICES,
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
        for (int i = 0; i < maxInFlightFrames; i++) {
            uniforms[i][0] = device.m_alloc_e.createVkGlBuffer(CAMERA_MATRICES_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_HEAP_DEVICE_LOCAL_BIT, 1);
            uniforms[i][1] = device.m_alloc_e.createVkGlBuffer(FOG_PARAMETERS_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_HEAP_DEVICE_LOCAL_BIT, 1);
            uniforms[i][2] = device.m_alloc_e.createVkGlBuffer(TRANSFORM_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_HEAP_DEVICE_LOCAL_BIT, TRANSFORM_STRUCT_STRIDE);
        }
        SVkGlImage blockAtlas = TEXTURE_MAP.getOrDefault(GlTexture.getHandle(TextureUtil.getBlockAtlasTexture()), null);
        SVkGlImage lightTexture = TEXTURE_MAP.getOrDefault(GlTexture.getHandle(TextureUtil.getLightTexture()), null);

        SVkImageView blockAtlasView = new SVkImageView(device, blockAtlas, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 4);
        SVkImageView lightTexView = new SVkImageView(device, lightTexture, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
        SVkSampler sampler = new SVkSampler(device, 4);
        SVkVertexInputBindingAttributeDescription vad = new SVkVertexInputBindingAttributeDescription(vertexArray, vertexFormat.stride());
        for (ChunkRenderPass crenderPass : renderPassManager.getAllRenderPasses()) {
            var constants = this.addAdditionalShaderConstants(getBaseShaderConstants(crenderPass, vertexType)).build();

            var vertShaderSource = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.vert"),
                    constants
            );
            var fragShaderSource = ShaderParser.parseSodiumShader(
                    ShaderLoader.MINECRAFT_ASSETS,
                    new Identifier("sodium", "terrain/terrain_opaque.frag"),
                    constants
            );
            var vertShader = new SVkShader(SVkDevice.INSTANCE, vertShaderSource, ShaderType.VERTEX);
            var fragShader = new SVkShader(SVkDevice.INSTANCE, fragShaderSource, ShaderType.FRAGMENT);
            SVkDescriptorSetLayout descriptorLayout = new SVkDescriptorSetLayout(SVkDevice.INSTANCE,
                    new SDescriptorDescription()
                            .add(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                            .add(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                            .add(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                            .add(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                            .add(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
            );
            descriptorPool[crenderPass.getId()] = new SVkDescriptorPool(device, descriptorLayout, maxInFlightFrames);
            descriptorSets[crenderPass.getId()] = descriptorPool[crenderPass.getId()].allocateMax();

            //HACK TODO: MOVE TO CLASS SYSTEM
            try (MemoryStack stack = stackPush()){
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(3, stack);
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(2, stack);
                imageInfo.get(0).imageLayout(VK_IMAGE_LAYOUT_UNDEFINED).imageView(blockAtlasView.view).sampler(sampler.sampler);
                imageInfo.get(1).imageLayout(VK_IMAGE_LAYOUT_UNDEFINED).imageView(lightTexView.view).sampler(sampler.sampler);
                VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(5, stack);
                descriptorWrites.get(0).sType$Default().dstBinding(0).dstArrayElement(0).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1).pBufferInfo(bufferInfo);
                descriptorWrites.get(1).sType$Default().dstBinding(1).dstArrayElement(0).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1).pBufferInfo(bufferInfo);
                descriptorWrites.get(2).sType$Default().dstBinding(2).dstArrayElement(0).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1).pBufferInfo(bufferInfo);
                descriptorWrites.get(3).sType$Default().dstBinding(3).dstArrayElement(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1).pImageInfo(imageInfo);
                descriptorWrites.get(4).sType$Default().dstBinding(4).dstArrayElement(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1).pImageInfo(imageInfo);
                for (int i = 0; i < maxInFlightFrames; i++) {
                    descriptorWrites.get(0).dstSet(descriptorSets[crenderPass.getId()][i].set);
                    descriptorWrites.get(1).dstSet(descriptorSets[crenderPass.getId()][i].set);
                    descriptorWrites.get(2).dstSet(descriptorSets[crenderPass.getId()][i].set);
                    descriptorWrites.get(3).dstSet(descriptorSets[crenderPass.getId()][i].set);
                    descriptorWrites.get(4).dstSet(descriptorSets[crenderPass.getId()][i].set);
                    bufferInfo.get(0).offset(0).range(CAMERA_MATRICES_SIZE).buffer(uniforms[i][0].buffer);
                    bufferInfo.get(1).offset(0).range(FOG_PARAMETERS_SIZE).buffer(uniforms[i][1].buffer);
                    bufferInfo.get(2).offset(0).range(TRANSFORM_SIZE).buffer(uniforms[i][2].buffer);

                    vkUpdateDescriptorSets(device.device, descriptorWrites, null);
                }
            }

            SVkPipelineLayout pipelineLayout = new SVkPipelineLayout(SVkDevice.INSTANCE, descriptorLayout);
            renderPipelines[crenderPass.getId()] =
                        new SVkGraphicsPipelineBuilder(device,
                            renderPass,
                            vad,
                            crenderPass.getPipelineDescription(),
                            new SVkShader[]{vertShader, fragShader},
                            pipelineLayout)
                                    .createPipeline();
        }
    }

    protected ShaderConstants.Builder addAdditionalShaderConstants(ShaderConstants.Builder constants) {
        constants.add("BASE_INSTANCE_INDEX");
        constants.add("MAX_BATCH_SIZE", String.valueOf(RenderRegion.REGION_SIZE));
        return constants; // NOOP, override if needed
    }

    @Override
    public void createRenderLists(SortedTerrainLists lists, int frameIndex) {

    }

    @Override
    public void render(ChunkRenderPass renderPass, ChunkRenderMatrices matrices, int frameIndex) {
        SVkPipeline pipeline = renderPipelines[renderPass.getId()];
        int fid = frameIndex % maxInFlightFrames;
        VkCommandBuffer commandBuffer = commandBuffers[fid];
        try (MemoryStack stack = stackPush()) {
            glSignalSemaphoreEXT(readySemaphores[fid].glSemaphore, new int[]{}, new int[]{}, new int[]{GL_LAYOUT_GENERAL_EXT});

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();
            if(vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer");
            }
            if (true) {
                VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
                clearValues.get(0).color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
                clearValues.get(1).depthStencil().set(1.0f, 0);

                VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(this.renderPass.renderPass)
                        .framebuffer(theFrameBuffer.framebuffer)
                        .pClearValues(clearValues);

                vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline);



                // Update dynamic viewport state
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .height(800)
                        .width(800)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                vkCmdSetViewport(commandBuffer, 0, viewport);

                // Update dynamic scissor state
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.extent().set(800, 800);
                scissor.offset().set(0, 0);

                vkCmdSetScissor(commandBuffer, 0, scissor);
                vkCmdSetDepthBias(commandBuffer, 0, 0.0f, 0);
                {

                    LongBuffer vertexBuffers = stack.longs(((GlobalSingleBufferProvider.GlVkImmutableBuffer)SodiumWorldRenderer.instance().getTerrainRenderer().regionManager.getGlobalVertexBufferTHISISTEMPORARY()).buffer.buffer);
                    LongBuffer offsets = stack.longs(0);
                    vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets);


                    vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.pipelineLayout.layout, 0, stack.longs(descriptorSets[renderPass.getId()][fid].set), null);
                    vkCmdBindIndexBuffer(commandBuffer, indexBuffer.indexBuffer.buffer, 0, VK_INDEX_TYPE_UINT32);
                    vkCmdDrawIndexed(commandBuffer, 3*2*10,1, 0, 0, 0);
                }
                vkCmdEndRenderPass(commandBuffer);
            }

            if(vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to record command buffer");
            }

            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer))
                    .pWaitSemaphores(stack.longs(readySemaphores[fid].vkSemaphore))
                    .pSignalSemaphores(stack.longs(finishSemaphores[fid].vkSemaphore));

            vkQueueSubmit(VkContextTEMP.getGraphicsQueue(), submitInfo, 0);
            glSignalSemaphoreEXT(finishSemaphores[fid].glSemaphore, new int[]{}, new int[]{}, new int[]{GL_LAYOUT_GENERAL_EXT});
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
