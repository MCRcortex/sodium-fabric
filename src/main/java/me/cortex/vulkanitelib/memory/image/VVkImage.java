package me.cortex.vulkanitelib.memory.image;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.VVkMemory;
import me.cortex.vulkanitelib.memory.VVkMemoryObject;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;

public class VVkImage extends VVkMemoryObject {
    public final long image;
    public final int width;
    public final int height;
    public final int depth;
    public final int mipLayers;
    public final int arrayLayers;
    public final int type;
    public final int format;
    public final int initialLayout;

    public VVkImage(int width, int height, int depth, int mipLayers, int arrayLayers, int type, int format, int initialLayout, long image, VVkMemory memory) {
        super(memory);
        this.image = image;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.mipLayers = mipLayers;
        this.arrayLayers = arrayLayers;
        this.type = type;
        this.format = format;
        this.initialLayout = initialLayout;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public VVkImageView createView(int aspect) {
        return createView(aspect, 0, mipLayers, 0, arrayLayers);
    }
    public VVkImageView createView(int aspect, int baseMip, int mipLevels, int baseArray, int arrayLayers) {//Maybe make this create it locally store and return it maybe
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo ivci = VkImageViewCreateInfo
                    .calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(type)//This might cause issues later, FIXME: cause you can get other types
                    .format(format)
                    //.components()//TODO: components
                    ;
            ivci.subresourceRange()
                    .aspectMask(aspect)
                    .baseMipLevel(baseMip)
                    .levelCount(mipLevels)
                    .baseArrayLayer(baseArray)
                    .layerCount(arrayLayers);
            LongBuffer pImageView = stack.mallocLong(1);
            _CHECK_(vkCreateImageView(device.device, ivci, null, pImageView));

            return new VVkImageView(device, aspect, arrayLayers, this, pImageView.get(0));
        }
    }
}