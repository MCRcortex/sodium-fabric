package me.cortex.vulkanitelib;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

public class VVkContext {
    VkDevice device;
    VkPhysicalDevice physicalDevice;
    VkInstance instance;
    Set<String> deviceSupportedExtensions;
    Set<String> requiredLayers;
    public VVkContext(VContextBuilder builder) {
        initInstance(builder);
        initPhysicalDevice(builder);
        initDevice(builder);
    }

    private void initDevice(VContextBuilder builder) {
        try (MemoryStack stack = stackPush()) {
            /*
            VkPhysicalDeviceFeatures2 features = VkPhysicalDeviceFeatures2.calloc(stack)
                    .sType$Default()
                    .pNext(VkPhysicalDeviceRayTracingPipelineFeaturesKHR.calloc(stack).sType$Default());
            //TODO: Need to chain fill out the feature struct with required features
            vkGetPhysicalDeviceFeatures2(physicalDevice, features);
            System.err.println(features);
             */



            PointerBuffer extensions = stack.mallocPointer(deviceSupportedExtensions.size());
            deviceSupportedExtensions.forEach(a->extensions.put(stack.UTF8(a)));
            extensions.rewind();
            PointerBuffer layers = stack.mallocPointer(requiredLayers.size());
            requiredLayers.forEach(a->layers.put(stack.UTF8(a)));
            layers.rewind();
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pQueuePriorities(stack.floats(1.0f))
                    .queueFamilyIndex(0);//FIXME: DONT HARDCODE, MAKE DYANMCI ETC

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .ppEnabledExtensionNames(extensions)
                    .ppEnabledLayerNames(layers)
                    .pQueueCreateInfos(queueCreateInfos);
            PointerBuffer pDevice = stack.callocPointer(1);
            _CHECK_(vkCreateDevice(physicalDevice, createInfo, null, pDevice));
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);
        }
    }

    private void initPhysicalDevice(VContextBuilder builder) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer devices = getPhysicalDevices(stack);
            int best = 0;
            devLoop:
            for (int i = 0; i < devices.capacity(); i++) {
                Set<String> supportedExtensions = new HashSet<>();
                try (MemoryStack stack2 = stackPush()) {
                    VkExtensionProperties.Buffer deviceExtensions = getDeviceExtensions(stack2, devices.get(i));
                    PointerBuffer foundExtensions = filterArray(stack2, deviceExtensions, builder.deviceExtensions.stream().map(a->a.name).toList());
                    int weighting = 0;
                    for (var entry : builder.deviceExtensions) {
                        boolean found = false;
                        for (int j = 0; j < foundExtensions.capacity(); j++) {
                            String name = foundExtensions.getStringUTF8(j);
                            if (name.equals(entry.name)) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            weighting++;
                        }
                        if (!found && !entry.optional) {
                            continue devLoop;
                        }
                        supportedExtensions.add(entry.name);
                    }
                    if (best <= weighting) {
                        physicalDevice = new VkPhysicalDevice(devices.get(i), instance);
                        best = weighting;
                    }
                }
                if (physicalDevice != null) {
                    deviceSupportedExtensions = supportedExtensions;
                }
            }
        }
        if (physicalDevice == null) {
            throw new IllegalStateException("Could not find suitable device");
        } else {
            try (MemoryStack stack = stackPush()) {
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(physicalDevice, props);
                System.out.println("Using device: "+props.deviceNameString());//TODO: MAKE A LOGGER OR SOMETHING
            }
        }
    }

    private static PointerBuffer filterArray(MemoryStack stack, VkLayerProperties.Buffer properties, List<String> requiredLayers) {
        List<ByteBuffer> supported = properties.stream().filter(a -> requiredLayers.contains(a.layerNameString())).map(VkLayerProperties::layerName).toList();
        PointerBuffer buffer = stack.mallocPointer(supported.size());
        supported.forEach(buffer::put);
        buffer.rewind();
        return buffer;
    }

    private static PointerBuffer filterArray(MemoryStack stack, VkExtensionProperties.Buffer properties, List<String> extensions) {
        List<ByteBuffer> supported = properties.stream().filter(a -> extensions.contains(a.extensionNameString())).map(VkExtensionProperties::extensionName).toList();
        PointerBuffer buffer = stack.mallocPointer(supported.size());
        supported.forEach(buffer::put);
        buffer.rewind();
        return buffer;
    }

    private boolean initInstance(VContextBuilder builder) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .apiVersion(VK_MAKE_VERSION(builder.major, builder.minor, 0))
                    .pApplicationName(memUTF8(builder.appName))
                    .pEngineName(memUTF8(builder.engineName));
            VkLayerProperties.Buffer layerProperties = getInstanceLayers(stack);
            VkExtensionProperties.Buffer extensionProperties = getInstanceExtensions(stack);
            PointerBuffer extensions = filterArray(stack, extensionProperties, builder.instanceExtensions.stream().map(a->a.name).toList());
            PointerBuffer layers = filterArray(stack, layerProperties, builder.instanceLayers);
            requiredLayers = new HashSet<>(builder.instanceLayers);//FIXME: should be a copy of layers

            //TODO: Add assertions that all the layers and extensions where found correctly, if not, raise error
            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(extensions)
                    .ppEnabledLayerNames(layers);
            PointerBuffer result = stack.pointers(0);
            _CHECK_(vkCreateInstance(instanceCreateInfo, null, result));
            instance = new VkInstance(result.get(0), instanceCreateInfo);
        }
        return false;
    }

    private VkLayerProperties.Buffer getInstanceLayers(MemoryStack stack) {
        int[] res = new int[1];
        _CHECK_(vkEnumerateInstanceLayerProperties(res, null));
        VkLayerProperties.Buffer layerProperties = VkLayerProperties.calloc(res[0], stack);
        _CHECK_(vkEnumerateInstanceLayerProperties(res, layerProperties));
        if (res[0] != layerProperties.capacity())
            throw new IllegalStateException();
        return layerProperties;
    }

    private VkExtensionProperties.Buffer getInstanceExtensions(MemoryStack stack) {
        int[] res = new int[1];
        _CHECK_(vkEnumerateInstanceExtensionProperties((String) null, res, null));
        VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(res[0], stack);
        _CHECK_(vkEnumerateInstanceExtensionProperties((String) null, res, extensionProperties));
        if (res[0] != extensionProperties.capacity())
            throw new IllegalStateException();
        return extensionProperties;
    }

    private PointerBuffer getPhysicalDevices(MemoryStack stack) {
        int[] res = new int[1];
        _CHECK_(vkEnumeratePhysicalDevices(instance, res, null));
        PointerBuffer devices = stack.callocPointer(res[0]);
        _CHECK_(vkEnumeratePhysicalDevices(instance, res, devices));
        if (res[0] != devices.capacity())
            throw new IllegalStateException();
        return devices;
    }

    private VkExtensionProperties.Buffer getDeviceExtensions(MemoryStack stack, long device) {
        return getDeviceExtensions(stack, new VkPhysicalDevice(device, instance));
    }

    private VkExtensionProperties.Buffer getDeviceExtensions(MemoryStack stack, VkPhysicalDevice device) {
        int[] res = new int[1];
        _CHECK_(vkEnumerateDeviceExtensionProperties(device, (String) null, res, null));
        VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(res[0], stack);
        _CHECK_(vkEnumerateDeviceExtensionProperties(device, (String) null, res, extensionProperties));
        if (res[0] != extensionProperties.capacity())
            throw new IllegalStateException();
        return extensionProperties;
    }

    private VVkDevice device_;
    public VVkDevice getDevice() {
        if (device_!=null)
            return device_;
        device_ = new VVkDevice(device, this);
        return device_;
    }

    public VkInstance getInstance() {
        return instance;
    }

    public VkPhysicalDevice getPhysicalDevice() {
        return physicalDevice;
    }
}
