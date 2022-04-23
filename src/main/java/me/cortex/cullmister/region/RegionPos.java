package me.cortex.cullmister.region;

import net.minecraft.util.math.ChunkSectionPos;

import java.util.Objects;

public record RegionPos(int x, int y, int z) {
    public long Long() {
        long l = 0L;
        l |= ((long)x & 4194303L) << 42;
        l |= ((long)y & 1048575L) << 0;
        return l | ((long)z & 4194303L) << 20;
    }

    public static RegionPos from(ChunkSectionPos pos) {
        return new RegionPos(pos.getX()>>Region.WIDTH_BITS, Math.floorDiv(pos.getY(), Region.HEIGHT), pos.getZ()>>Region.WIDTH_BITS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RegionPos regionPos = (RegionPos) o;
        return x == regionPos.x && y == regionPos.y && z == regionPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "RegionPos(" + x +
                ", " + y +
                ", " + z +
                ')';
    }
}
