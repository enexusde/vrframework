package de.e_nexus.vrplatform.gl;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Two-pass separable Gaussian blur over a fullscreen quad. Used to give
 * unfocused (semi-transparent) text panels a frosted-glass backdrop instead
 * of a sharp view of whatever is behind them.
 */
public class BlurPass {

    private final int width;
    private final int height;
    private ShaderProgram shader;
    private int vao, vbo;
    private EyeFramebuffer horizontalFbo;
    private EyeFramebuffer verticalFbo;

    public BlurPass(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void initialize() {
        shader = new ShaderProgram("/shaders/blur_vertex.glsl", "/shaders/blur_fragment.glsl");
        shader.compile();

        horizontalFbo = new EyeFramebuffer(width, height);
        horizontalFbo.initialize();
        verticalFbo = new EyeFramebuffer(width, height);
        verticalFbo.initialize();

        float[] verts = {
            -1f, -1f,   1f, -1f,   1f, 1f,
            -1f, -1f,   1f,  1f,  -1f, 1f,
        };

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(verts.length);
        try {
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(fb);
        }

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    /** Blurs sourceTexture (must be width x height) and returns the resulting texture id. */
    public int blur(int sourceTexture) {
        // These FBOs' depth buffers are never cleared; with depth testing on
        // (enabled globally) the fullscreen quad could be discarded against
        // whatever garbage is left in them, so disable it for this pass.
        boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);

        shader.use();
        shader.setVec2("uTexelSize", 1f / width, 1f / height);
        glBindVertexArray(vao);

        horizontalFbo.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        shader.setInt("uSource", 0);
        shader.setVec2("uDirection", 1f, 0f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        verticalFbo.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        glBindTexture(GL_TEXTURE_2D, horizontalFbo.getColorTexture());
        shader.setVec2("uDirection", 0f, 1f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindVertexArray(0);
        if (depthWasEnabled) {
            glEnable(GL_DEPTH_TEST);
        }
        return verticalFbo.getColorTexture();
    }

    public void destroy() {
        if (shader != null) shader.destroy();
        if (horizontalFbo != null) horizontalFbo.destroy();
        if (verticalFbo != null) verticalFbo.destroy();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
