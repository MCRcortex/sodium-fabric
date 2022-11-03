package net.caffeinemc.sodium.vk;

import net.caffeinemc.sodium.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

public class AccelerationData {
    NativeBuffer buffer;
    int quadCount;
    public AccelerationData(NativeBuffer baked, int quadCount) {
        this.buffer = baked;
        this.quadCount = quadCount;
    }

    public void delete() {
        buffer.free();
        buffer = null;
    }

    public AccelerationData copy() {
        var nb = new NativeBuffer(buffer.getLength());
        MemoryUtil.memCopy(buffer.getDirectBuffer(), nb.getDirectBuffer());
        return new AccelerationData(nb, quadCount);
    }
}
