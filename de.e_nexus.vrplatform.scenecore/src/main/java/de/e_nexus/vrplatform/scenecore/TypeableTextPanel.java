package de.e_nexus.vrplatform.scenecore;

import de.e_nexus.vrplatform.scenecore.text.CaretPosition;
import de.e_nexus.vrplatform.scenecore.text.EditableTextContent;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.SelectionRange;
import de.e_nexus.vrplatform.scenecore.text.TextContent;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

/**
 * A text panel whose content is live-typed via the keyboard instead of
 * static. Holds the cursor/selection/clipboard behavior shared by both line
 * modes; not a standalone CDI component (subclasses register with @Named).
 *
 * <p>Line-specific actions (Enter, Up/Down navigation) default to no-ops
 * here, matching a single line's semantics — see {@link SingleLineTextPanel}.
 * {@link MultiLineTextPanel} overrides them with real multi-line behavior.
 */
public abstract class TypeableTextPanel extends TextPanel {

    private final PanelTheme theme;
    protected final EditableTextContent content;
    private final TextDecoration decoration;
    private boolean focused = false;

    protected TypeableTextPanel(float x, float y, float z,
                                 PanelTheme theme, EditableTextContent content, TextDecoration decoration) {
        this(x, y, z, theme, content, decoration, theme.getFontSize());
    }

    protected TypeableTextPanel(float x, float y, float z, PanelTheme theme, EditableTextContent content,
                                 TextDecoration decoration, int baseFontSize) {
        super(x, y, z, baseFontSize);
        this.theme = theme;
        this.content = content;
        this.decoration = decoration;
    }

    @Override
    protected PanelTheme theme() {
        return theme;
    }

    @Override
    protected TextContent content() {
        return content;
    }

    @Override
    protected TextDecoration decoration() {
        return decoration;
    }

    @Override
    protected CaretPosition caret() {
        return content.caret();
    }

    @Override
    protected SelectionRange selection() {
        return content.selectionRange();
    }

    @Override
    protected boolean focused() {
        return focused;
    }

    @Override
    protected boolean focusable() {
        return true;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            refresh();
        }
    }

    public void handleChar(char c) {
        content.append(c);
        refresh();
    }

    public void handleBackspace() {
        content.backspace();
        refresh();
    }

    public void handleDelete() {
        content.delete();
        refresh();
    }

    public void handleCursorLeft() {
        content.moveCursorLeft();
        refresh();
    }

    public void handleCursorRight() {
        content.moveCursorRight();
        refresh();
    }

    public void handleHome() {
        content.moveHome();
        refresh();
    }

    public void handleEnd() {
        content.moveEnd();
        refresh();
    }

    public void handleExtendLeft() {
        content.extendSelectionLeft();
        refresh();
    }

    public void handleExtendRight() {
        content.extendSelectionRight();
        refresh();
    }

    public void handleExtendHome() {
        content.extendSelectionHome();
        refresh();
    }

    public void handleExtendEnd() {
        content.extendSelectionEnd();
        refresh();
    }

    public void handleCopy() {
        content.copy();
    }

    public void handleCut() {
        content.cut();
        refresh();
    }

    public void handlePaste() {
        content.paste();
        refresh();
    }

    /** No-op for a single line; {@link MultiLineTextPanel} creates a new logical line. */
    public void handleNewLine() {
    }

    /** No-op for a single line; {@link MultiLineTextPanel} navigates to the line above. */
    public void handleCursorUp() {
    }

    /** No-op for a single line; {@link MultiLineTextPanel} navigates to the line below. */
    public void handleCursorDown() {
    }

    /** No-op for a single line; {@link MultiLineTextPanel} extends the selection upward. */
    public void handleExtendUp() {
    }

    /** No-op for a single line; {@link MultiLineTextPanel} extends the selection downward. */
    public void handleExtendDown() {
    }
}
