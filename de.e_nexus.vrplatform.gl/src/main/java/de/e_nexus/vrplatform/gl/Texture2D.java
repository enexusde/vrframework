package de.e_nexus.vrplatform.gl;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Loads a classpath image (PNG/JPG) as a tileable 2D OpenGL texture:
 * GL_REPEAT wrapping so UV coordinates beyond [0,1] tile the pattern, plus
 * mipmapping for a clean look when the texture is seen at a distance.
 */
public class Texture2D {

    // Resolves resourcePath against the caller's class rather than this
    // class: under OSGi, this class is loaded by the framework bundle while
    // callers (e.g. demo) are typically a different bundle whose own
    // classpath holds the image, so Texture2D.class.getResourceAsStream
    // would never find it.
    private final Class<?> resourceOwner;
    private final String resourcePath;
    private int textureId;

    public Texture2D(Class<?> resourceOwner, String resourcePath) {
        this.resourceOwner = resourceOwner;
        this.resourcePath = resourcePath;
    }

    public void initialize() {
        BufferedImage img = loadImage(resourceOwner, resourcePath);
        int width = img.getWidth();
        int height = img.getHeight();

        int[] pixels = new int[width * height];
        img.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buf = MemoryUtil.memAlloc(width * height * 4);
        try {
            // AWT row 0 is the top of the image; OpenGL's texture row 0 is
            // conventionally the bottom, so flip vertically while packing.
            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x++) {
                    int p = pixels[y * width + x];
                    buf.put((byte) ((p >> 16) & 0xFF));
                    buf.put((byte) ((p >>  8) & 0xFF));
                    buf.put((byte) ( p        & 0xFF));
                    buf.put((byte) ((p >> 24) & 0xFF));
                }
            }
            buf.flip();

            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private static BufferedImage loadImage(Class<?> resourceOwner, String resourcePath) {
        try (InputStream is = resourceOwner.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Texture resource not found: " + resourcePath);
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + resourcePath, e);
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }
}
