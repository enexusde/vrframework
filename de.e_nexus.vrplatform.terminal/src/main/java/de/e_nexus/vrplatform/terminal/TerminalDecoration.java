package de.e_nexus.vrplatform.terminal;

import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

import java.awt.Color;
import java.awt.Font;

/**
 * Per-character styling sourced from a {@link VTScreen}'s own grid instead of
 * a fixed theme -- a terminal's colors come from the remote program's SGR
 * escape codes (see {@link VTScreen#applySgr}), not a static decoration.
 */
final class TerminalDecoration implements TextDecoration {

    private final VTScreen screen;

    TerminalDecoration(VTScreen screen) {
        this.screen = screen;
    }

    @Override
    public Font font(int lineIndex, String line, int charIndex, int fontSize) {
        boolean bold = screen.isBold(lineIndex, charIndex);
        return new Font(Font.MONOSPACED, bold ? Font.BOLD : Font.PLAIN, fontSize);
    }

    @Override
    public Color color(int lineIndex, String line, int charIndex) {
        Color c = screen.foreground(lineIndex, charIndex);
        return c != null ? c : VTScreen.DEFAULT_FG;
    }
}
