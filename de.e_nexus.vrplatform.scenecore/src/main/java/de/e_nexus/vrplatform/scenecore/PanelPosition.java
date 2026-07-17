package de.e_nexus.vrplatform.scenecore;

import org.joml.Vector3f;

/** World-space position of a panel's center, in meters. */
public record PanelPosition(float x, float y, float z) {

    public Vector3f toVector3f() {
        return new Vector3f(x, y, z);
    }
}
