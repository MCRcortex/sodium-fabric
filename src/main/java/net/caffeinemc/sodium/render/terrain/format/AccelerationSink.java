package net.caffeinemc.sodium.render.terrain.format;

public interface AccelerationSink {
    AccelerationBufferSink getAccelerationBuffer();
    void writeAccelerationVertex(float posX, float posY, float posZ, int meta);

    void setAccelerationBuffer(AccelerationBufferSink accelerationSink);
}
