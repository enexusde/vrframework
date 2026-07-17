package de.e_nexus.vrplatform.skydome;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A camera-following sky sphere rendered with a Rayleigh/Mie atmospheric
 * scattering shader. Drawn at the far plane (translation stripped from the
 * view matrix, depth pinned via the gl_Position.xyww skybox trick) so it
 * never needs to be sized to the scene.
 */
public class Skydome {

    private static final int STACKS = 24;
    private static final int SLICES = 48;

    private int vao, vbo, ebo, indexCount;
    private ShaderProgram shader;

    public void initialize() {
        shader = new ShaderProgram("/shaders/sky_vertex.glsl", "/shaders/sky_fragment.glsl");
        shader.compile();

        float[] verts = buildSphereVertices();
        int[] indices = buildSphereIndices();
        indexCount = indices.length;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer vb = MemoryUtil.memAllocFloat(verts.length);
        try {
            vb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(vb);
        }

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = MemoryUtil.memAllocInt(indices.length);
        try {
            ib.put(indices).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(ib);
        }

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    private static float[] buildSphereVertices() {
        float[] verts = new float[(STACKS + 1) * (SLICES + 1) * 3];
        int i = 0;
        for (int stack = 0; stack <= STACKS; stack++) {
            float phi = (float) Math.PI * stack / STACKS;
            float y = (float) Math.cos(phi);
            float r = (float) Math.sin(phi);
            for (int slice = 0; slice <= SLICES; slice++) {
                float theta = (float) (2.0 * Math.PI * slice / SLICES);
                verts[i++] = r * (float) Math.cos(theta);
                verts[i++] = y;
                verts[i++] = r * (float) Math.sin(theta);
            }
        }
        return verts;
    }

    private static int[] buildSphereIndices() {
        int[] indices = new int[STACKS * SLICES * 6];
        int i = 0;
        for (int stack = 0; stack < STACKS; stack++) {
            for (int slice = 0; slice < SLICES; slice++) {
                int a = stack * (SLICES + 1) + slice;
                int b = a + SLICES + 1;
                indices[i++] = a;
                indices[i++] = b;
                indices[i++] = a + 1;
                indices[i++] = a + 1;
                indices[i++] = b;
                indices[i++] = b + 1;
            }
        }
        return indices;
    }

    public void render(Matrix4f proj, Matrix4f view, Vector3f sunDirection) {
        Matrix4f skyView = new Matrix4f(view).setTranslation(0f, 0f, 0f);

        glDepthMask(false);
        glDepthFunc(GL_LEQUAL); // fragments sit exactly at the far plane (z == w)
        glDisable(GL_CULL_FACE); // camera is inside the sphere

        shader.use();
        shader.setMatrix4f("projection", proj);
        shader.setMatrix4f("view", skyView);
        shader.setVec3("sunDirection", sunDirection);

        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);

        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LESS);
        glDepthMask(true);
    }

    public void destroy() {
        if (shader != null) shader.destroy();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }
}
