package net.caffeinemc.sodium.vkinterop.vk.pipeline;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;

import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;

public class SVkPipelineLayout {
    SVkDescriptorSetLayout[] descriptorLayouts;
    SVkPushConstant[] pushConstants;
    public long layout;
    public SVkPipelineLayout(SVkDevice device, SVkDescriptorSetLayout layout) {
        this(device, new SVkDescriptorSetLayout[]{layout}, null);
    }
    public SVkPipelineLayout(SVkDevice device, SVkDescriptorSetLayout[] layouts, SVkPushConstant[] constants) {
        descriptorLayouts = layouts;
        pushConstants = constants;//TODO:THIS
        try (MemoryStack stack = stackPush()) {
            LongBuffer layoutPtrs = stack.callocLong(descriptorLayouts.length);
            for (SVkDescriptorSetLayout layoutSet : descriptorLayouts) {
                layoutPtrs.put(layoutSet.layout);
            }
            layoutPtrs.rewind();
            VkPipelineLayoutCreateInfo ci = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(layoutPtrs)
                    .pPushConstantRanges(null);//TODO:THIS
            LongBuffer pPipelineLayout = stack.callocLong(1);
            _CHECK_(vkCreatePipelineLayout(device.device, ci, null, pPipelineLayout), "failed to create pipeline layout");
            layout = pPipelineLayout.get(0);
        }
    }
}
