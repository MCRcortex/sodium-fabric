package net.caffeinemc.sodium.vkinterop;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.caffeinemc.gfx.api.pipeline.state.BlendFunc;
import net.caffeinemc.gfx.api.pipeline.state.DepthFunc;
import net.caffeinemc.gfx.opengl.GlEnum;
import org.lwjgl.opengl.GL45C;

import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.*;

public class VkEnum {


    private static final int[] BLEND_SRC_FACTORS = build(BlendFunc.SrcFactor.class, (map) -> {
        map.put(BlendFunc.SrcFactor.ZERO,                       VK_BLEND_FACTOR_ZERO);
        map.put(BlendFunc.SrcFactor.ONE,                        VK_BLEND_FACTOR_ONE);
        map.put(BlendFunc.SrcFactor.SRC_COLOR,                  VK_BLEND_FACTOR_SRC_COLOR);
        map.put(BlendFunc.SrcFactor.ONE_MINUS_SRC_COLOR,        VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR);
        map.put(BlendFunc.SrcFactor.DST_COLOR,                  VK_BLEND_FACTOR_DST_COLOR);
        map.put(BlendFunc.SrcFactor.ONE_MINUS_DST_COLOR,        VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR);
        map.put(BlendFunc.SrcFactor.SRC_ALPHA,                  VK_BLEND_FACTOR_SRC_ALPHA);
        map.put(BlendFunc.SrcFactor.ONE_MINUS_SRC_ALPHA,        VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
        map.put(BlendFunc.SrcFactor.DST_ALPHA,                  VK_BLEND_FACTOR_DST_ALPHA);
        map.put(BlendFunc.SrcFactor.ONE_MINUS_DST_ALPHA,        VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA);
        map.put(BlendFunc.SrcFactor.CONSTANT_COLOR,             VK_BLEND_FACTOR_CONSTANT_COLOR);
        map.put(BlendFunc.SrcFactor.ONE_MINUS_CONSTANT_COLOR,   VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR);
        map.put(BlendFunc.SrcFactor.CONSTANT_ALPHA,             VK_BLEND_FACTOR_CONSTANT_ALPHA);
        map.put(BlendFunc.SrcFactor.ONE_MINUS_CONSTANT_ALPHA,   VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA);
        map.put(BlendFunc.SrcFactor.SRC_ALPHA_SATURATE,         VK_BLEND_FACTOR_SRC_ALPHA_SATURATE);
    });

    private static final int[] BLEND_DST_FACTORS = build(BlendFunc.DstFactor.class, (map) -> {
        map.put(BlendFunc.DstFactor.ZERO,                       VK_BLEND_FACTOR_ZERO);
        map.put(BlendFunc.DstFactor.ONE,                        VK_BLEND_FACTOR_ONE);
        map.put(BlendFunc.DstFactor.SRC_COLOR,                  VK_BLEND_FACTOR_SRC_COLOR);
        map.put(BlendFunc.DstFactor.ONE_MINUS_SRC_COLOR,        VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR);
        map.put(BlendFunc.DstFactor.DST_COLOR,                  VK_BLEND_FACTOR_DST_COLOR);
        map.put(BlendFunc.DstFactor.ONE_MINUS_DST_COLOR,        VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR);
        map.put(BlendFunc.DstFactor.SRC_ALPHA,                  VK_BLEND_FACTOR_SRC_ALPHA);
        map.put(BlendFunc.DstFactor.ONE_MINUS_SRC_ALPHA,        VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
        map.put(BlendFunc.DstFactor.DST_ALPHA,                  VK_BLEND_FACTOR_DST_ALPHA);
        map.put(BlendFunc.DstFactor.ONE_MINUS_DST_ALPHA,        VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA);
        map.put(BlendFunc.DstFactor.CONSTANT_COLOR,             VK_BLEND_FACTOR_CONSTANT_COLOR);
        map.put(BlendFunc.DstFactor.ONE_MINUS_CONSTANT_COLOR,   VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR);
        map.put(BlendFunc.DstFactor.CONSTANT_ALPHA,             VK_BLEND_FACTOR_CONSTANT_ALPHA);
        map.put(BlendFunc.DstFactor.ONE_MINUS_CONSTANT_ALPHA,   VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA);
    });

    public static int from(DepthFunc depthFunc) {
        return GlEnum.from(depthFunc)-0x200;
    }

    public static int from(BlendFunc.SrcFactor srcRGB) {
        return BLEND_SRC_FACTORS[srcRGB.ordinal()];
    }

    public static int from(BlendFunc.DstFactor dstRGB) {
        return BLEND_DST_FACTORS[dstRGB.ordinal()];
    }

    private static <T extends Enum<T>> int[] build(Class<T> type, Consumer<Reference2IntMap<T>> consumer) {
        Enum<T>[] universe = type.getEnumConstants();

        Reference2IntMap<T> map = new Reference2IntOpenHashMap<>(universe.length);
        map.defaultReturnValue(-1);

        consumer.accept(map);

        int[] values = new int[universe.length];

        for (Enum<T> e : universe) {
            int value = map.getInt(e);

            if (value == -1) {
                throw new RuntimeException("No mapping defined for " + e.name());
            }

            values[e.ordinal()] = value;
        }

        return values;
    }
}
