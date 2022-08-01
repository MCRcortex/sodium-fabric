package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

public class Range {
    public int start;
    public int count;

    public void write(IStructWriter writer) {
        writer.write(start);
        writer.write(count);
    }
}
