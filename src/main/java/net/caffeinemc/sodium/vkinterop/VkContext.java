package net.caffeinemc.sodium.vkinterop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.List;

import static net.caffeinemc.sodium.vkinterop.VkUtils.translateVulkanResult;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
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
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class VkContext {
    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;


    private VkContext(Builder builder) {
        createInstance(builder);
        pickPhysicalDevice(builder);
    }


    private static int debugCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {
        VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
        System.err.println("Validation layer: " + callbackData.pMessageString());
        return VK_FALSE;
    }

    private static VkDebugUtilsMessengerCreateInfoEXT populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo) {
        debugCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
        debugCreateInfo.messageSeverity(
                //VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT   |
                        0
        );
        debugCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
        debugCreateInfo.pfnUserCallback(VkContext::debugCallback);
        return debugCreateInfo;
    }

    private void createInstance(Builder builder) {
        //FIXME: all the memory leaks that are made
        VkApplicationInfo appInfo = VkApplicationInfo.calloc()
                .sType$Default()
                .apiVersion(builder.apiVersion)
                .engineVersion(VK_MAKE_VERSION(1,0,0));

        VkInstanceCreateInfo pCreateInfo = VkInstanceCreateInfo.calloc()
                .sType$Default()
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(builder.buildInstanceExtensions())
                .ppEnabledLayerNames(builder.buildInstanceLayers())
                .pNext(populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT.calloc()
                        .sType$Default())
                            .address());

        PointerBuffer pInstance = memAllocPointer(1);
        int err = vkCreateInstance(pCreateInfo, null, pInstance);
        long instance = pInstance.get(0);
        memFree(pInstance);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to create VkInstance: " + translateVulkanResult(err));
        }
        this.instance = new VkInstance(instance, pCreateInfo);

        pCreateInfo.free();
        appInfo.free();
        /*
        memFree(VK_EXT_DEBUG_REPORT_EXTENSION);
        memFree(ppEnabledExtensionNames);
        memFree(appInfo.pApplicationName());
        memFree(appInfo.pEngineName());
        */

    }

    private void pickPhysicalDevice(Builder builder) {
        IntBuffer pPhysicalDeviceCount = memAllocInt(1);
        int err = vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, null);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get number of physical devices: " + translateVulkanResult(err));
        }
        PointerBuffer pPhysicalDevices = memAllocPointer(pPhysicalDeviceCount.get(0));
        err = vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, pPhysicalDevices);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get physical devices: " + translateVulkanResult(err));
        }
        for (int i = 0; i < pPhysicalDevices.capacity(); i++) {
            VkPhysicalDevice physicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance);
            if (canUseDevice(physicalDevice)) {
                this.physicalDevice = physicalDevice;
                break;
            }
        }
        memFree(pPhysicalDeviceCount);
        memFree(pPhysicalDevices);
        if (this.physicalDevice == null) {
            throw new AssertionError("Failed to find suitable physical devices: " + translateVulkanResult(err));
        }
    }

    private boolean canUseDevice(VkPhysicalDevice physicalDevice) {
        return false;
    }


    public static class Builder {
        //FIXME: i think the validation stuff aint right
        private boolean useValidation;
        private int apiVersion;
        public Builder(boolean useValidation, int apiVersion) {
            this.useValidation = useValidation;
            this.apiVersion = apiVersion;
            if (useValidation) {
                addInstanceExtension(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
            }
        }


        private List<String> instanceExtensions = new LinkedList<>();
        public Builder addInstanceExtension(String name) {
            instanceExtensions.add(name);
            return this;
        }
        private PointerBuffer buildInstanceExtensions() {
            PointerBuffer buffer = PointerBuffer.allocateDirect(instanceExtensions.size());
            instanceExtensions.forEach(ie -> buffer.put(memUTF8(ie)));
            return buffer.rewind();
        }


        private List<String> instanceLayers = new LinkedList<>();
        public Builder addInstanceLayer(String name) {
            instanceLayers.add(name);
            return this;
        }
        private PointerBuffer buildInstanceLayers() {
            PointerBuffer buffer = PointerBuffer.allocateDirect(instanceLayers.size()+(useValidation?1:0));
            instanceLayers.forEach(ie -> buffer.put(memUTF8(ie)));
            if (useValidation)
                buffer.put(memUTF8("VK_LAYER_KHRONOS_validation"));
            return buffer.rewind();
        }

        //TODO: add missing featureStruct
        public Builder addDeviceExtension(String name, int version) {
            return this;
        }

        public Builder addRequestedQueue(int flags, int count, float priority) {
            return this;
        }

        public Builder addDeviceExtension(String name) {
            return addDeviceExtension(name, 0);
        }

        public Builder addRequestedQueue(int flags) {
            return addRequestedQueue(flags, 1, 1.0f);
        }

        public VkContext build() {
            return new VkContext(this);
        }
    }



    public static void INIT() {
        var builder = new VkContext.Builder(true, VK_API_VERSION_1_2);
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
        builder.addDeviceExtension(VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME);
        builder.build();
    }
}
