package de.e_nexus.vrplatform.cube;

import de.e_nexus.vrplatform.scenecore.Mesh;
import de.e_nexus.vrplatform.scenecore.SceneObject;

public class Cube {

    public static SceneObject create(float r, float g, float b) {
        float[] v = {
            // front (+z), normal 0,0,1
            -0.5f, -0.5f,  0.5f,  r,g,b,  0f, 0f, 1f,
             0.5f, -0.5f,  0.5f,  r,g,b,  0f, 0f, 1f,
             0.5f,  0.5f,  0.5f,  r,g,b,  0f, 0f, 1f,
            -0.5f,  0.5f,  0.5f,  r,g,b,  0f, 0f, 1f,
            // back (-z), normal 0,0,-1  (vertices CCW when seen from -z)
             0.5f, -0.5f, -0.5f,  r,g,b,  0f, 0f,-1f,
            -0.5f, -0.5f, -0.5f,  r,g,b,  0f, 0f,-1f,
            -0.5f,  0.5f, -0.5f,  r,g,b,  0f, 0f,-1f,
             0.5f,  0.5f, -0.5f,  r,g,b,  0f, 0f,-1f,
            // left (-x), normal -1,0,0
            -0.5f, -0.5f, -0.5f,  r,g,b, -1f, 0f, 0f,
            -0.5f, -0.5f,  0.5f,  r,g,b, -1f, 0f, 0f,
            -0.5f,  0.5f,  0.5f,  r,g,b, -1f, 0f, 0f,
            -0.5f,  0.5f, -0.5f,  r,g,b, -1f, 0f, 0f,
            // right (+x), normal 1,0,0
             0.5f, -0.5f,  0.5f,  r,g,b,  1f, 0f, 0f,
             0.5f, -0.5f, -0.5f,  r,g,b,  1f, 0f, 0f,
             0.5f,  0.5f, -0.5f,  r,g,b,  1f, 0f, 0f,
             0.5f,  0.5f,  0.5f,  r,g,b,  1f, 0f, 0f,
            // top (+y), normal 0,1,0
            -0.5f,  0.5f,  0.5f,  r,g,b,  0f, 1f, 0f,
             0.5f,  0.5f,  0.5f,  r,g,b,  0f, 1f, 0f,
             0.5f,  0.5f, -0.5f,  r,g,b,  0f, 1f, 0f,
            -0.5f,  0.5f, -0.5f,  r,g,b,  0f, 1f, 0f,
            // bottom (-y), normal 0,-1,0
            -0.5f, -0.5f, -0.5f,  r,g,b,  0f,-1f, 0f,
             0.5f, -0.5f, -0.5f,  r,g,b,  0f,-1f, 0f,
             0.5f, -0.5f,  0.5f,  r,g,b,  0f,-1f, 0f,
            -0.5f, -0.5f,  0.5f,  r,g,b,  0f,-1f, 0f,
        };

        int[] idx = {
             0,  1,  2,   2,  3,  0,   // front
             4,  5,  6,   6,  7,  4,   // back
             8,  9, 10,  10, 11,  8,   // left
            12, 13, 14,  14, 15, 12,   // right
            16, 17, 18,  18, 19, 16,   // top
            20, 21, 22,  22, 23, 20,   // bottom
        };

        return new SceneObject(new Mesh(v, idx));
    }
}
