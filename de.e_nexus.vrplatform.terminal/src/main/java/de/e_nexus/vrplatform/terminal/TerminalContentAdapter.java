package de.e_nexus.vrplatform.terminal;

import de.e_nexus.vrplatform.scenecore.text.CaretPosition;
import de.e_nexus.vrplatform.scenecore.text.EditableTextContent;
import de.e_nexus.vrplatform.scenecore.text.SelectionRange;

import java.util.List;

/**
 * Adapts a {@link VTScreen} to the {@link EditableTextContent} contract
 * {@link de.e_nexus.vrplatform.scenecore.TypeableTextPanel} requires. Only
 * {@link #lines()} and {@link #caret()} do anything real -- a terminal's
 * content comes entirely from the remote program's own output, not from
 * local editing, so every mutating method here is unreachable: {@link
 * TerminalPanel} overrides every {@code handleXxx} callback to write raw
 * bytes to the pty instead of calling into this content.
 */
final class TerminalContentAdapter implements EditableTextContent {

    private final VTScreen screen;

    TerminalContentAdapter(VTScreen screen) {
        this.screen = screen;
    }

    @Override
    public List<String> lines() {
        return screen.lines();
    }

    @Override
    public CaretPosition caret() {
        return new CaretPosition(screen.cursorRow(), screen.cursorCol());
    }

    @Override
    public SelectionRange selectionRange() {
        return null;
    }

    @Override
    public void append(char c) {
    }

    @Override
    public void backspace() {
    }

    @Override
    public void delete() {
    }

    @Override
    public void moveCursorLeft() {
    }

    @Override
    public void moveCursorRight() {
    }

    @Override
    public void moveHome() {
    }

    @Override
    public void moveEnd() {
    }

    @Override
    public void extendSelectionLeft() {
    }

    @Override
    public void extendSelectionRight() {
    }

    @Override
    public void extendSelectionHome() {
    }

    @Override
    public void extendSelectionEnd() {
    }

    @Override
    public void copy() {
    }

    @Override
    public void cut() {
    }

    @Override
    public void paste() {
    }

    @Override
    public void clear() {
    }
}
