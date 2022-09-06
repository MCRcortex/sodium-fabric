package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import net.caffeinemc.gfx.util.misc.MathUtil;
import org.joml.Vector4f;
import org.joml.Vector4i;

public class SectionMeta {
    public static final int SIZE = MathUtil.align(4+4+4*2+4*4+AABB.SIZE + 3*RenderPassRanges.SIZE+Range.SIZE, 16);

    public int id;
    public int regionId;
    public int visbitmask;
    public Vector4i sectionPos = new Vector4i();
    public AABB aabb = new AABB();

    public RenderPassRanges[] ranges = new RenderPassRanges[3];
    public Range translucency = new Range();

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
        translucency.write(writer);
    }
}
