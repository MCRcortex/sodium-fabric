package net.caffeinemc.sodium.vkinterop.vk.memory.images;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class SVkSampler {
    SVkDevice device;
    public long sampler;
    public SVkSampler(SVkDevice device, int mipLevels) {
        this.device = device;
        try (MemoryStack stack = stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .anisotropyEnable(true)
            .maxAnisotropy(16.0f)
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .unnormalizedCoordinates(false)
            .compareEnable(false)
            .compareOp(VK_COMPARE_OP_ALWAYS)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .minLod(0) // Optional
            .maxLod((float) mipLevels)
            .mipLodBias(0); // Optional

            LongBuffer pTextureSampler = stack.mallocLong(1);

            _CHECK_(vkCreateSampler(device.device, samplerInfo, null, pTextureSampler), "Failed to create texture sampler");


            sampler = pTextureSampler.get(0);
        }
    }
}
