package de.e_nexus.vrplatform.scenecore.text;

import java.awt.Color;
import java.awt.Font;

/**
 * Per-character font styling and color for panel text. The full line is
 * passed alongside the character index so implementations can still key
 * decoration off word or line context (not just the single character).
 */
public interface TextDecoration {
    Font font(int lineIndex, String line, int charIndex, int fontSize);
    Color color(int lineIndex, String line, int charIndex);
}
