package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

public class RegionMeta {
    public int id;
    public AABB bb = new AABB();
    public int sectionStart;
    public int sectionCount;
}
