package net.caffeinemc.sodium.render.chunk.cull.gpu.structs;

public class RegionMeta {
    public static final int SIZE = 4 + 4 + 4 + 4 + AABB.SIZE;

    public int id = -1;
    public int sectionStart;
    public int sectionCount;
    public AABB aabb = new AABB();

    public void write(IStructWriter writer) {
        writer.write(id);
        writer.write(sectionStart);
        writer.write(sectionCount);
        writer.write(0);//PADDING
        aabb.write(writer);
    }
}
