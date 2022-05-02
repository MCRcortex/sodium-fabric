package me.cortex.cullmister.commandListStuff;

import me.cortex.cullmister.utils.ShaderPreprocessor;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.NVCommandList.*;

public class CommandListTokenWriter {
    private static final int[] SIZES = new int[] {
            4,          //TerminateSequenceCommandNV
            4,          //NOPCommandNV
            4*4,        //DrawElementsCommandNV
            4*3,        //DrawArraysCommandNV
            4*4,        //DrawElementsCommandNV
            4*3,        //DrawArraysCommandNV
            4*7,        //DrawElementsInstancedCommandNV
            4*6,        //DrawArraysInstancedCommandNV
            4*4,        //ElementAddressCommandNV
            4*4,        //AttributeAddressCommandNV
            4*3+2*2,    //UniformAddressCommandNV
            4*5,        //BlendColorCommandNV
            4*3,        //StencilRefCommandNV
            4*2,        //LineWidthCommandNV
            4*3,        //PolygonOffsetCommandNV
            4*2,        //AlphaRefCommandNV
            4*5,        //ViewportCommandNV
            4*5,        //ScissorCommandNV
            4*2,        //FrontFaceCommandNV
    };



    public static int NVHeader(int token) {
        return glGetCommandHeaderNV(token, SIZES[token]);
    }
    public static long NVHeader(long ptr, int token) {
        MemoryUtil.memPutInt(ptr, NVHeader(token));
        return ptr + 4;
    }

    /*
        NOTE: index explicitly cast to short
     */
    public static long NVTokenUBO(long ptr, int index, int shader_stage, long address) {
        ptr = NVHeader(ptr, GL_UNIFORM_ADDRESS_COMMAND_NV);
        MemoryUtil.memPutShort(ptr, (short) index);
        MemoryUtil.memPutShort(ptr+2, glGetStageIndexNV(shader_stage));
        MemoryUtil.memPutInt(ptr+4, (int)(address));
        MemoryUtil.memPutInt(ptr+8, (int)(address>>>32));
        return ptr + 12;
    }

    public static long NVTokenVBO(long ptr, int index, long address) {
        ptr = NVHeader(ptr, GL_ATTRIBUTE_ADDRESS_COMMAND_NV);
        MemoryUtil.memPutInt(ptr, index);
        MemoryUtil.memPutInt(ptr+4, (int)(address));
        MemoryUtil.memPutInt(ptr+8, (int)(address>>>32));
        return ptr + 12;
    }

    public static long NVTokenIBO(long ptr, int type, long address) {
        ptr = NVHeader(ptr, GL_ELEMENT_ADDRESS_COMMAND_NV);
        MemoryUtil.memPutInt(ptr, (int)(address));
        MemoryUtil.memPutInt(ptr+4, (int)(address>>>32));
        MemoryUtil.memPutInt(ptr+8, switch (type){
            case GL_UNSIGNED_BYTE -> 1;
            case GL_UNSIGNED_SHORT -> 2;
            case GL_UNSIGNED_INT -> 4;
            default -> {throw new IllegalArgumentException();}
        });
        return ptr + 12;
    }

    public static long NVTokenDrawElementsInstanced(long ptr, long drawIndirectCommand) {
        ptr = NVHeader(ptr, GL_DRAW_ELEMENTS_INSTANCED_COMMAND_NV);
        MemoryUtil.memCopy(ptr, drawIndirectCommand, 4*6);
        return ptr + 4*6;
    }

    public static long NVTokenDrawElementsInstanced(long ptr, int mode, int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        ptr = NVHeader(ptr, GL_DRAW_ELEMENTS_INSTANCED_COMMAND_NV);
        MemoryUtil.memPutInt(ptr + 4*0, mode);
        MemoryUtil.memPutInt(ptr + 4*1, count);
        MemoryUtil.memPutInt(ptr + 4*2, instanceCount);
        MemoryUtil.memPutInt(ptr + 4*3, firstIndex);
        MemoryUtil.memPutInt(ptr + 4*4, baseVertex);
        MemoryUtil.memPutInt(ptr + 4*5, baseVertex);
        return ptr + 4*6;
    }

    public static long NVTokenDrawElements(long ptr, int count, int firstIndex, int baseIndex) {
        ptr = NVHeader(ptr, GL_DRAW_ELEMENTS_COMMAND_NV);
        MemoryUtil.memPutInt(ptr, count);
        MemoryUtil.memPutInt(ptr+4, firstIndex);
        MemoryUtil.memPutInt(ptr+8, baseIndex);
        return ptr + 12;
    }

}
