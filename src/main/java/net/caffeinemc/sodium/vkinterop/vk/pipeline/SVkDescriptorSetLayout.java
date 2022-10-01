package net.caffeinemc.sodium.vkinterop.vk.pipeline;

import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import net.caffeinemc.sodium.vkinterop.vk.SDescriptorDescription;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class SVkDescriptorSetLayout {
    SDescriptorDescription description;
    long layout;
    public SVkDescriptorSetLayout(SDescriptorDescription description) {
        this.description = description;
        try(MemoryStack stack = stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(description.descriptors.size(), stack);
            for (int i = 0; i < description.descriptors.size(); i++) {
                SDescriptorDescription.Descriptor descriptor = description.descriptors.get(i);
                bindings.get(i)//Camera matrices
                        .binding(descriptor.binding)
                        .descriptorCount(descriptor.count)
                        .descriptorType(descriptor.type)
                        .pImmutableSamplers(null)
                        .stageFlags(descriptor.stages);
            }

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack);
            layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);

            if(vkCreateDescriptorSetLayout(VkContextTEMP.getDevice(), layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }
            layout = pDescriptorSetLayout.get(0);
        }
    }

    /*
    private void createDescriptorLayout() {
        try(MemoryStack stack = stackPush()) {

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(5, stack);
            //Just hard coding for now
            bindings.get(0)//Camera matrices
                    .binding(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

            bindings.get(1)//Chunk transforms
                    .binding(1)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)//.descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

            bindings.get(2)//Fog
                    .binding(2)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);



            bindings.get(3)//tex_diffuse
                    .binding(3)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_ALL_GRAPHICS);

            bindings.get(4)//tex_light
                    .binding(4)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImmutableSamplers(null)
                    .stageFlags(VK_SHADER_STAGE_ALL_GRAPHICS);


            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack);
            layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            if(vkCreateDescriptorSetLayout(VkContextTEMP.getDevice(), layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }
            layout = pDescriptorSetLayout.get(0);
        }
    }*/
}
