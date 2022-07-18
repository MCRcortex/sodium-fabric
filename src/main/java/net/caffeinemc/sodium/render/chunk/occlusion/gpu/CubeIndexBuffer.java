package net.caffeinemc.sodium.render.chunk.occlusion.gpu;

import net.caffeinemc.gfx.api.buffer.ImmutableBuffer;
import net.caffeinemc.sodium.SodiumClientMod;

import java.nio.ByteBuffer;
import java.util.Set;

public class CubeIndexBuffer {
    public static final ImmutableBuffer INDEX_BUFFER;
    static {

        //TODO: generate all different combination of facing camera, that way when rendering just specify
        // the faces facing the camera
        ByteBuffer indices = ByteBuffer.allocateDirect(3*2*6);
        //Bottom face
        indices.put((byte) 0); indices.put((byte) 1); indices.put((byte) 2);
        indices.put((byte) 1); indices.put((byte) 3); indices.put((byte) 2);

        //right face
        indices.put((byte) 0); indices.put((byte) 2); indices.put((byte) 6);
        indices.put((byte) 6); indices.put((byte) 4); indices.put((byte) 0);

        //Back face
        indices.put((byte) 0); indices.put((byte) 4); indices.put((byte) 5);
        indices.put((byte) 5); indices.put((byte) 1); indices.put((byte) 0);

        //Left face
        indices.put((byte) 1); indices.put((byte) 5); indices.put((byte) 7);
        indices.put((byte) 7); indices.put((byte) 3); indices.put((byte) 1);

        //Bottom face
        indices.put((byte) 4); indices.put((byte) 6); indices.put((byte) 7);
        indices.put((byte) 7); indices.put((byte) 5); indices.put((byte) 4);

        //Top face
        indices.put((byte) 2); indices.put((byte) 7); indices.put((byte) 6);
        indices.put((byte) 2); indices.put((byte) 3); indices.put((byte) 7);
        indices.position(0);
        INDEX_BUFFER = SodiumClientMod.DEVICE.createBuffer(indices, Set.of());
    }
}
