package me.cortex.vulkanitelib.descriptors.builders;

import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetsPooled;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.memory.image.VVkImageView;
import me.cortex.vulkanitelib.memory.image.VVkSampler;
import net.minecraft.util.Pair;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;


public class DescriptorUpdateBuilder {
    public interface IDescriptorPrep {
        void prep(MemoryStack stack, VkWriteDescriptorSet write);
    }
    public interface IDescriptorWrite {
        void write(int index, VkWriteDescriptorSet write);
    }
    public List<Pair<IDescriptorPrep, IDescriptorWrite>> updates = new LinkedList<>();
    public DescriptorUpdateBuilder(VVkDescriptorSetLayout layout) {

    }
    private static void combImageSamplePrep(MemoryStack stack, VkWriteDescriptorSet writer) {
        writer.descriptorCount(1).pImageInfo(VkDescriptorImageInfo.calloc(1, stack)).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
    }
    private static void accelerationPrep(MemoryStack stack, VkWriteDescriptorSet writer) {
        writer.descriptorCount(1).pNext(VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack).sType$Default()).descriptorType(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR);
    }

    public DescriptorUpdateBuilder sbuffer(int binding, VVkBuffer[] buffers) {
        return buffer(binding, buffers, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
    }

    public DescriptorUpdateBuilder sbuffer(int binding, VVkBuffer buffer) {
        return buffer(binding, buffer, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
    }
    public DescriptorUpdateBuilder ubuffer(int binding, VVkBuffer[] buffers) {
        return buffer(binding, buffers, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
    }

    public DescriptorUpdateBuilder ubuffer(int binding, VVkBuffer buffer) {
        return buffer(binding, buffer, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
    }
    public DescriptorUpdateBuilder buffer(int binding, VVkBuffer[] buffers, int type) {
        updates.add(new Pair<>(((stack, writer) -> {writer.descriptorCount(1).pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack)).descriptorType(type);}),
                (index, writer) -> {
                    writer.dstBinding(binding).pBufferInfo().buffer(buffers[index].buffer).range(VK_WHOLE_SIZE);
                }));
        return this;
    }

    public DescriptorUpdateBuilder buffer(int binding, VVkBuffer buffer, int type) {
        updates.add(new Pair<>((stack, writer) -> {writer.descriptorCount(1).pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack)).descriptorType(type);},
                (index, writer) -> {
                    writer.dstBinding(binding).pBufferInfo().buffer(buffer.buffer).range(VK_WHOLE_SIZE);
                }));
        return this;
    }
    public DescriptorUpdateBuilder image(int binding, int layoutType, VVkSampler sampler, VVkImageView view) {
        updates.add(new Pair<>(DescriptorUpdateBuilder::combImageSamplePrep,
                (index, writer) -> {
                    writer.dstBinding(binding).pImageInfo().imageView(view.view).sampler(sampler.sampler).imageLayout(layoutType);
                }));
        return this;
    }
}
/*
public class DescriptorUpdateBuilder {
    public interface IDescriptorWriter<T> {
        void set(int index, T writer);
    }

    public DescriptorUpdateBuilder(VVkDescriptorSetLayout layout) {

    }
    public abstract class Updater <T>{
        List<IDescriptorWriter<T>> singleWriters = new LinkedList<>();
        List<IDescriptorWriter<T>> writers = new LinkedList<>();
        void setup(MemoryStack stack, VkWriteDescriptorSet set) {

        }
        int binding;
        public DescriptorUpdateBuilder end() {
            return DescriptorUpdateBuilder.this;
        }
    }


    public class BufferUpdater extends Updater<VkDescriptorBufferInfo> {
        public BufferUpdater update(VVkBuffer buffer, long offset, long range) {//TODO: can maybe make range be automatic from layout
            throw new IllegalStateException();
        }

        public BufferUpdater update(VVkBuffer buffer) {//TODO: can maybe make range be automatic from layout
            throw new IllegalStateException();
        }

        public BufferUpdater updateZipped(VVkBuffer... buffers) {//Defaults to 0 offset and max range
            throw new IllegalStateException();
        }

        public BufferUpdater update(IDescriptorWriter<VkDescriptorBufferInfo> writer) {//Maybe dont pass in the direct struct? idk
            throw new IllegalStateException();
        }
    }

    public class ImageUpdater extends Updater {
        public ImageUpdater update(VVkSampler sampler, VVkImageView image) {
            throw new IllegalStateException();
        }
        public ImageUpdater updateZipped(VVkSampler[] sampler, VVkImageView[] image) {
            throw new IllegalStateException();
        }
    }

    public class AccelerationUpdater extends Updater {

    }

    public BufferUpdater buffer(int binding) {
        return buffer(binding, 0);
    }
    public BufferUpdater buffer(int binding, int arrayElement) {
        return new BufferUpdater();
    }

    public DescriptorUpdateBuilder buffer(int binding, VVkBuffer[] buffers) {
        BufferUpdater updater = buffer(binding);
        updater.updateZipped(buffers);
        return updater.end();
    }


    public ImageUpdater image(int binding) {
        return image(binding, 0);
    }
    public ImageUpdater image(int binding, int arrayElement) {
        return new ImageUpdater();
    }
    public DescriptorUpdateBuilder image(int binding, VVkSampler[] samplers, VVkImageView[] views) {
        ImageUpdater updater = image(binding);
        updater.updateZipped(samplers, views);
        return updater.end();
    }
    public DescriptorUpdateBuilder image(int binding, VVkSampler sampler, VVkImageView view) {
        ImageUpdater updater = image(binding);
        updater.update(sampler, view);
        return updater.end();
    }

    public AccelerationUpdater accelerationStructure(int binding) {
        return new AccelerationUpdater();
    }


    static class UpdateSystem {
        VkWriteDescriptorSet.Buffer writers;
        VVkDescriptorSetsPooled descriptorPool;

        public UpdateSystem(MemoryStack stack, DescriptorUpdateBuilder updateBuilder, VVkDescriptorSetsPooled pooled) {
            descriptorPool = pooled;
            //writers = VkWriteDescriptorSet.calloc(updateBuilder, stack);
            initUpdateConstants();
        }

        private void initUpdateConstants() {

        }

        public void update(int index) {

        }
    }
}
*/