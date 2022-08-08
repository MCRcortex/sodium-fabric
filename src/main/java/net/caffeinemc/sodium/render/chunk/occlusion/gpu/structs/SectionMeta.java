package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import net.caffeinemc.sodium.util.MathUtil;
import org.joml.Vector4f;

public class SectionMeta {
    public static final int SIZE = MathUtil.align(4+4+4*2+4*4+AABB.SIZE + 3*RenderPassRanges.SIZE, 16);

    public int id;
    public int regionId;
    public int visbitmask;
    public Vector4f sectionPos = new Vector4f();
    public AABB aabb = new AABB();

    public RenderPassRanges[] ranges = new RenderPassRanges[3];

    public SectionMeta() {
        for (int i = 0; i < ranges.length; i++) {
            ranges[i] = new RenderPassRanges();
        }
    }

    public void write(IStructWriter writer) {
        writer.write(id);
        writer.write(regionId);
        writer.write(visbitmask);
        //PADDING
        writer.write(0);

        writer.write(sectionPos);
        aabb.write(writer);

        for (var passRange : ranges) {
            passRange.write(writer);
        }
    }
}
