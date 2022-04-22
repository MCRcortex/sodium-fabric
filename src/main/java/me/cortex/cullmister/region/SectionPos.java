package me.cortex.cullmister.region;

import net.minecraft.util.math.ChunkSectionPos;

import java.util.Objects;

public record SectionPos(int x, int y, int z) {
    public static SectionPos from(ChunkSectionPos pos) {
        return new SectionPos(pos.getX()&0b11111, Math.floorMod(pos.getY(), Region.HEIGHT), pos.getZ()&0b11111);
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
        return (y<<10)|(z<<5)|x;
    }
}
