package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import org.joml.Vector4f;

public class SectionMeta {
    public static final int SIZE = 4+4+4*4+AABB.SIZE*2;

    public int id;
    public int regionId;
    public Vector4f sectionPos = new Vector4f();
    public AABB bb = new AABB();

    public void write(IStructWriter writer) {
        writer.write(id);
        writer.write(regionId);
        writer.write(sectionPos);
        bb.write(writer);
    }
}
