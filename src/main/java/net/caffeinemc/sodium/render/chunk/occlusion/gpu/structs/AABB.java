package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import net.caffeinemc.sodium.interop.vanilla.math.frustum.Frustum;
import net.caffeinemc.sodium.render.chunk.state.ChunkRenderBounds;
import org.joml.Vector4f;

public class AABB {
    //TODO: maybe convert too min and max vectors and when writing convert to min + size as it simplifies alot of things
    public static final int SIZE = 4 * 4 * 2;
    public Vector4f offset = new Vector4f();
    public Vector4f size = new Vector4f();

    public void write(IStructWriter writer) {
        writer.write(offset);
        writer.write(size);
    }

    public boolean isVisible(Frustum frustum) {
        return frustum.isBoxVisible(offset.x, offset.y, offset.z, offset.x + size.x, offset.y + size.y, offset.z + size.z);
    }

    public boolean isInside(AABB other) {
        throw new IllegalStateException();
    }

    public boolean isOnInsideBoarder(AABB other) {
        throw new IllegalStateException();
    }

    public boolean isOnInsideBoarder(ChunkRenderBounds other) {
        //TODO: CHECK THIS WORKS DUE TO FLOATING POINT ISSUES, if not make it like if its close enough
        if ((float)other.x1 == offset.x)
            return true;
        if ((float)other.y1 == offset.y)
            return true;
        if ((float)other.z1 == offset.z)
            return true;
        if ((float)other.x2 == offset.x + size.x)
            return true;
        if ((float)other.y2 == offset.y + size.y)
            return true;
        if ((float)other.z2 == offset.z + size.z)
            return true;

        return false;
    }

    public void set(ChunkRenderBounds New) {
        offset.x = (float) New.x1;
        offset.y = (float) New.y1;
        offset.z = (float) New.z1;
        size.x = (float) (New.x2-New.x1);
        size.y = (float) (New.y2-New.y1);
        size.z = (float) (New.z2-New.z1);
    }

    public void ensureContains(ChunkRenderBounds bounds) {
        double lsx = offset.x + size.x;
        double lsy = offset.y + size.y;
        double lsz = offset.z + size.z;
        offset.x = (float) Math.min(bounds.x1, offset.x);
        offset.y = (float) Math.min(bounds.y1, offset.y);
        offset.z = (float) Math.min(bounds.z1, offset.z);
        size.x = (float) Math.max(bounds.x2, lsx) - offset.x;
        size.y = (float) Math.max(bounds.y2, lsy) - offset.y;
        size.z = (float) Math.max(bounds.z2, lsz) - offset.z;
    }
}
