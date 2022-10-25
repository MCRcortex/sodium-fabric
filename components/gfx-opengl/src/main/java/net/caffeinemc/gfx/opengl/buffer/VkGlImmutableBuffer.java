package net.caffeinemc.gfx.opengl.buffer;

import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.gfx.api.buffer.ImmutableBufferFlags;

import java.util.Collections;
import java.util.Set;

public class VkGlImmutableBuffer extends GlBuffer implements ImmutableBuffer {
    private final Set<ImmutableBufferFlags> flags;
    public final Object data;

    public VkGlImmutableBuffer(Object data, int handle, long capacity, Set<ImmutableBufferFlags> flags) {
        super(handle, capacity);
        this.flags = Collections.unmodifiableSet(flags);
        this.data = data;
    }

    @Override
    public Set<ImmutableBufferFlags> flags() {
        return this.flags;
    }
}
