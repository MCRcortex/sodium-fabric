package me.cortex.vulkanitelib.pipelines;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

import java.nio.ByteBuffer;

public class VVkShader extends VVkObject {
    public ByteBuffer code;
    public long module;
    public int stages;

    public VVkShader(VVkDevice device, ByteBuffer shaderSource, int shaderStage, long module) {
        super(device);
        this.stages = shaderStage;
        this.module = module;
        this.code = shaderSource;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
