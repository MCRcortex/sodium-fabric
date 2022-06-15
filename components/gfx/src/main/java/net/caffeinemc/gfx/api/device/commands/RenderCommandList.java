package net.caffeinemc.gfx.api.device.commands;

import net.caffeinemc.gfx.api.buffer.Buffer;
import net.caffeinemc.gfx.api.shader.MultiBlockBind;
import net.caffeinemc.gfx.api.types.ElementFormat;
import net.caffeinemc.gfx.api.types.PrimitiveType;

public interface RenderCommandList<T extends Enum<T>> {
    void bindElementBuffer(Buffer buffer);

    void bindDispatchIndirectBuffer(Buffer buffer);

    void bindVertexBuffer(T target, Buffer buffer, int offset, int stride);

    void bindCommandBuffer(Buffer buffer);

    void bindParameterCountBuffer(Buffer buffer);

    void multiDrawElementsIndirect(PrimitiveType primitiveType, ElementFormat elementType, long indirectOffset, int indirectCount);

    void multiDrawElementsIndirectCount(PrimitiveType primitiveType, ElementFormat elementType, long indirectOffset, long indirectCountOffset, int maxDrawCount, int stride);

    void drawElementsInstanced(PrimitiveType primitiveType, int count, ElementFormat elementType, long indices, int primcount);

    void dispatchCompute(int group_x, int group_y, int group_z);

    void dispatchComputeIndirect(long offset);
}
