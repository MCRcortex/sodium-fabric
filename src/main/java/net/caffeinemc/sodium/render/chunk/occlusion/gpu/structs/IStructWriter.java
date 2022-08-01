package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import org.joml.Vector4f;

public interface IStructWriter {
    void write(int sectionCount);

    void write(Vector4f sectionPos);
}
