package me.jellysquid.mods.sodium.mixin.core;

import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FrustumIntersection.class)
public interface FrustumIntersectionAccessor {
    @Accessor
    Vector4f[] getPlanes();
}
