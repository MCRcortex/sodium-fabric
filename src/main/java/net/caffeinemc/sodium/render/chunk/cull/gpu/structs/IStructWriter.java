package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4i;

public interface IStructWriter {
    void write(int number);

    void write(Vector4f vec);

    void write(Vector4i vec);

    void write(Matrix4f mat);
}
