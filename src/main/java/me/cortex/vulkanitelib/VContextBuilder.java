package me.cortex.vulkanitelib;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;

public class VContextBuilder {
    boolean DEBUG;
    String appName = "";
    String engineName = "";
    String gpuFilter;
    int major, minor;

    public VContextBuilder(boolean debug) {
        DEBUG = debug;
        if (debug) {
            addInstanceExtension(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
            addInstanceLayer("VK_LAYER_KHRONOS_validation");
        }
    }

    public VContextBuilder setVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
        return this;
    }

    public VContextBuilder gpuFilter(String filter) {
        this.gpuFilter = filter;
        return this;
    }

    static class ExtensionEntry {//TODO: add optional pointerFeatureStruct so that it can be conditionally configured if wanted NOTE: MUST DO THIS SO THAT vkGetPhysicalDeviceFeatures2 struct can be filled out
        final String name;
        final boolean optional;
        final Function<MemoryStack, Struct> pNextApplicator;
        public ExtensionEntry(String name, boolean optional, Function<MemoryStack, Struct> pNextApplicator) {
            this.name = name;
            this.optional = optional;
            this.pNextApplicator = pNextApplicator;
        }
        public ExtensionEntry(String name, Function<MemoryStack, Struct> pNextApplicator) {
            this(name, false, pNextApplicator);
        }
        public ExtensionEntry(String name) {
            this(name, false, null);
        }
    }
    List<ExtensionEntry> instanceExtensions = new LinkedList<>();
    public VContextBuilder addInstanceExtension(String extension) {
        instanceExtensions.add(new ExtensionEntry(extension));
        return this;
    }
    public VContextBuilder addInstanceExtensions(String... extensions) {
        for (String i : extensions)
            addInstanceExtension(i);
        return this;
    }

    List<String> instanceLayers = new LinkedList<>();
    public VContextBuilder addInstanceLayer(String layer) {
        instanceLayers.add(layer);
        return this;
    }

    List<ExtensionEntry> deviceExtensions = new LinkedList<>();
    public VContextBuilder addDeviceExtension(String extension) {
        deviceExtensions.add(new ExtensionEntry(extension));
        return this;
    }

    //TODO: need to add a feature requester
    public VContextBuilder addDeviceExtension(String extension, Function<MemoryStack, Struct> applicator) {//TODO: make this alot cleaner somehow
        deviceExtensions.add(new ExtensionEntry(extension, applicator));
        return this;
    }
    public VContextBuilder addDeviceExtensions(String... extensions) {
        for (String i : extensions)
            addDeviceExtension(i);
        return this;
    }


    //TODO: When building need to request the physical device features with `vkGetPhysicalDeviceFeatures2`
    // and enable them when creating the device

    public VVkContext create() {
        return new VVkContext(this);
    }
}
