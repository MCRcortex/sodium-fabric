package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import org.joml.Vector4f;

public class AABB {
    public Vector4f offset = new Vector4f();
    public Vector4f size = new Vector4f();

    public void write(IStructWriter writer) {
        writer.write(offset);
        writer.write(size);
    }

    public boolean isVisible(Frustum frustum) {
        return frustum.isBoxVisible(offset.x, offset.y, offset.z, offset.x + size.x, offset.y + size.y, offset.z + size.z);
    }
}
