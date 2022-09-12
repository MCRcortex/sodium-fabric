package net.caffeinemc.sodium.render.chunk.cull.gpu.structs;

public class RenderPassRanges {
    public static final int SIZE = 7 * Range.SIZE;

    public Range[] ranges = new Range[7];
    public RenderPassRanges() {
        for (int i = 0; i < ranges.length; i++) {
            ranges[i] = new Range();
        }
    }

    public void write(IStructWriter writer) {
        for (var range : ranges) {
            range.write(writer);
        }
    }
}
