package de.e_nexus.vrplatform.demo.scene.text;

import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

import java.awt.Color;
import java.awt.Font;

/**
 * Cycles through ten distinct font sizes (as fractions of the panel's fitted
 * base size), one per word, so a showcase panel visibly uses all ten instead
 * of just the base size everywhere.
 *
 * <p>Not {@code @Named}: registered explicitly by {@code AppConfig} (see its
 * comment for why -- OSGi bundles aren't reliably classpath-scannable).
 */
public class VariedFontSizeDecoration implements TextDecoration {

    private static final float[] SIZE_FACTORS = {
        0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f
    };

    @Override
    public Font font(int lineIndex, String line, int charIndex, int fontSize) {
        float factor = SIZE_FACTORS[sizeSlot(lineIndex, line, charIndex)];
        int size = Math.max(6, Math.round(fontSize * factor));
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    @Override
    public Color color(int lineIndex, String line, int charIndex) {
        return Color.WHITE;
    }

    // Offsetting by line index (not just the word's position within its own
    // line) spreads the ten slots across the whole panel instead of always
    // starting each short wrapped line back at slot 0.
    private static int sizeSlot(int lineIndex, String line, int charIndex) {
        int wordIndex = wordIndexOf(line, charIndex);
        return (lineIndex * 3 + wordIndex) % SIZE_FACTORS.length;
    }

    private static int wordIndexOf(String line, int charIndex) {
        int spaces = 0;
        for (int i = 0; i < charIndex; i++) {
            if (line.charAt(i) == ' ') {
                spaces++;
            }
        }
        return spaces;
    }
}
