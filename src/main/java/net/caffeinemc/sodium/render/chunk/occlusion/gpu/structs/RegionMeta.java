package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

public class RegionMeta {
    public static final int SIZE = 4 + AABB.SIZE + 4 + 4;

    public int id = -1;
    public AABB aabb = new AABB();
    public int sectionStart;
    public int sectionCount;

    public void write(IStructWriter writer) {
        writer.write(id);
        aabb.write(writer);
        writer.write(sectionCount);
        writer.write(sectionStart);
    }
}
