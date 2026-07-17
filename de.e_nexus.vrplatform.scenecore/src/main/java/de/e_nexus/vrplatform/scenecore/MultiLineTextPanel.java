package de.e_nexus.vrplatform.scenecore;

import de.e_nexus.vrplatform.scenecore.text.MultiLineTextContent;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

/**
 * A {@link TypeableTextPanel} that supports multiple lines: Enter creates a
 * new logical line, Up/Down navigate between lines, and a line longer than
 * the panel can soft-wrap. See {@link SingleLineTextPanel} for a field that
 * never breaks onto a second line.
 */
public class MultiLineTextPanel extends TypeableTextPanel {

    protected final MultiLineTextContent multiLineContent;

    public MultiLineTextPanel(float x, float y, float z,
                               PanelTheme theme, MultiLineTextContent content, TextDecoration decoration) {
        this(x, y, z, theme, content, decoration, theme.getFontSize());
    }

    public MultiLineTextPanel(float x, float y, float z, PanelTheme theme, MultiLineTextContent content,
                               TextDecoration decoration, int baseFontSize) {
        super(x, y, z, theme, content, decoration, baseFontSize);
        this.multiLineContent = content;
        // Derived from the actual rendered width instead of a guessed
        // constant, so the two stay connected (see TextPanel.estimateMaxLineLength).
        content.setMaxLineLength(estimateMaxLineLength());
    }

    @Override
    public void handleNewLine() {
        multiLineContent.newLine();
        refresh();
    }

    @Override
    public void handleCursorUp() {
        multiLineContent.moveCursorUp();
        refresh();
    }

    @Override
    public void handleCursorDown() {
        multiLineContent.moveCursorDown();
        refresh();
    }

    @Override
    public void handleExtendUp() {
        multiLineContent.extendSelectionUp();
        refresh();
    }

    @Override
    public void handleExtendDown() {
        multiLineContent.extendSelectionDown();
        refresh();
    }
}
