package de.e_nexus.vrplatform.checkbox;

import de.e_nexus.vrplatform.scenecore.SingleLineTextPanel;
import de.e_nexus.vrplatform.scenecore.text.CaretPosition;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.SelectionRange;
import de.e_nexus.vrplatform.scenecore.text.SingleLineTextContent;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

import java.awt.Font;
import java.awt.FontMetrics;

/**
 * A tri-state checkbox built on {@link SingleLineTextPanel}: shows a state
 * symbol ("x" checked, "0" unchecked, "-" indeterminate) followed by a
 * label. Unlike a plain single-line field it is not free-text editable —
 * only {@link #toggle()} (bound to Enter while focused) changes what's
 * shown, and no text caret or selection is ever drawn.
 */
public class Checkbox extends SingleLineTextPanel {

    private final String label;
    private CheckboxState state;

    public Checkbox(float x, float y, float z, PanelTheme theme, SingleLineTextContent content,
                     TextDecoration decoration, String label, CheckboxState initialState) {
        super(x, y, z, theme, content, decoration);
        this.label = label;
        this.state = initialState;
        updateDisplay();
    }

    public CheckboxState getState() {
        return state;
    }

    public void setState(CheckboxState state) {
        if (this.state != state) {
            this.state = state;
            updateDisplay();
        }
    }

    /** Cycles unchecked -> checked -> indeterminate -> unchecked. */
    public void toggle() {
        setState(state.next());
    }

    @Override
    public void handleNewLine() {
        toggle();
    }

    @Override
    public void handleChar(char c) {
        // Not free-text editable.
    }

    @Override
    public void handleBackspace() {
    }

    @Override
    public void handleDelete() {
    }

    @Override
    public void handlePaste() {
    }

    @Override
    public void handleCut() {
    }

    @Override
    public void handleCursorLeft() {
    }

    @Override
    public void handleCursorRight() {
    }

    @Override
    public void handleHome() {
    }

    @Override
    public void handleEnd() {
    }

    @Override
    public void handleExtendLeft() {
    }

    @Override
    public void handleExtendRight() {
    }

    @Override
    public void handleExtendHome() {
    }

    @Override
    public void handleExtendEnd() {
    }

    @Override
    protected CaretPosition caret() {
        return null; // no text cursor on a checkbox
    }

    @Override
    protected SelectionRange selection() {
        return null; // nothing to select
    }

    // Sized to its own text instead of the 1 m × 1 m default every other
    // panel uses, so the checkbox reads as a compact widget next to a full
    // text field rather than an oversized panel with mostly empty space.
    // Measured at the same TEX_SIZE-px-per-panel-meter density as the
    // default-sized panels, so the text fills this smaller quad just as
    // snugly as it would fill a full one. Uses the theme's actual font size
    // (not the fixed BASE_FONT_SIZE constant) since that's the size the text
    // really renders at -- otherwise the panel is sized for text bigger or
    // smaller than what's actually drawn.
    @Override
    protected float panelWidth() {
        FontMetrics fm = scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, theme().getFontSize()));
        return (fm.stringWidth(displayText()) + 2f * PAD) / (float) TEX_SIZE;
    }

    @Override
    protected float panelHeight() {
        FontMetrics fm = scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, theme().getFontSize()));
        return (fm.getAscent() + fm.getDescent() + 2f * PAD) / (float) TEX_SIZE;
    }

    private String displayText() {
        return state.symbol() + " " + label;
    }

    private void updateDisplay() {
        content.clear();
        for (char c : displayText().toCharArray()) {
            content.append(c);
        }
        refresh();
    }
}
