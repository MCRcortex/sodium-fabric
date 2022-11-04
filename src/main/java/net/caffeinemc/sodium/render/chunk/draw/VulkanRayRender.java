package net.caffeinemc.sodium.render.chunk.draw;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetsPooled;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorSetLayoutBuilder;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorUpdateBuilder;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.memory.image.VVkFramebuffer;
import me.cortex.vulkanitelib.memory.image.VVkImageView;
import me.cortex.vulkanitelib.other.VVkCommandBuffer;
import me.cortex.vulkanitelib.other.VVkCommandPool;
import me.cortex.vulkanitelib.pipelines.VVkGraphicsPipeline;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;
import me.cortex.vulkanitelib.pipelines.builders.GraphicsPipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.RenderPassBuilder;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.vk.VulkanContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4x3f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanRayRender {
    private final VVkDevice device;
    private final VVkBuffer[] cameraData;
    private final VVkCommandBuffer[] buffers;
    private final VVkRenderPass renderPass;
    private final VVkCommandPool commandPool;
    private final int inflightFrames;
    protected final VVkDescriptorSetsPooled descriptorSets;
    final VVkGraphicsPipeline compositePipeline;
    final VVkFramebuffer theFramebuffer;
    public VulkanRayRender(VVkDevice device, int inflightFrames, VVkImageView destImage) {
        this.device = device;
        this.inflightFrames = inflightFrames;
        this.cameraData = new VVkBuffer[inflightFrames];
        commandPool = device.createCommandPool(0, VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        renderPass = device.build(new RenderPassBuilder()
                .attachment(VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_ATTACHMENT_LOAD_OP_LOAD)
                .subpass(VK_PIPELINE_BIND_POINT_GRAPHICS, -1,0));

        this.buffers = commandPool.createCommandBuffers(inflightFrames, VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        VVkDescriptorSetLayout layout = device.build(new DescriptorSetLayoutBuilder()
                .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT)//camera data
                .binding(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, VK_SHADER_STAGE_FRAGMENT_BIT)//funni acceleration buffer
                .binding(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT)//funni buffer buffer
        );
        descriptorSets = layout.createDescriptorSetsAndPool(inflightFrames);

        var constants = ShaderConstants.builder().build();

        var vertShader = ShaderParser.parseSodiumShader(
                ShaderLoader.MINECRAFT_ASSETS,
                new Identifier("sodium", "raytracing_composite.frag"),
                constants
        );

        GraphicsPipelineBuilder pipelineBuilder = new GraphicsPipelineBuilder()
                .set(renderPass)
                .add(layout)
                .addDynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)
                .rasterization(false, false, VK_CULL_MODE_NONE)
                .inputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN)
                .multisampling()
                .addViewport()
                .addScissor()
                .depthStencil()//TODO: DISABLE DEPTH TEST AND DEPTH WRITE
                .colourBlending().attachment().end()
                .add(device.compileShader("""
                        #version 420 core
                        layout(location = 0) out vec3 pos;
                        void main(void) {
                            //output the position of each vertex
                            //const array of positions for the triangle
                            const vec3 positions[4] = vec3[4](
                                vec3(0.f,0.f, 0.0f),
                                vec3(0.f,1.f, 0.0f),
                                vec3(1.f,1.f, 0.0f),
                                vec3(1.f,0.f, 0.0f)
                            );
                            pos = positions[gl_VertexIndex];
                            //output the position of each vertex
                            gl_Position = vec4(positions[gl_VertexIndex], 1.0f);
                        }
                        """, VK_SHADER_STAGE_VERTEX_BIT))
                .add(device.compileShader(vertShader, VK_SHADER_STAGE_FRAGMENT_BIT));

        compositePipeline = device.build(pipelineBuilder);

        for (int i = 0; i < inflightFrames; i++) {
            cameraData[i] = device.allocator.createBuffer(1024, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
        DescriptorUpdateBuilder dub = new DescriptorUpdateBuilder(layout).buffer(0, cameraData, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        descriptorSets.update(dub);

        theFramebuffer = device.createFramebuffer(renderPass, destImage);
    }
    boolean ready;
    public void render(int frameId, ChunkRenderMatrices crm, Vector3f cameraOffset) {

        if (VulkanContext.acceleration.tick()) {
            ready = true;
            System.out.println("acceleration update");
            DescriptorUpdateBuilder dub = new DescriptorUpdateBuilder(descriptorSets.layouts[0])
                    .acceleration(1, VulkanContext.acceleration.tlas)
                    .sbuffer(2, VulkanContext.acceleration.gpuBlasRefBuffer);
            descriptorSets.update(dub);
        }
        if (!ready)
            return;
        var mapped = cameraData[frameId].map();
        Vector3f tmpv3 = new Vector3f();
        Matrix4f invProjMatrix = new Matrix4f();
        Matrix4f invViewMatrix = new Matrix4f();

        crm.projection().invert(invProjMatrix);
        crm.modelView().translate(cameraOffset.negate()).invert(invViewMatrix);
        invProjMatrix.transformProject(-1, -1, 0, 1, tmpv3).get(mapped);
        invProjMatrix.transformProject(+1, -1, 0, 1, tmpv3).get(4*Float.BYTES, mapped);
        invProjMatrix.transformProject(-1, +1, 0, 1, tmpv3).get(8*Float.BYTES, mapped);
        invProjMatrix.transformProject(+1, +1, 0, 1, tmpv3).get(12*Float.BYTES, mapped);
        invViewMatrix.get(Float.BYTES * 16, mapped);
        cameraData[frameId].unmap();

        device.singleTimeCommand(cmd->{//TODO: not use single time commands
            cmd.beginRenderPass(theFramebuffer);
            try (MemoryStack stack = MemoryStack.stackPush()){
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .width(theFramebuffer.width)
                        .height(theFramebuffer.height)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                vkCmdSetViewport(cmd.buffer, 0, viewport);

                // Update dynamic scissor state
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.extent().set(theFramebuffer.width, theFramebuffer.height);
                cmd.bind(compositePipeline);
                cmd.bind(descriptorSets, frameId);

                vkCmdSetScissor(cmd.buffer, 0, scissor);


                VkClearAttachment.Buffer clearAttachments = VkClearAttachment.calloc(1, stack);
                clearAttachments.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).clearValue().color().float32(0, 1).float32(1, 0).float32(2, 1).float32(3, 1);
                VkClearRect.Buffer clearRects = VkClearRect.calloc(1, stack);
                clearRects.get(0).layerCount(1).rect().extent().set(theFramebuffer.width/2, theFramebuffer.height/2);
                //vkCmdClearAttachments(cmd.buffer, clearAttachments, clearRects);
            }
            vkCmdDraw(cmd.buffer, 4, 1, 0, 0);
            cmd.endRenderPass();
            }, ()->{});
    }
}
