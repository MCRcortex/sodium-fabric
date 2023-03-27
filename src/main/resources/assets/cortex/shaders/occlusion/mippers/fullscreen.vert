#version 460
void main()
{
    gl_Position = vec4(fma(float(gl_VertexID&1),4.0,-1.0), fma(float((gl_VertexID>>1)&1),4.0, -1.0), 0.0, 1.0);
}