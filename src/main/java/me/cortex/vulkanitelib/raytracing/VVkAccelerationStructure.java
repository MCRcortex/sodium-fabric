package me.cortex.vulkanitelib.raytracing;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;

public class VVkAccelerationStructure extends VVkObject {
    public final long acceleration;
    public VVkBuffer buffer;
    public VVkAccelerationStructure(VVkDevice device, long acceleration, VVkBuffer accelerationBuffer) {
        super(device);
        this.acceleration = acceleration;
        this.buffer = accelerationBuffer;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
