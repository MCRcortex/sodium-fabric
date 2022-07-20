package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import org.joml.Vector4f;

public class SectionMeta {
    public int id;
    public int regionId;
    public Vector4f sectionPos = new Vector4f();
    public AABB bb = new AABB();
}
