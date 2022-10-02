package net.caffeinemc.sodium.vkinterop.vk;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SDescriptorDescription {
    public List<Descriptor> descriptors = new ArrayList<>();
    public static class Descriptor {
        public int binding;
        public int type;
        public int count;
        public int stages;
        public Descriptor(int binding, int type, int count, int stages) {
            this.binding = binding;
            this.type = type;
            this.count = count;
            this.stages = stages;
        }
    }
    public SDescriptorDescription(Descriptor... descriptors) {
        this.descriptors.addAll(List.of(descriptors));
    }
    public SDescriptorDescription add(int type, int stages) {
        this.descriptors.add(new Descriptor(this.descriptors.size(), type, 1, stages));
        return this;
    }
}
