package net.caffeinemc.sodium.vk;

import net.caffeinemc.sodium.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

public class AccelerationData {
    NativeBuffer buffer;
    NativeBuffer metaBuffer;
    int quadCount;
    public AccelerationData(NativeBuffer baked, NativeBuffer metaBuffer, int quadCount) {
        this.buffer = baked;
        this.metaBuffer = metaBuffer;
        this.quadCount = quadCount;
    }

    public void delete() {
        buffer.free();
        metaBuffer.free();
        buffer = null;
        metaBuffer = null;
    }

    public AccelerationData copy() {
        var nb = new NativeBuffer(buffer.getLength());
        var nmb = new NativeBuffer(metaBuffer.getLength());
        MemoryUtil.memCopy(buffer.getDirectBuffer(), nb.getDirectBuffer());
        MemoryUtil.memCopy(metaBuffer.getDirectBuffer(), nmb.getDirectBuffer());
        return new AccelerationData(nb, nmb, quadCount);
    }
}
