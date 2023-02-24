package me.cortex.nv;

import me.cortex.nv.gl.BufferArena;
import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.gl.buffers.Buffer;
import me.cortex.nv.gl.buffers.DeviceOnlyBuffer;
import me.cortex.nv.gl.UploadingBufferStream;

public class Resources {
    private final RenderDevice device;
    public UploadingBufferStream terrainMetaUploadStream;

    public DeviceOnlyBuffer regionMetaBuffer;
    public DeviceOnlyBuffer sectionMetaBuffer;
    public BufferArena terrainGeometryBuffer;

    public DeviceOnlyBuffer counters;
    public DeviceOnlyBuffer uniform;
    public DeviceOnlyBuffer commands;

    //Need 4x4 shrunk depth framebuffer
    public Resources(RenderDevice device, int renderFrames, int renderDistance) {
        this.device = device;
        terrainMetaUploadStream = new UploadingBufferStream(device, renderFrames, 1<<24);
    }

    public void delete() {

    }
}
