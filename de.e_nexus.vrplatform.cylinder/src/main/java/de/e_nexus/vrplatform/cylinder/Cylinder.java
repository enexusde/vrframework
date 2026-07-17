package de.e_nexus.vrplatform.cylinder;

import de.e_nexus.vrplatform.scenecore.Mesh;
import de.e_nexus.vrplatform.scenecore.SceneObject;
import de.e_nexus.vrplatform.scenecore.SingleLineTextPanel;
import de.e_nexus.vrplatform.scenecore.TextPanel;
import de.e_nexus.vrplatform.scenecore.text.CaretPosition;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.SelectionRange;
import de.e_nexus.vrplatform.scenecore.text.SingleLineTextContent;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;
import org.joml.Vector3f;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A rotatable cylindrical dial: a 3D cylinder mesh with a text label in
 * front of it showing the current numeric value. Turned with Alt-Gr (see
 * {@link #rotate(float)}), which both spins the mesh and moves the value
 * proportionally within [{@link #minValue()}, {@link #maxValue()}].
 *
 * <p>{@code T} is closed over exactly the boxed numeric types
 * {@link Integer}, {@link Short}, {@link Long}, {@link Float} and
 * {@link Double} (checked at construction, since Java generics can't express
 * that bound directly) — floating-point values show two decimal places,
 * the others show as plain integers.
 */
public class Cylinder<T extends Number & Comparable<T>> {

    private static final Set<Class<?>> SUPPORTED_TYPES =
            Set.of(Integer.class, Short.class, Long.class, Float.class, Double.class);

    // The label's physical world height, independent of the text shown (so
    // it can't drift as the value changes length) -- proportioned to the dial.
    private static final float LABEL_HEIGHT_FACTOR = 0.5f; // x radius
    // How much of the texture's fixed height budget the glyphs themselves
    // (ascent+descent, plus PAD top and bottom) should fill -- large, so the
    // digits are a big fraction of the label rather than a small mark in a
    // mostly-blank box.
    private static final double LABEL_HEIGHT_FILL = 0.85;
    // Margin on either side of the text, as a fraction of the label height.
    private static final float LABEL_MARGIN_FACTOR = 0.15f;

    public enum Direction {
        /** Barrel axis runs top-to-bottom (Y); turned like a bottle standing on end. */
        VERTICAL,
        /** Barrel axis runs side-to-side (X); turned like a rolling pin lying flat. */
        HORIZONTAL
    }

    private final Direction direction;
    private final T minValue;
    private final T maxValue;
    private final SceneObject mesh;
    private final Label label;
    private final SingleLineTextContent labelContent;
    private T value;

    public Cylinder(float x, float y, float z, Direction direction, float radius, float height, int segments,
            float r, float g, float b, T value, T minValue, T maxValue, PanelTheme theme,
            TextDecoration decoration) {
        requireSupportedType(value);
        requireSupportedType(minValue);
        requireSupportedType(maxValue);
        this.direction = direction;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.value = clamp(value);

        mesh = buildMesh(direction, radius, height, segments, r, g, b).position(x, y, z);

        // labelHeight is fixed (radius-proportional); labelWidth is derived
        // from the actual text at a font size chosen to fill LABEL_HEIGHT_FILL
        // of the texture vertically -- *and* to fit TextPanel.renderPixels()'s
        // own width budget (TEX_SIZE - 2*PAD), since that method silently
        // shrinks the font further if it doesn't (see fontSizeForLabel).
        // Sizing labelWidth from a font that gets shrunk out from under it at
        // render time (this class's third attempt at this) leaves exactly
        // the gap on the right that a too-large starting font shrinks away from.
        //
        // The subtlety that broke the two attempts before that: TextPanel
        // squeezes glyphs horizontally by glyphScaleX = panelHeight/panelWidth
        // to counteract a non-square panel stretching the texture -- so the
        // world-space width the text actually renders at is
        //   stringWidth(fontSize) * glyphScaleX/TEX_SIZE * labelWidth
        //   = stringWidth(fontSize) * labelHeight/TEX_SIZE
        // labelWidth cancels out completely. Solving labelWidth from a
        // formula that (like Checkbox's) doesn't account for that
        // cancellation always leaves the panel far too big for whatever text
        // ends up in its top-left corner.
        String initialText = format(this.value);
        int labelFontSize = fontSizeForLabel(initialText);
        FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, labelFontSize));
        float labelHeight = radius * LABEL_HEIGHT_FACTOR;
        float margin = labelHeight * LABEL_MARGIN_FACTOR;
        float labelWidth = fm.stringWidth(initialText) * labelHeight / (float) TextPanel.TEX_SIZE + 2f * margin;

        labelContent = new SingleLineTextContent();
        // Floats just in front of the barrel (toward the camera, +Z) so it
        // stays flat and readable regardless of how far the mesh has spun.
        label = new Label(x, y, z + radius + 0.02f, theme, labelContent, decoration, labelWidth, labelHeight,
                labelFontSize);

        updateLabel();
    }

    public void initialize() {
        label.initialize();
    }

    public void destroy() {
        mesh.destroy();
        label.destroy();
    }

    public SceneObject mesh() {
        return mesh;
    }

    public TextPanel label() {
        return label;
    }

    public Vector3f position() {
        return mesh.position();
    }

    /** Highlights the label (same cyan border a focused text field draws) while this dial is being turned. */
    public void setFocused(boolean focused) {
        label.setFocused(focused);
    }

    public Direction direction() {
        return direction;
    }

    public T value() {
        return value;
    }

    public T minValue() {
        return minValue;
    }

    public T maxValue() {
        return maxValue;
    }

    public void setValue(T newValue) {
        value = clamp(newValue);
        updateLabel();
    }

    /**
     * Turns the dial by this many radians around its own barrel axis (spins
     * the mesh, no clamping), and moves the value proportionally within
     * [{@link #minValue()}, {@link #maxValue()}] (clamped) -- one full turn
     * (2*pi) sweeps the entire range. Intended to be driven by an Alt-Gr
     * drag gesture while this cylinder is aimed at, the same way a checkbox
     * is armed and toggled (see VRScene's checkbox-aim handling); wiring
     * that gesture up is not part of this component.
     */
    public void rotate(float deltaRadians) {
        switch (direction) {
            case VERTICAL -> mesh.rotateY(deltaRadians);
            case HORIZONTAL -> mesh.rotateX(deltaRadians);
        }
        double range = maxValue.doubleValue() - minValue.doubleValue();
        double deltaValue = (deltaRadians / (2 * Math.PI)) * range;
        setValue(toT(value.doubleValue() + deltaValue));
    }

    private T clamp(T candidate) {
        if (candidate.compareTo(minValue) < 0) {
            return minValue;
        }
        if (candidate.compareTo(maxValue) > 0) {
            return maxValue;
        }
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private T toT(double raw) {
        Class<?> type = minValue.getClass();
        if (type == Integer.class) {
            return (T) Integer.valueOf((int) Math.round(raw));
        }
        if (type == Short.class) {
            return (T) Short.valueOf((short) Math.round(raw));
        }
        if (type == Long.class) {
            return (T) Long.valueOf(Math.round(raw));
        }
        if (type == Float.class) {
            return (T) Float.valueOf((float) raw);
        }
        return (T) Double.valueOf(raw);
    }

    private void updateLabel() {
        labelContent.clear();
        for (char c : format(value).toCharArray()) {
            labelContent.append(c);
        }
        label.refresh();
    }

    private static String format(Number n) {
        if (n instanceof Float || n instanceof Double) {
            return String.format("%.2f", n.doubleValue());
        }
        return String.valueOf(n.longValue());
    }

    // Largest font size that both (a) leaves ascent+descent+2*PAD within
    // LABEL_HEIGHT_FILL of the texture's height and (b) fits `text` within
    // TextPanel.renderPixels()'s own width budget (TEX_SIZE - 2*PAD) --
    // (b) matters because that method silently shrinks the font further,
    // on its own, whenever a size satisfying only (a) is too wide; using a
    // font that satisfies both from the start means the two never disagree
    // about how wide the text actually ends up.
    private static int fontSizeForLabel(String text) {
        int textAreaWidth = TextPanel.TEX_SIZE - 2 * TextPanel.PAD;
        int fontSize = 4000;
        while (fontSize > 8) {
            FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
            boolean fitsHeight = fm.getAscent() + fm.getDescent() + 2 * TextPanel.PAD <= LABEL_HEIGHT_FILL * TextPanel.TEX_SIZE;
            boolean fitsWidth = fm.stringWidth(text) <= textAreaWidth;
            if (fitsHeight && fitsWidth) {
                break;
            }
            fontSize -= 2;
        }
        return fontSize;
    }

    private static void requireSupportedType(Number value) {
        if (!SUPPORTED_TYPES.contains(value.getClass())) {
            throw new IllegalArgumentException(
                    "unsupported numeric type: " + value.getClass() + " (must be Integer, Short, Long, Float or Double)");
        }
    }

    // ---- mesh generation ----

    private static SceneObject buildMesh(Direction direction, float radius, float height, int segments, float r,
            float g, float b) {
        var halfH = height / 2f;
        var vertices = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();

        for (var i = 0; i < segments; i++) {
            var a0 = (float) (2 * Math.PI * i / segments);
            var a1 = (float) (2 * Math.PI * (i + 1) / segments);
            var cx0 = (float) Math.cos(a0) * radius;
            var cz0 = (float) Math.sin(a0) * radius;
            var cx1 = (float) Math.cos(a1) * radius;
            var cz1 = (float) Math.sin(a1) * radius;

            var p0 = point(direction, cx0, -halfH, cz0);
            var p1 = point(direction, cx1, -halfH, cz1);
            var p2 = point(direction, cx1, halfH, cz1);
            var p3 = point(direction, cx0, halfH, cz0);
            var n0 = point(direction, (float) Math.cos(a0), 0f, (float) Math.sin(a0));
            var n1 = point(direction, (float) Math.cos(a1), 0f, (float) Math.sin(a1));

            var base = vertices.size() / 9;
            addVertex(vertices, p0, r, g, b, n0);
            addVertex(vertices, p1, r, g, b, n1);
            addVertex(vertices, p2, r, g, b, n1);
            addVertex(vertices, p3, r, g, b, n0);
            // Outward-facing winding is (p0,p2,p1) and (p0,p3,p2) here, not the
            // naive (p0,p1,p2)/(p2,p3,p0) quad order -- GL_CULL_FACE is on, and
            // that naive order faces inward for a CCW-front-face convention.
            indices.add(base);
            indices.add(base + 2);
            indices.add(base + 1);
            indices.add(base);
            indices.add(base + 3);
            indices.add(base + 2);
        }

        addCap(vertices, indices, direction, radius, -halfH, segments, r, g, b, true);
        addCap(vertices, indices, direction, radius, halfH, segments, r, g, b, false);

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

    // One flat end cap, fan-triangulated from its center. `negative` selects
    // the low end of the axis (outward normal points toward -axis instead of +axis).
    private static void addCap(List<Float> vertices, List<Integer> indices, Direction direction, float radius,
            float axisCoord, int segments, float r, float g, float b, boolean negative) {
        var normal = point(direction, 0f, negative ? -1f : 1f, 0f);
        var center = point(direction, 0f, axisCoord, 0f);
        var centerIndex = vertices.size() / 9;
        addVertex(vertices, center, r, g, b, normal);

        var rimStart = centerIndex + 1;
        for (var i = 0; i <= segments; i++) {
            var a = (float) (2 * Math.PI * i / segments);
            var p = point(direction, (float) Math.cos(a) * radius, axisCoord, (float) Math.sin(a) * radius);
            addVertex(vertices, p, r, g, b, normal);
        }
        for (var i = 0; i < segments; i++) {
            indices.add(centerIndex);
            if (negative) {
                indices.add(rimStart + i);
                indices.add(rimStart + i + 1);
            } else {
                indices.add(rimStart + i + 1);
                indices.add(rimStart + i);
            }
        }
    }

    private static void addVertex(List<Float> vertices, float[] position, float r, float g, float b,
            float[] normal) {
        vertices.add(position[0]);
        vertices.add(position[1]);
        vertices.add(position[2]);
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(normal[0]);
        vertices.add(normal[1]);
        vertices.add(normal[2]);
    }

    // Maps (radial1, axisCoord, radial2) onto world (x, y, z) depending on
    // which world axis is the barrel axis. The two branches must be an even
    // permutation of each other (a rotation, not a mirror) or the outward
    // winding derived for VERTICAL comes out backwards for HORIZONTAL.
    private static float[] point(Direction direction, float radial1, float axisCoord, float radial2) {
        return switch (direction) {
            case VERTICAL -> new float[] { radial1, axisCoord, radial2 };
            case HORIZONTAL -> new float[] { axisCoord, radial2, radial1 };
        };
    }

    // Read-only label: shows the formatted value, never takes keyboard focus
    // or draws a caret/selection (nothing ever types into it directly).
    private static final class Label extends SingleLineTextPanel {

        private final float width;
        private final float height;

        Label(float x, float y, float z, PanelTheme theme, SingleLineTextContent content, TextDecoration decoration,
                float width, float height, int baseFontSize) {
            super(x, y, z, theme, content, decoration, baseFontSize);
            this.width = width;
            this.height = height;
        }

        @Override
        protected float panelWidth() {
            return width;
        }

        @Override
        protected float panelHeight() {
            return height;
        }

        @Override
        protected boolean focusable() {
            return false;
        }

        @Override
        protected CaretPosition caret() {
            return null;
        }

        @Override
        protected SelectionRange selection() {
            return null;
        }
    }
}
