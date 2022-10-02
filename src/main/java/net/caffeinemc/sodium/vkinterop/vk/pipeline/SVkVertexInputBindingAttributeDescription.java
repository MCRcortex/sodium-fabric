package net.caffeinemc.sodium.vkinterop.vk.pipeline;

import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.array.VertexArrayResourceBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttribute;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeFormat;
import net.caffeinemc.gfx.api.array.attribute.VertexFormat;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.lwjgl.vulkan.VK10.*;

public class SVkVertexInputBindingAttributeDescription {
    VertexArrayDescription<?> vad;
    int stride;//FIXME: make via api
    public SVkVertexInputBindingAttributeDescription(VertexArrayDescription<?> vad, int stride) {
        this.vad = vad;
        this.stride = stride;
    }
    public VkVertexInputBindingDescription.Buffer createBindingDescriptions() {//FIXME: need to have 1 binding per source
        VkVertexInputBindingDescription.Buffer bindingDescriptions =
                VkVertexInputBindingDescription.create(1);

        bindingDescriptions.get(0)
                .binding(0)
                .stride(stride)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        return bindingDescriptions;
    }

    private static int findVkFormat(VertexAttribute format) {
        if (!format.intType() && format.normalized() && format.format()== VertexAttributeFormat.SHORT && format.count()==3) {
            return VK_FORMAT_R16G16B16_SNORM;
        } else if (!format.intType() && format.normalized() && format.format()==VertexAttributeFormat.UNSIGNED_BYTE && format.count()==4) {
            return VK_FORMAT_R8G8B8A8_UNORM;
        } else if (!format.intType() && format.normalized() && format.format()==VertexAttributeFormat.UNSIGNED_SHORT && format.count()==2) {
            return VK_FORMAT_R16G16_UNORM;
        } else if (format.intType() && !format.normalized() && format.format()==VertexAttributeFormat.UNSIGNED_SHORT && format.count()==2) {
            return VK_FORMAT_R16G16_SINT;
        } else {
            throw new IllegalStateException("Unknown format");
        }
    }

    public VkVertexInputAttributeDescription.Buffer createAttributeDescriptions() {
        List<VertexAttributeBinding> bindings = vad.vertexBindings().stream().flatMap(a -> Arrays.stream(a.attributeBindings())).toList();
        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(bindings.size());
        for(int i = 0; i < bindings.size(); i++) {
            VertexAttributeBinding binding = bindings.get(i);
            attributeDescriptions.get(i)
                    .binding(0)
                    .format(findVkFormat(binding.attribute()))
                    .location(binding.index())
                    .offset(binding.attribute().offset());
        }

        return attributeDescriptions.rewind();
    }

}
