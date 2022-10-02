package net.caffeinemc.sodium.vkinterop.vk.pipeline;

import net.caffeinemc.sodium.vkinterop.vk.SDescriptorDescription;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

public class SVkDescriptorSet {
    SVkDevice device;
    SVkDescriptorPool pool;//Contains the ref to SVkDescriptorSetLayout
    public long set;
    public SVkDescriptorSet(SVkDevice device, SVkDescriptorPool pool, long set) {
        this.device = device;
        this.pool = pool;
        this.set = set;
    }

}
