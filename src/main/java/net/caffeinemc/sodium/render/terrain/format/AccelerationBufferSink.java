package net.caffeinemc.sodium.render.terrain.format;

import net.caffeinemc.sodium.util.NativeBuffer;
import net.caffeinemc.sodium.vk.AccelerationData;
import org.lwjgl.system.MemoryUtil;

import java.util.Objects;
import java.util.Random;

public class AccelerationBufferSink {
    public NativeBuffer buffer = new NativeBuffer(0);
    public long position;
    public void ensureRemainingCapacity(long capacity) {
        if (buffer.getLength() < position+capacity) {
            buffer.reallocate((int) ((position+capacity)*1.25));
        }
    }

    public void write(float posX, float posY, float posZ, int meta) {
        ensureRemainingCapacity(Float.BYTES*4);
        MemoryUtil.memPutFloat(buffer.getAddress()+position, posX);
        MemoryUtil.memPutFloat(buffer.getAddress()+position+4, posY);
        MemoryUtil.memPutFloat(buffer.getAddress()+position+8, posZ);
        MemoryUtil.memPutInt(buffer.getAddress()+position+12, meta);

        position+=16;
    }
    public void free() {
        buffer.free();
        position = -1;
        buffer = null;
    }

    public void reset() {
        position = 0;
    }

    public AccelerationData bake() {
        if (position % (16*4) != 0)
            throw new IllegalStateException("Not quad in acceleration sink");
        NativeBuffer baked = new NativeBuffer((int) position);
        MemoryUtil.memCopy(buffer.getAddress(), baked.getAddress(), position);
        return new AccelerationData(baked, (int) (position/(16*4)));
    }
}
