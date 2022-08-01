package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

public class RegionMeta {
    public int id;
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
