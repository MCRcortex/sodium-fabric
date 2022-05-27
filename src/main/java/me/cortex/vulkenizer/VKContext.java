package me.cortex.vulkenizer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.vulkan.VK10.*;

public class VKContext {
    private final IntBuffer ib = memAllocInt(1);
    private VKContext(Builder builder) {
        createInstance(builder);
        //createInstance(builder);
    }

    private void createInstance(Builder builder) {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8Safe("glvk"));
            appInfo.pEngineName(stack.UTF8Safe("No Engine"));
            appInfo.apiVersion(VK_MAKE_VERSION(builder.major, builder.minor, 0));

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
            //instance = new VkInstance(instancePtr.get(0), createInfo);
        }
    }




    public static class Builder {
        private boolean useValidation;
        private int major;
        private int minor;
        public Builder(boolean useValidation, int major, int minor) {
            this.useValidation = useValidation;
            this.major = major;
            this.minor = minor;
        }

        public Builder addInstanceExtension(String name, boolean optional) {
            return this;
        }

        public Builder addInstanceLayer(String name, boolean optional) {
            return this;
        }
        //TODO: add missing featureStruct
        public Builder addDeviceExtension(String name, boolean optional, int version) {
            return this;
        }

        public Builder addRequestedQueue(int flags, int count, float priority) {
            return this;
        }

        public Builder addInstanceExtension(String name) {
            return addInstanceExtension(name, false);
        }

        public Builder addInstanceLayer(String name) {
            return addInstanceLayer(name, false);
        }

        public Builder addDeviceExtension(String name) {
            return addDeviceExtension(name, false, 0);
        }

        public Builder addRequestedQueue(int flags) {
            return addRequestedQueue(flags, 1, 1.0f);
        }


        public VKContext build() {
            return new VKContext(this);
        }
    }
}
