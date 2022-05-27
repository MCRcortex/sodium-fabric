package me.cortex.vulkenizer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.opengl.EXTMemoryObject.glNamedBufferStorageMemEXT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRExternalFence.VK_KHR_EXTERNAL_FENCE_EXTENSION_NAME;
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
import static org.lwjgl.vulkan.KHRMaintenance1.VK_KHR_MAINTENANCE1_EXTENSION_NAME;
import static org.lwjgl.vulkan.NVRayTracing.VK_NV_RAY_TRACING_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

public class Vulkanizer {
    public static final boolean VALIDATE = true;
    private static VkInstance instance;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice device;
    private static VkQueue graphicsQueue;
    public static void init_vulkan() {
        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }
        VKContext.Builder builder = new VKContext.Builder(VALIDATE, 1,1);
        //TODO: enqueue default queues, aftermathFlags
        builder.addInstanceExtension(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
        builder.addInstanceExtension(VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME);
        builder.addInstanceExtension(VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME);
        builder.addInstanceExtension(VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_MAINTENANCE1_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_EXTERNAL_FENCE_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME);
        builder.addDeviceExtension(VK_NV_RAY_TRACING_EXTENSION_NAME);
        builder.addDeviceExtension(VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME);
        VKContext context = builder.build();

        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);

            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8Safe("Hello Triangle"));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8Safe("No Engine"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK_API_VERSION_1_0);

            //Can use this to check avalible api ver
            //VkResult result = vkEnumerateInstanceVersion(&version);


            //Get instance layer properties
            vkEnumerateInstanceLayerProperties(ib, null);
            VkLayerProperties.Buffer layerPropertiesBuffer = VkLayerProperties.callocStack(ib.get(0), stack);
            vkEnumerateInstanceLayerProperties(ib, layerPropertiesBuffer);

            //TODO: check it has all the layers i want?


            //Get instance extension properties
            vkEnumerateInstanceExtensionProperties((ByteBuffer) null, ib, null);
            VkExtensionProperties.Buffer extensionPropertiesBuffer = VkExtensionProperties.callocStack(ib.get(0), stack);
            vkEnumerateInstanceExtensionProperties((ByteBuffer) null, ib, extensionPropertiesBuffer);

            //TODO: check it has all the extensions i want?

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);
            // enabledExtensionCount is implicitly set when you call ppEnabledExtensionNames
            createInfo.ppEnabledExtensionNames(glfwGetRequiredInstanceExtensions());
            // same with enabledLayerCount
            createInfo.ppEnabledLayerNames(null);

            // We need to retrieve the pointer of the created instance
            PointerBuffer instancePtr = stack.mallocPointer(1);

            if(vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create instance");
            }
            instance = new VkInstance(instancePtr.get(0), createInfo);
        }

        pickPhysicalDevice();
        createLogicalDevice();
    }

    private static void pickPhysicalDevice() {

        try(MemoryStack stack = stackPush()) {

            IntBuffer deviceCount = stack.ints(0);

            vkEnumeratePhysicalDevices(instance, deviceCount, null);

            if(deviceCount.get(0) == 0) {
                throw new RuntimeException("Failed to find GPUs with Vulkan support");
            }

            PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

            vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

            for(int i = 0;i < ppPhysicalDevices.capacity();i++) {

                VkPhysicalDevice device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

                if(isDeviceSuitable(device)) {
                    physicalDevice = device;
                    return;
                }
            }

            throw new RuntimeException("Failed to find a suitable GPU");
        }
    }

    private static class QueueFamilyIndices {

        // We use Integer to use null as the empty value
        private Integer graphicsFamily;

        private boolean isComplete() {
            return graphicsFamily != null;
        }

    }

    private static boolean isDeviceSuitable(VkPhysicalDevice device) {

        QueueFamilyIndices indices = findQueueFamilies(device);

        return indices.isComplete();
    }

    private static QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {

        QueueFamilyIndices indices = new QueueFamilyIndices();

        try(MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            IntStream.range(0, queueFamilies.capacity())
                    .filter(index -> (queueFamilies.get(index).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0)
                    .findFirst()
                    .ifPresent(index -> indices.graphicsFamily = index);

            return indices;
        }
    }

    private static void createLogicalDevice() {

        try(MemoryStack stack = stackPush()) {

            QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(1, stack);

            queueCreateInfos.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            queueCreateInfos.queueFamilyIndex(indices.graphicsFamily);
            queueCreateInfos.pQueuePriorities(stack.floats(1.0f));

            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.callocStack(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);
            // queueCreateInfoCount is automatically set

            createInfo.pEnabledFeatures(deviceFeatures);

            if(VALIDATE) {
                //createInfo.ppEnabledLayerNames(validationLayersAsPointerBuffer());
            }

            PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

            if(vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device");
            }

            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pGraphicsQueue = stack.pointers(VK_NULL_HANDLE);

            vkGetDeviceQueue(device, indices.graphicsFamily, 0, pGraphicsQueue);

            graphicsQueue = new VkQueue(pGraphicsQueue.get(0), device);
        }
    }
}
