package de.e_nexus.vrplatform.scenecore.text;

import java.awt.Color;
import java.awt.Font;

/**
 * Plain monospace styling for the typing panel; the caret is drawn separately (see TextPanel).
 *
 * <p>Not {@code @Named}: framework beans are registered explicitly by the
 * consuming application's config (e.g. demo's {@code AppConfig}) instead of
 * relying on classpath scanning to find them, since in an OSGi deployment a
 * consumer's {@code @ComponentScan} can only enumerate classes physically
 * packaged inside its own bundle, not a separate framework bundle's.
 */
public class TypedTextDecoration implements TextDecoration {

    @Override
    public Font font(int lineIndex, String line, int charIndex, int fontSize) {
        return new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
    }

    @Override
    public Color color(int lineIndex, String line, int charIndex) {
        return Color.WHITE;
    }
}
