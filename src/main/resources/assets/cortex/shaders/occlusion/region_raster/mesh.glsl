#version 460
#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_bindless_texture : require



layout(local_size_x = 8) in;
layout(triangles, max_vertices=8, max_primitives=12) out;

//NOTE: can use gl_MeshPrimitivesNV[].gl_PrimitiveID to specify the cube id

//layout(location=1) uniform uint64_t *regionData;

void main() {
    //TODO: maybe use shuffle operations to compute the shape
    uint64_t data = 0;//regionData[];
    uint8_t shapeY = (uint8_t)((data>>62)&0x3);//(technically dont need the 0x3)
    uint8_t shapeX = (uint8_t)((data>>59)&0x7);
    uint8_t shapeZ = (uint8_t)((data>>56)&0x7);
    uint8_t count = (uint8_t)((data>>48)&0xFF);
    int8_t startY = (int8_t)(data>>40);             //In chunk coordinates
    int32_t startX = (((int32_t)(data>>8))>>20);    //In chunk coordinates
    int32_t startZ = (((int32_t)(data<<12))>>20);   //In chunk coordinates
    //TODO: Look into only doing 4 locals, for 2 reasons, its more effective for reducing duplicate computation and bandwidth
    // it also means that each thread can emit 3 primatives, 9 indicies each


    gl_MeshVerticesNV[0].gl_Position = vec4(-1.0,-1.0,0.0,2.0);
    gl_MeshVerticesNV[1].gl_Position = vec4(1.0,-1.0,0.0,2.0);
    gl_MeshVerticesNV[2].gl_Position = vec4(0.0,1.0,0.0,2.0);
    gl_PrimitiveIndicesNV[0] = 0;
    gl_PrimitiveIndicesNV[1] = 1;
    gl_PrimitiveIndicesNV[2] = 2;
    gl_PrimitiveCountNV = 1;
    gl_MeshPrimitivesNV[0].gl_PrimitiveID = 0;
}