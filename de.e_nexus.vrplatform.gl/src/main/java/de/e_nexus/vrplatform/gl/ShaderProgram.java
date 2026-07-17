package de.e_nexus.vrplatform.gl;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {

    // Resolves vertexPath/fragmentPath against the caller's class rather
    // than this class: under OSGi, this class is loaded by the gl bundle
    // while callers (render, scenecore, skydome, ...) are typically a
    // different bundle whose own classpath holds the .glsl resources, so
    // getClass().getResourceAsStream would never find them - same reason
    // Texture2D takes a resourceOwner.
    private final Class<?> resourceOwner;
    private int programId;
    private final String vertexPath;
    private final String fragmentPath;

    public ShaderProgram(Class<?> resourceOwner, String vertexPath, String fragmentPath) {
        this.resourceOwner = resourceOwner;
        this.vertexPath   = vertexPath;
        this.fragmentPath = fragmentPath;
    }

    public void compile() {
        int vert = compileShader(GL_VERTEX_SHADER,   vertexPath);
        int frag = compileShader(GL_FRAGMENT_SHADER, fragmentPath);

        programId = glCreateProgram();
        glAttachShader(programId, vert);
        glAttachShader(programId, frag);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link error:\n" + glGetProgramInfoLog(programId));
        }

        glDeleteShader(vert);
        glDeleteShader(frag);
    }

    private int compileShader(int type, String path) {
        int shader = glCreateShader(type);
        glShaderSource(shader, loadResource(path));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String label = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            throw new RuntimeException(label + " shader error in " + path + ":\n" + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private String loadResource(String path) {
        try (InputStream is = resourceOwner.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Shader resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader: " + path, e);
        }
    }

    public void use() {
        glUseProgram(programId);
    }

    public void setMatrix4f(String name, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            matrix.get(buf);
            glUniformMatrix4fv(glGetUniformLocation(programId, name), false, buf);
        }
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(glGetUniformLocation(programId, name), x, y, z);
    }

    public void setVec3(String name, Vector3f v) {
        glUniform3f(glGetUniformLocation(programId, name), v.x, v.y, v.z);
    }

    public void setVec2(String name, float x, float y) {
        glUniform2f(glGetUniformLocation(programId, name), x, y);
    }

    public void setInt(String name, int value) {
        glUniform1i(glGetUniformLocation(programId, name), value);
    }

    public void destroy() {
        glDeleteProgram(programId);
    }
}
