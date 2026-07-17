package de.e_nexus.vrplatform.texturedcube;

import de.e_nexus.vrplatform.gl.Texture2D;

/** A cube ("box") whose faces show a tileable texture instead of a flat color. */
public class TexturedCube {

    /**
     * @param repeats how many times the texture repeats across each face
     *                (UV coordinates run 0..repeats; the texture must use
     *                GL_REPEAT wrapping — see {@link Texture2D}).
     */
    public static TexturedSceneObject create(Texture2D texture, float repeats) {
        float r = repeats;
        float[] v = {
            // front (+z), normal 0,0,1
            -0.5f, -0.5f,  0.5f,  0f, 0f,  0f, 0f, 1f,
             0.5f, -0.5f,  0.5f,  r,  0f,  0f, 0f, 1f,
             0.5f,  0.5f,  0.5f,  r,  r,   0f, 0f, 1f,
            -0.5f,  0.5f,  0.5f,  0f, r,   0f, 0f, 1f,
            // back (-z), normal 0,0,-1  (vertices CCW when seen from -z)
             0.5f, -0.5f, -0.5f,  0f, 0f,  0f, 0f,-1f,
            -0.5f, -0.5f, -0.5f,  r,  0f,  0f, 0f,-1f,
            -0.5f,  0.5f, -0.5f,  r,  r,   0f, 0f,-1f,
             0.5f,  0.5f, -0.5f,  0f, r,   0f, 0f,-1f,
            // left (-x), normal -1,0,0
            -0.5f, -0.5f, -0.5f,  0f, 0f, -1f, 0f, 0f,
            -0.5f, -0.5f,  0.5f,  r,  0f, -1f, 0f, 0f,
            -0.5f,  0.5f,  0.5f,  r,  r,  -1f, 0f, 0f,
            -0.5f,  0.5f, -0.5f,  0f, r,  -1f, 0f, 0f,
            // right (+x), normal 1,0,0
             0.5f, -0.5f,  0.5f,  0f, 0f,  1f, 0f, 0f,
             0.5f, -0.5f, -0.5f,  r,  0f,  1f, 0f, 0f,
             0.5f,  0.5f, -0.5f,  r,  r,   1f, 0f, 0f,
             0.5f,  0.5f,  0.5f,  0f, r,   1f, 0f, 0f,
            // top (+y), normal 0,1,0
            -0.5f,  0.5f,  0.5f,  0f, 0f,  0f, 1f, 0f,
             0.5f,  0.5f,  0.5f,  r,  0f,  0f, 1f, 0f,
             0.5f,  0.5f, -0.5f,  r,  r,   0f, 1f, 0f,
            -0.5f,  0.5f, -0.5f,  0f, r,   0f, 1f, 0f,
            // bottom (-y), normal 0,-1,0
            -0.5f, -0.5f, -0.5f,  0f, 0f,  0f,-1f, 0f,
             0.5f, -0.5f, -0.5f,  r,  0f,  0f,-1f, 0f,
             0.5f, -0.5f,  0.5f,  r,  r,   0f,-1f, 0f,
            -0.5f, -0.5f,  0.5f,  0f, r,   0f,-1f, 0f,
        };

        int[] idx = {
             0,  1,  2,   2,  3,  0,   // front
             4,  5,  6,   6,  7,  4,   // back
             8,  9, 10,  10, 11,  8,   // left
            12, 13, 14,  14, 15, 12,   // right
            16, 17, 18,  18, 19, 16,   // top
            20, 21, 22,  22, 23, 20,   // bottom
        };

        return new TexturedSceneObject(new TexturedMesh(v, idx), texture);
    }
}
