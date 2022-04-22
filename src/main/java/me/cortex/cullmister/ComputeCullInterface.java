package me.cortex.cullmister;

import me.cortex.cullmister.region.Region;
import me.cortex.cullmister.utils.CShader;
import org.joml.Matrix4f;

public class ComputeCullInterface {
    CShader cullShader;
    HiZ hiz;
    public ComputeCullInterface(HiZ hiZ) {
        this.hiz = hiZ;
        cullShader = CShader.fromResource("assets/cullmister/culler/occlusionCompute.comp");
    }

    //Begin batch processing
    public void begin(Matrix4f vmpt, int renderId) {
        cullShader.bind();
        cullShader.setUniform("viewModelProjectionTranslate", vmpt);
        cullShader.setUniformU("renderId", renderId);
    }

    //Enqueue process
    public void process(Region region) {
        
    }

    //End process
    public void end() {
        cullShader.unbind();
    }
}
