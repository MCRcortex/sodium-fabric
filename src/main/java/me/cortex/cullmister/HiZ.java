package me.cortex.cullmister;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.cullmister.utils.Shader;
import me.cortex.cullmister.utils.VAO;
import org.lwjgl.opengl.GL45C;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL45.*;
//TODO: SPEED THIS SHIT UP AS IT IS SLOW!!!, e.g. skip the first few layers or something and dont do the last few or something
//TODO: ADD OPTIONAL MAX MIP level e.g. 6 would only build mip chain up to 6
public class HiZ {
    Shader mipChainShader;

    public int mipDepthTex = -1;
    int sampler;
    int dummyFBO;
    VAO dummyVAO;
    int width;
    int height;
    int levels;

    Shader debugBlit;

    public HiZ() {
        sampler = glGenSamplers();
        glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        //https://github.com/nvpro-samples/gl_occlusion_culling/blob/master/cull-downsample.frag.glsl
        // TODO: maybe replace with a mix of this https://miketuritzin.com/post/hierarchical-depth-buffers/
        //  as it should give better results for non even cases
        mipChainShader = new Shader("""
                    
                    #version 330
                    /**/
                                        
                    out vec2 uv;
                                        
                    void main()
                    {
                      vec4 pos =  vec4(
                          (float( gl_VertexID    &1)) * 4.0 - 1.0,
                          (float((gl_VertexID>>1)&1)) * 4.0 - 1.0,
                          0, 1.0);
                         \s
                      uv = pos.xy * 0.5 + 0.5;
                     \s
                      gl_Position = pos;
                    }
                                        
                    """,
            """
                                        
                    #version 430\s
                    /**/
                                        
                    layout(location=0) uniform int       depthLod;
                    layout(location=1) uniform bool      evenLod;
                    layout(binding=0)  uniform sampler2D depthTex;
                                        
                    in vec2 uv;
                                        
                    void main()
                    {
                      ivec2 lodSize = textureSize(depthTex,depthLod);
                      float depth = 0;
                     \s
                      if (evenLod){
                        ivec2 offsets[] = ivec2[](
                          ivec2(0,0),
                          ivec2(0,1),
                          ivec2(1,1),
                          ivec2(1,0)
                        );
                        ivec2 coord = ivec2(gl_FragCoord.xy);
                        coord *= 2;
                       \s
                        for (int i = 0; i < 4; i++){
                          depth = max(
                            depth,\s
                            texelFetch(depthTex,
                              clamp(coord + offsets[i], ivec2(0), lodSize - ivec2(1)),
                              depthLod).r );
                        }
                      }
                      else{
                        // need this to handle non-power of two
                        // very conservative
                       \s
                        vec2 offsets[] = vec2[](
                          vec2(-1,-1),
                          vec2( 0,-1),
                          vec2( 1,-1),
                          vec2(-1, 0),
                          vec2( 0, 0),
                          vec2( 1, 0),
                          vec2(-1, 1),
                          vec2( 0, 1),
                          vec2( 1, 1)
                        );
                        vec2 coord = uv;
                        vec2 texel = 1.0/(vec2(lodSize));
                       \s
                        for (int i = 0; i < 9; i++){
                          vec2 pos = coord + offsets[i] * texel;
                          depth = max(
                            depth,\s
                            #if 1
                            texelFetch(depthTex,
                              clamp(ivec2(pos * lodSize), ivec2(0), lodSize - ivec2(1)),
                              depthLod).r\s
                            #else
                            textureLod(depthTex,
                              pos,
                              depthLod).r\s
                            #endif
                            );
                        }
                      }
                                        
                      gl_FragDepth = depth;
                    }
                    """);
        dummyFBO = glGenFramebuffers();
        dummyVAO = new VAO();

        debugBlit = new Shader("""          
                #version 450
                void main() {
                    ivec2 corners[] = ivec2[](
                      ivec2(-1,-1),
                      ivec2(1,-1),
                      ivec2(1,1),
                      
                      ivec2(-1,1),
                      ivec2(-1,-1),
                      ivec2(1,1)
                    );
                    gl_Position = vec4(corners[gl_VertexID], 0, 1.0);
                }
                """, """
                #version 450
                uniform int miplod;
                layout(binding=0) uniform sampler2D depthTex;
                out vec4 colour;
                void main() {
                    vec2 coord = gl_FragCoord.xy/textureSize(depthTex,0);
                    float z = textureLod(depthTex, coord, miplod).r;
                    float n = 1.0;
                    float f = 5000.0;
                    float c = (2) / (10000-10000*z);
                    colour.rgb = vec3(c);
                }
                """);
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height)
            return;

        levels = (int)Math.ceil(Math.log(Math.max(width, height))/Math.log(2));
        if (mipDepthTex != -1) {
            glDeleteTextures(mipDepthTex);
            mipDepthTex = 0;
        }
        //levels = Math.min(levels, 6);
        mipDepthTex = glCreateTextures(GL_TEXTURE_2D);
        //glTextureStorage2D(mipDepthTex, levels, GL_DEPTH24_STENCIL8, width, height);
        glTextureStorage2D(mipDepthTex, levels, GL_DEPTH_COMPONENT32F, width, height);
        glTextureParameteri(mipDepthTex, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTextureParameteri(mipDepthTex, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(mipDepthTex, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glTextureParameteri(mipDepthTex, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(mipDepthTex, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindFramebuffer(GL_FRAMEBUFFER, dummyFBO);
        glDrawBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        //glNamedFramebufferTexture(dummyFBO, GL_DEPTH_STENCIL_ATTACHMENT, mipDepthTex, 0);
        glNamedFramebufferTexture(dummyFBO, GL_DEPTH_ATTACHMENT, mipDepthTex, 0);
        this.width = width;
        this.height = height;
    }

    //TODO: Add optional build depth e.g. how many levels to build
    public void buildMips() {
        glBindFramebuffer(GL_FRAMEBUFFER, dummyFBO);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        {
            System.err.println("GL Framebuffer not complete");
            return;
        }
        RenderSystem.enableDepthTest();
        mipChainShader.bind();
        glDepthFunc(GL_ALWAYS);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mipDepthTex);
        //TODO: CHANGE TO USE TEXTURE UNIT SHIT
        GL45C.glBindTextureUnit(0, mipDepthTex);
        GL45C.glBindSampler(0, sampler);
        dummyVAO.bind();
        //TODO: Change from 1 bool too 2, one for width one for height, should speed up shader, will also provide better
        // results if only 1 dim is non mod 2
        boolean wasEven = width % 2 == 0 && height % 2 == 0;
        int cwidth = width / 2;
        int cheight = height / 2;
        for (int level = 1; level < levels; level++) {

            glViewport(0, 0, cwidth, cheight);
            //glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, mipDepthTex, level);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mipDepthTex, level);
            glUniform1i(0, level-1);
            glUniform1i(1, wasEven?1:0);

            glDrawArrays(GL_TRIANGLES, 0, 3);


            wasEven = cwidth % 2 == 0 && cheight % 2 == 0;
            cwidth = Math.max(1, cwidth/2);
            cheight = Math.max(1, cheight/2);
        }


        dummyVAO.unbind();
        mipChainShader.unbind();
        glViewport(0, 0, width, height);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDepthFunc(GL_LEQUAL);
        glViewport(0, 0, width, height);
    }



    public void debugBlit(int lod) {
        debugBlit.bind();
        debugBlit.setUniform("miplod", lod);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(false);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mipDepthTex);
        dummyVAO.bind();
        glDrawArrays(GL_TRIANGLES, 0, 6);
        dummyVAO.unbind();
        debugBlit.unbind();
        glDepthMask(true);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDepthFunc(GL_LEQUAL);
    }
}
