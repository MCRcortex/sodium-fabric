package me.cortex.nv;

import me.cortex.nv.gl.BufferArena;
import me.cortex.nv.gl.GlBuffer;
import me.cortex.nv.gl.UploadingBufferStream;

public class Resources {
    public UploadingBufferStream terrainMetaUploadStream;

    public GlBuffer regionMetaBuffer;
    public GlBuffer sectionMetaBuffer;
    public BufferArena terrainGeometryBuffer;

    //Need 4x4 shrunk depth framebuffer
    public Resources(int renderFrames) {

    }

    public void delete() {

    }
}
