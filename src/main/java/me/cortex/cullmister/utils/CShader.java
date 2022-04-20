package me.cortex.cullmister.utils;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glUniform1ui;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

public class CShader implements IBindable {
    private boolean bound;
    private int programObject;
    private int computeShaderObject;
    private Object2IntMap<String> uniforms = new Object2IntOpenHashMap<>();
    private static String getAll(Path f) {
        try {
            return new String(Files.readAllBytes(f));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<name>.*)>");

    private static List<String> resolver(Path f) {
        try {
            LinkedList<String> lines = new LinkedList<>();
            for (String line : Files.readAllLines(f)) {
                if (line.startsWith("#import")) {
                    Matcher res = IMPORT_PATTERN.matcher(line);
                    if (!res.matches()) {
                        throw new IllegalStateException("Malformed #include");
                    }
                    String name = res.group("name");
                    lines.addAll(resolver(f.getParent().resolve(name)));
                } else {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Could not open file "+f);
        }
    }

    public CShader(Path f) {
        this(String.join("\n", resolver(f)));
    }

    public CShader(String code) {
        programObject = glCreateProgram();
        computeShaderObject = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(computeShaderObject, code);
        glCompileShader(computeShaderObject);
        if (glGetShaderi(computeShaderObject, GL_COMPILE_STATUS) != 1) {
            System.err.println(glGetShaderInfoLog(computeShaderObject));
            System.exit(1);
        }
        glAttachShader(programObject, computeShaderObject);
        glLinkProgram(programObject);
        if (glGetProgrami(programObject, GL_LINK_STATUS) != 1) {
            System.err.println(glGetProgramInfoLog(programObject));
            System.exit(1);
        }
        glValidateProgram(programObject);
        if (glGetProgrami(programObject, GL_VALIDATE_STATUS) != 1) {
            System.err.println(glGetProgramInfoLog(programObject));
            System.exit(1);
        }
    }

    public void bind() {
        bound = true;
        glUseProgram(programObject);
    }

    public void unbind() {
        bound = false;
        glUseProgram(0);
    }

    public void dispatch(int x, int y, int z) {
        if (!bound)
            bind();
        glDispatchCompute(x, y, z);
    }

    public void setUniformU(String uniformName, int value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform1ui(location, value);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, Matrix4f value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        FloatBuffer matrixData = BufferUtils.createFloatBuffer(16);
        value.get(matrixData);
        if (location != -1) glUniformMatrix4fv(location, false, matrixData);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, int value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform1i(location, value);
        else throw new IllegalArgumentException();
    }
}
