package de.e_nexus.vrplatform.scenecore;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SceneObject {

    protected final Mesh mesh;
    protected final Vector3f position = new Vector3f();
    protected final Quaternionf rotation = new Quaternionf();
    protected final Vector3f scale = new Vector3f(1f, 1f, 1f);

    public SceneObject(Mesh mesh) {
        this.mesh = mesh;
    }

    public void render(ShaderProgram shader) {
        Matrix4f model = new Matrix4f()
            .translate(position)
            .rotate(rotation)
            .scale(scale);
        shader.setMatrix4f("model", model);
        mesh.render();
    }

    public SceneObject position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    public Vector3f position() {
        return new Vector3f(position);
    }

    public SceneObject scale(float x, float y, float z) {
        scale.set(x, y, z);
        return this;
    }

    public SceneObject rotateY(float angleRad) {
        rotation.rotateY(angleRad);
        return this;
    }

    public SceneObject rotateX(float angleRad) {
        rotation.rotateX(angleRad);
        return this;
    }

    public void destroy() {
        mesh.destroy();
    }
}
