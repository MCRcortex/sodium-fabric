package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import org.joml.Vector4f;

public class SectionMeta {
    public static final int SIZE = 4+4+4*2+4*4+AABB.SIZE;

    public int id;
    public int regionId;
    public int visbitmask;
    public Vector4f sectionPos = new Vector4f();
    public AABB aabb = new AABB();

    public void write(IStructWriter writer) {
        writer.write(id);
        writer.write(regionId);
        writer.write(visbitmask);
        //PADDING
        writer.write(0);

        writer.write(sectionPos);
        aabb.write(writer);
    }
}
