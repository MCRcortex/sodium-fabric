package me.cortex.cullmister.commandListStuff;

import me.cortex.cullmister.region.Region;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.cortex.cullmister.commandListStuff.CommandListTokenWriter.*;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_SHORT;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_COPY;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL42C.glDrawElementsInstancedBaseVertexBaseInstance;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.NVCommandList.*;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV;


//TODO: Since using a commandlist, can actually have an IBO PER CHUNK/chunk face, and actually generate merged quads
// when generating subchunks


public class CommandList {
    int commandCount = 3;
    public CommandList() {
        /*

         */
    }

    public void draw(int IBO, Region region, Matrix4f transform) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);


        long[] holder = new long[1];
        int uboBuffer = glCreateBuffers();
        glNamedBufferData(uboBuffer, 1000000, GL_DYNAMIC_COPY);

        glGetNamedBufferParameterui64vNV(uboBuffer,GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(uboBuffer, GL_READ_ONLY);
        long uboAddr = holder[0];

        glGetNamedBufferParameterui64vNV(IBO,GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(IBO, GL_READ_ONLY);
        long iboAddr = holder[0];



        long a = nglMapNamedBuffer(uboBuffer, GL_READ_WRITE);
        transform.getToAddress(a);
        /*
        transform.getColumn(0,new Vector4f()).getToAddress(a);
        transform.getColumn(1,new Vector4f()).getToAddress(a+4*4);
        transform.getColumn(2,new Vector4f()).getToAddress(a+4*4*2);
        transform.getColumn(3,new Vector4f()).getToAddress(a+4*4*3);
         */
        glUnmapNamedBuffer(uboBuffer);
        glFinish();



        int commandBuffer = glCreateBuffers();
        glNamedBufferData(commandBuffer, 1000000, GL_DYNAMIC_COPY);
        nglClearNamedBufferData(commandBuffer, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        a = nglMapNamedBuffer(commandBuffer, GL_WRITE_ONLY);
        a = NVTokenUBO(a, 0, GL_VERTEX_SHADER, uboAddr);
        a = NVTokenIBO(a, GL_UNSIGNED_SHORT, iboAddr);
        long vbo =  region.sections.values().stream().findFirst().get().vertexData.addr;
        a = NVTokenVBO(a, 0,vbo);
        a = NVTokenVBO(a, 1, region.draw.drawMeta.addr);
        a = NVTokenDrawElementsInstanced(a, GL_TRIANGLES, 6*200,1,0,0,0);
        glUnmapNamedBuffer(commandBuffer);
        glFinish();

        glGetNamedBufferParameterui64vNV(commandBuffer,GL_BUFFER_GPU_ADDRESS_NV, holder);
        glMakeNamedBufferResidentNV(commandBuffer, GL_READ_ONLY);
        long cbAddr = holder[0];




        glVertexAttribFormat(1, 3, GL_SHORT, true, 0);
        glVertexAttribFormat(2, 4, GL_UNSIGNED_BYTE, true, 8);
        glVertexAttribFormat(3, 2, GL_UNSIGNED_SHORT, true, 12);
        glVertexAttribFormat(4, 2, GL_UNSIGNED_SHORT, true, 16);

        glVertexAttribBinding(1, 0);
        glVertexAttribBinding(2, 0);
        glVertexAttribBinding(3, 0);
        glVertexAttribBinding(4, 0);

        glVertexAttribFormat(0, 3, GL_FLOAT,false, 0);

        glVertexAttribBinding(0, 1);
        glVertexBindingDivisor(1, 1);

        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);
        glEnableVertexAttribArray(4);

        glEnableVertexAttribArray(0);









        glBindBufferBase(GL_UNIFORM_BUFFER, 0, uboBuffer);
        glBindVertexBuffer(0, 0, 0, 20);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, IBO);
        glBindVertexBuffer(1, 0, 0, 3*4);

        glFinish();

        ByteBuffer buffer = ByteBuffer.allocateDirect(100).order(ByteOrder.nativeOrder());
        buffer.asLongBuffer().put(0,cbAddr);
        buffer.asIntBuffer().put(2,10000);

        glEnableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        glEnableClientState(GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        glEnableClientState(GL_ELEMENT_ARRAY_UNIFIED_NV);
        //nglDrawCommandsNV(GL_TRIANGLES, commandBuffer, MemoryUtil.memAddress(buffer), MemoryUtil.memAddress(buffer)+8, 1);
        nglDrawCommandsAddressNV(GL_TRIANGLES, MemoryUtil.memAddress(buffer), MemoryUtil.memAddress(buffer)+8, 1);
        glDisableClientState(GL_UNIFORM_BUFFER_UNIFIED_NV);
        glDisableClientState(GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        glDisableClientState(GL_ELEMENT_ARRAY_UNIFIED_NV);

        //glDrawElements(GL_TRIANGLES, 6*100, GL_UNSIGNED_SHORT, 0);
        glFinish();
        glDeleteBuffers(commandBuffer);
        glDeleteBuffers(uboBuffer);
        //glDrawElementsInstancedBaseVertexBaseInstance(GL_TRIANGLES, 6*100, GL_UNSIGNED_SHORT, 0, 1,0,0);

        GL20C.glDisableVertexAttribArray(0);
        GL20C.glDisableVertexAttribArray(1);
        GL20C.glDisableVertexAttribArray(2);
        GL20C.glDisableVertexAttribArray(3);
        GL20C.glDisableVertexAttribArray(4);
        glBindVertexArray(0);
        glDeleteVertexArrays(vao);

        glMakeNamedBufferNonResidentNV(IBO);


    }
}
