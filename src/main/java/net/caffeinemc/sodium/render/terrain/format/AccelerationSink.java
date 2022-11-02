package net.caffeinemc.sodium.render.terrain.format;

public interface AccelerationSink {
    AccelerationBufferSink getAccelerationBuffer();
    void writeAccelerationVertex(float posX, float posY, float posZ);

    void setAccelerationBuffer(AccelerationBufferSink accelerationSink);
}
