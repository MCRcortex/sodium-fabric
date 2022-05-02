package me.cortex.cullmister.utils;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.joml.*;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glUniform1ui;

public class Shader implements IBindable {
    public int view_matrix_location;
    private int programObject;
    private int vertexShaderObject;
    private int fragmentShaderObject;
    private Object2IntMap<String> uniforms = new Object2IntOpenHashMap<>();

    public static Path get(String path) {
        try {
            return Path.of(ClassLoader.getSystemClassLoader().getResource(path).toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Shader(Path vertex, Path fragment) {
        this(ShaderPreprocessor.load(vertex), ShaderPreprocessor.load(fragment));
    }
    public Shader(String vertex, String fragment) {
        programObject = glCreateProgram();

        vertexShaderObject = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShaderObject, vertex);
        glCompileShader(vertexShaderObject);
        if (glGetShaderi(vertexShaderObject, GL_COMPILE_STATUS) != 1) {
            System.err.println(glGetShaderInfoLog(vertexShaderObject));
            System.exit(1);
        }

        fragmentShaderObject = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShaderObject, fragment);
        glCompileShader(fragmentShaderObject);
        if (glGetShaderi(fragmentShaderObject, GL_COMPILE_STATUS) != 1) {
            System.err.println(glGetShaderInfoLog(fragmentShaderObject));
            System.exit(1);
        }

        glAttachShader(programObject, vertexShaderObject);
        glAttachShader(programObject, fragmentShaderObject);

        //glBindAttribLocation(programObject, 0, "vertices");
        //glBindAttribLocation(programObject, 1, "textures");

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

    /*
    @Override
    protected void finalize() throws Throwable {
        glDetachShader(programObject, vertexShaderObject);
        glDetachShader(programObject, fragmentShaderObject);
        glDeleteShader(vertexShaderObject);
        glDeleteShader(fragmentShaderObject);
        glDeleteProgram(programObject);
        super.finalize();
    }
     */

    public void setUniform(String uniformName, int value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform1i(location, value);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, float value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform1f(location, value);
        else throw new IllegalArgumentException();
    }

    public void setUniformU(String uniformName, int value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform1ui(location, value);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, Vector4f value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform4f(location, value.x, value.y, value.z, value.w);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, Vector2f value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform2f(location, value.x, value.y);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, Vector3f value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform3f(location, value.x, value.y, value.z);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, Vector2i value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        if (location != -1) glUniform2i(location, value.x, value.y);
        else throw new IllegalArgumentException();
    }

    public void setUniform(String uniformName, Matrix4f value) {
        int location = uniforms.computeIntIfAbsent(uniformName, (name)->glGetUniformLocation(programObject, uniformName));
        FloatBuffer matrixData = BufferUtils.createFloatBuffer(16);
        value.get(matrixData);
        if (location != -1) glUniformMatrix4fv(location, false, matrixData);
        else throw new IllegalArgumentException();
    }

    public void bind() {
        glUseProgram(programObject);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public int getId() {
        return programObject;
    }

    public Shader setViewMatrix(String name) {
        view_matrix_location = glGetUniformLocation(programObject, name);
        return this;
    }

    public void delete() {
        glDeleteProgram(programObject);
    }
}

