package de.e_nexus.vrplatform.scenecore.text;

import java.awt.Color;

/**
 * Not {@code @Named}: framework beans are registered explicitly by the
 * consuming application's config (e.g. demo's {@code AppConfig}) instead of
 * relying on classpath scanning to find them, since in an OSGi deployment a
 * consumer's {@code @ComponentScan} can only enumerate classes physically
 * packaged inside its own bundle, not a separate framework bundle's.
 */
public class DefaultPanelTheme implements PanelTheme {

    // Fraction of the blurred backdrop that shows through the panel (see
    // text_fragment.glsl's mix(backdrop, panel.rgb, panel.a) — reflection is
    // exactly 1 - alpha there).
    private static final float FOCUSED_REFLECTION   = 0.10f;
    private static final float UNFOCUSED_REFLECTION = 0.50f;

    // Matches TextPanel.BASE_FONT_SIZE until something sets a real standard
    // (see VRScene, which promotes the sample text's third word to this).
    private static final int DEFAULT_FONT_SIZE = 90;

    private int fontSize = DEFAULT_FONT_SIZE;

    @Override
    public int getFontSize() {
        return fontSize;
    }

    @Override
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    @Override
    public Color backgroundColor(boolean dimmed) {
        float reflection = dimmed ? UNFOCUSED_REFLECTION : FOCUSED_REFLECTION;
        return new Color(0.08f, 0.08f, 0.12f, 1f - reflection);
    }

    @Override
    public Color borderColor() {
        return new Color(0.75f, 0.75f, 0.85f, 1f);
    }

    @Override
    public float borderWidth() {
        return 6f;
    }

    @Override
    public float cornerRadius() {
        return 48f;
    }
}
