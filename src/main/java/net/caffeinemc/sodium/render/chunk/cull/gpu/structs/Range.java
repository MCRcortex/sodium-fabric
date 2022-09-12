package net.caffeinemc.sodium.render.chunk.cull.gpu.structs;

public class Range {
    public static final int SIZE = 8;

    public int start;
    public int count;

    public void write(IStructWriter writer) {
        writer.write(start);
        writer.write(count);
    }
}
