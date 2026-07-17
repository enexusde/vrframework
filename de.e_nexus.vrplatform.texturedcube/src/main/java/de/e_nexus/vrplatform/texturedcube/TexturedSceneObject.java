package de.e_nexus.vrplatform.texturedcube;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import de.e_nexus.vrplatform.gl.Texture2D;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A mesh rendered with a sampled, tileable texture instead of a flat vertex
 * color. The texture is not owned/destroyed here — the same {@link Texture2D}
 * is typically shared across several textured objects.
 */
public class TexturedSceneObject {

    protected final TexturedMesh mesh;
    protected final Texture2D texture;
    protected final Vector3f position = new Vector3f();
    protected final Quaternionf rotation = new Quaternionf();
    protected final Vector3f scale = new Vector3f(1f, 1f, 1f);

    public TexturedSceneObject(TexturedMesh mesh, Texture2D texture) {
        this.mesh = mesh;
        this.texture = texture;
    }

    public void render(ShaderProgram shader) {
        Matrix4f model = new Matrix4f()
            .translate(position)
            .rotate(rotation)
            .scale(scale);
        shader.setMatrix4f("model", model);

        texture.bind(0);
        shader.setInt("uTexture", 0);

        mesh.render();
    }

    public TexturedSceneObject position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    public TexturedSceneObject scale(float x, float y, float z) {
        scale.set(x, y, z);
        return this;
    }

    public TexturedSceneObject rotateY(float angleRad) {
        rotation.rotateY(angleRad);
        return this;
    }

    public void destroy() {
        mesh.destroy();
    }
}
