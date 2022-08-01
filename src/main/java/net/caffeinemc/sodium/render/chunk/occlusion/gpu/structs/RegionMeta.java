package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

public class RegionMeta {
    public int id;
    public AABB bb = new AABB();
    public int sectionStart;
    public int sectionCount;

    public void write(IStructWriter writer) {
        writer.write(id);
        bb.write(writer);
        writer.write(sectionCount);
        writer.write(sectionStart);
    }
}
