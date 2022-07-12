package net.caffeinemc.sodium.render.buffer.arena.smart;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.sodium.render.buffer.arena.ArenaBuffer;
import net.caffeinemc.sodium.render.buffer.arena.BufferSegment;
import net.caffeinemc.sodium.render.buffer.arena.PendingUpload;

import java.util.List;

public class ConstantAsyncArenaBuffer implements ArenaBuffer {
    @Override
    public long getDeviceUsedMemory() {
        return 0;
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return 0;
    }

    @Override
    public void free(BufferSegment entry) {

    }

    @Override
    public void delete() {

    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Buffer getBufferObject() {
        return null;
    }

    @Override
    public void upload(List<PendingUpload> uploads, int frameIndex) {

    }

    @Override
    public int getStride() {
        return 0;
    }
}
