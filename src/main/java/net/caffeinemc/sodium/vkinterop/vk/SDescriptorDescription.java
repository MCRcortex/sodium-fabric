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
    }
    public SDescriptorDescription() {
    }
}
