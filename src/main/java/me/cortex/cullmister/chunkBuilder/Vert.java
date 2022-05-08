package me.cortex.cullmister.chunkBuilder;

import org.joml.Vector3f;

record Vert(float posX, float posY, float posZ, int color, float u, float v, int light) {
    public Vector3f pos() {
        return new Vector3f(posX, posY, posZ);
    }

    public boolean isSamePos(Vert vert) {
        return pos().sub(vert.pos()).absolute().distanceSquared(0,0,0)<0.0001f;
    }

    //TODO: add flags to match different parts
    public boolean isSimilar(Vert other) {
        return other.color == this.color;
    }
}
