package net.caffeinemc.sodium.render.chunk.occlusion.gpu.structs;

import net.caffeinemc.gfx.util.misc.MathUtil;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4i;

public class SceneStruct {
    public static final int SIZE = MathUtil.align(4*4*4*2+4*4*2+2*4+2*4, 16);

    public Matrix4f MVP = new Matrix4f();
    public Matrix4f MV = new Matrix4f();
    public Vector4f camera = new Vector4f();
    public Vector4i cameraSection = new Vector4i();
    public int frameId;
    public int regionCount;
    public int regionInId;
    public int sectionInIndex;

    public void write(IStructWriter writer) {
        writer.write(MVP);
        writer.write(MV);
        writer.write(camera);
        writer.write(cameraSection);
        writer.write(frameId);
        writer.write(regionCount);
        writer.write(regionInId);
        writer.write(sectionInIndex);
    }
}
