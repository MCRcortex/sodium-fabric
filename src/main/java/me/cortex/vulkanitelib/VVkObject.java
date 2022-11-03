package me.cortex.vulkanitelib;

import java.lang.ref.Cleaner;

public abstract class VVkObject {
    private static final Cleaner cleaner = Cleaner.create();
    public final VVkDevice device;
    protected VVkObject(VVkDevice device) {
        this.device = device;
        cleaner.register(this, ()->{//TODO: Clean the object/yell at person that object was not freed
            //TODO: not yell if object was freed
            //System.out.println("ERROR: Vulkan object out of gc scope without cleaning");
        });
    }
    public abstract void free();
}
