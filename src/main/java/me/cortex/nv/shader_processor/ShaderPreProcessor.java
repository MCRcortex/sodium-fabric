package me.cortex.nv.shader_processor;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.GLSLParser;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;

public class ShaderPreProcessor {
    ASTParser parser;
    TranslationUnit unit;
    private ShaderPreProcessor(String source) {
        parser = new ASTParser();
        parser.getLexer().enableCustomDirective = true;
        //parser.getLexer().enableIncludeDirective = true;
        unit = parser.parseTranslationUnit(source);
    }

    public static String process(String shaderSource) {
        return new ShaderPreProcessor(shaderSource).process();
    }

    private String process() {
        return null;
    }
}
