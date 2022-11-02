package net.caffeinemc.sodium.vk;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.vulkanitelib.VContextBuilder;
import me.cortex.vulkanitelib.VVkContext;
import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.image.VGlVkImage;
import me.cortex.vulkanitelib.other.VVkCommandPool;
import org.lwjgl.vulkan.VkPhysicalDeviceAccelerationStructureFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceBufferDeviceAddressFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceRayQueryFeaturesKHR;

import static org.lwjgl.opengl.GL11C.GL_RENDERER;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceCapabilities.VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceWin32.VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderDrawParameters.VK_KHR_SHADER_DRAW_PARAMETERS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderFloatControls.VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSpirv14.VK_KHR_SPIRV_1_4_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;

public class VulkanContext {
    public static final VVkContext context;
    public static final VVkDevice device;
    public static final Int2ObjectOpenHashMap<VGlVkImage> gl2vk_textures = new Int2ObjectOpenHashMap<>();
    public static VGlVkImage colorTex;
    public static VGlVkImage depthTex;
    static {glGetString(GL_RENDERER);
        context = new VContextBuilder(true)
                .setVersion(1,3)
                .addInstanceExtensions(
                        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME)
                .addDeviceExtensions(
                        VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME,
                        VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME,
                        VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME,
                        VK_KHR_SPIRV_1_4_EXTENSION_NAME,
                        VK_KHR_SHADER_DRAW_PARAMETERS_EXTENSION_NAME//,

                        //VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME,
                        //VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
                )

                .addDeviceExtension(VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME, (stack)->
                        VkPhysicalDeviceBufferDeviceAddressFeaturesKHR
                                .calloc(stack)
                                .sType$Default()
                                .bufferDeviceAddress(true))
                /*
                .addDeviceExtension(VK_KHR_RAY_QUERY_EXTENSION_NAME, (stack)->
                        VkPhysicalDeviceRayQueryFeaturesKHR
                                .calloc(stack)
                                .sType$Default()
                                .rayQuery(true))
                .addDeviceExtension(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME, (stack)->
                        VkPhysicalDeviceAccelerationStructureFeaturesKHR
                                .calloc(stack)
                                .sType$Default()
                                .accelerationStructure(true))

                 */
                .gpuFilter(glGetString(GL_RENDERER).split("/")[0])
                .create();
        device = context.getDevice();
        System.out.println("Successfully initialized vulkan backend");
    }
}
