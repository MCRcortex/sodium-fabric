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

    public void write(float posX, float posY, float posZ) {
        ensureRemainingCapacity(Float.BYTES*4);
        MemoryUtil.memPutFloat(buffer.getAddress()+position, posX);
        MemoryUtil.memPutFloat(buffer.getAddress()+position+4, posY);
        MemoryUtil.memPutFloat(buffer.getAddress()+position+8, posZ);

        position+=12;
    }
    public void free() {
        buffer.free();
        metaBuffer.free();
        position = -1;
        mPosition = -1;
        buffer = null;
        metaBuffer = null;
    }

    public void reset() {
        position = 0;
        mPosition = 0;
    }

    public void ensureRemainingCapacityMeta(long capacity) {
        if (metaBuffer.getLength() < mPosition+capacity) {
            metaBuffer.reallocate((int) ((mPosition+capacity)*1.25));
        }
    }

    public void writeMeta(float u, float v) {
        ensureRemainingCapacityMeta(Float.BYTES*2);
        MemoryUtil.memPutFloat(metaBuffer.getAddress()+mPosition, u);
        MemoryUtil.memPutFloat(metaBuffer.getAddress()+mPosition+4, v);
        mPosition+=8;
    }

    public NativeBuffer metaBuffer = new NativeBuffer(0);
    public long mPosition;

    public AccelerationData bake() {
        if (position % (12*4) != 0)
            throw new IllegalStateException("Not quad in acceleration sink");
        NativeBuffer baked = new NativeBuffer((int) position);
        MemoryUtil.memCopy(buffer.getAddress(), baked.getAddress(), position);
        NativeBuffer mb = new NativeBuffer((int) mPosition);
        MemoryUtil.memCopy(metaBuffer.getAddress(), mb.getAddress(), mPosition);
        return new AccelerationData(baked, mb, (int) (position/(12*4)));
    }
}
