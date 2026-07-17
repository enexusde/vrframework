package de.e_nexus.vrplatform.cuboidframe;

import de.e_nexus.vrplatform.scenecore.FrameElement;
import de.e_nexus.vrplatform.scenecore.Mesh;
import de.e_nexus.vrplatform.scenecore.SceneObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A wireframe frame around a cuboid: twelve straight bars of square
 * cross-section, one running along each edge. Bars share their full edge
 * length, so they overlap (and thus visually join) inside the corner cubes.
 *
 * <p>Besides its mesh, a frame carries a {@link FrameElement} tree as its
 * layout model: a single root node, either a leaf {@link PanelComponent} or
 * a {@link FrameSplit} dividing the interior further. The root is unset
 * ({@code null}) until {@link #setTree(FrameElement)} is called.
 */
public class CuboidFrame {

    private final SceneObject mesh;
    private FrameElement tree;

    public CuboidFrame(float width, float height, float depth, float barThickness, float r, float g, float b) {
        this.mesh = buildMesh(width, height, depth, barThickness, r, g, b);
    }

    public SceneObject mesh() {
        return mesh;
    }

    public FrameElement tree() {
        return tree;
    }

    public void setTree(FrameElement tree) {
        this.tree = tree;
    }

    private static SceneObject buildMesh(float width, float height, float depth, float barThickness, float r,
            float g, float b) {
        var halfW = width / 2f;
        var halfH = height / 2f;
        var halfD = depth / 2f;
        var halfT = barThickness / 2f;

        var vertices = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();

        for (var y : new float[] { -halfH, halfH }) {
            for (var z : new float[] { -halfD, halfD }) {
                addBox(vertices, indices, -halfW, halfW, y - halfT, y + halfT, z - halfT, z + halfT, r, g, b);
            }
        }
        for (var x : new float[] { -halfW, halfW }) {
            for (var z : new float[] { -halfD, halfD }) {
                addBox(vertices, indices, x - halfT, x + halfT, -halfH, halfH, z - halfT, z + halfT, r, g, b);
            }
        }
        for (var x : new float[] { -halfW, halfW }) {
            for (var y : new float[] { -halfH, halfH }) {
                addBox(vertices, indices, x - halfT, x + halfT, y - halfT, y + halfT, -halfD, halfD, r, g, b);
            }
        }

        var v = new float[vertices.size()];
        for (var i = 0; i < v.length; i++) {
            v[i] = vertices.get(i);
        }
        var idx = new int[indices.size()];
        for (var i = 0; i < idx.length; i++) {
            idx[i] = indices.get(i);
        }

        return new SceneObject(new Mesh(v, idx));
    }

    // One bar, as an axis-aligned box between the given min/max corners.
    private static void addBox(List<Float> vertices, List<Integer> indices, float minX, float maxX, float minY,
            float maxY, float minZ, float maxZ, float r, float g, float b) {
        addFace(vertices, indices, r, g, b, 0f, 0f, 1f,
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ); // front (+z)
        addFace(vertices, indices, r, g, b, 0f, 0f, -1f,
                maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ); // back (-z)
        addFace(vertices, indices, r, g, b, -1f, 0f, 0f,
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ); // left (-x)
        addFace(vertices, indices, r, g, b, 1f, 0f, 0f,
                maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ); // right (+x)
        addFace(vertices, indices, r, g, b, 0f, 1f, 0f,
                minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ); // top (+y)
        addFace(vertices, indices, r, g, b, 0f, -1f, 0f,
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ); // bottom (-y)
    }

    // One quad, given as four corners in counter-clockwise winding (as seen from along the normal).
    private static void addFace(List<Float> vertices, List<Integer> indices, float r, float g, float b, float nx,
            float ny, float nz, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
            float z2, float x3, float y3, float z3) {
        var base = vertices.size() / 9;

        addVertex(vertices, x0, y0, z0, r, g, b, nx, ny, nz);
        addVertex(vertices, x1, y1, z1, r, g, b, nx, ny, nz);
        addVertex(vertices, x2, y2, z2, r, g, b, nx, ny, nz);
        addVertex(vertices, x3, y3, z3, r, g, b, nx, ny, nz);

        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base + 2);
        indices.add(base + 3);
        indices.add(base);
    }

    private static void addVertex(List<Float> vertices, float x, float y, float z, float r, float g, float b,
            float nx, float ny, float nz) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
    }
}
