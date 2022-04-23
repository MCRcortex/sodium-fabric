package me.cortex.cullmister.region;

import net.minecraft.util.math.ChunkSectionPos;

import java.util.Objects;

public record SectionPos(int x, int y, int z) {
    public static SectionPos from(ChunkSectionPos pos) {
        return new SectionPos(pos.getX()&((1<<Region.WIDTH_BITS)-1), Math.floorMod(pos.getY(), Region.HEIGHT), pos.getZ()&((1<<Region.WIDTH_BITS)-1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SectionPos that = (SectionPos) o;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return (y<<(Region.WIDTH_BITS*2))|(z<<Region.WIDTH_BITS)|x;
    }
}
