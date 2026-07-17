package de.e_nexus.vrplatform.terminal;

import de.e_nexus.vrplatform.scenecore.TypeableTextPanel;
import de.e_nexus.vrplatform.scenecore.text.CaretPosition;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

/**
 * The single grid a {@link Terminal} renders onto: unlike an ordinary typed
 * text field, nothing here is edited locally -- every key callback writes
 * raw bytes straight to the pty (see {@link Terminal#sendInput}), and the
 * grid it displays comes entirely from what the remote program echoes back
 * over the same pty (see {@link VTScreen}), exactly like a real terminal.
 */
public class TerminalPanel extends TypeableTextPanel {

    private static final String ESC = "";
    private static final String BACKSPACE_BYTE = "";
    private static final String INTERRUPT_BYTE = "";

    private final Terminal owner;
    private final VTScreen screen;
    private final float width;
    private final float height;

    TerminalPanel(float x, float y, float z, PanelTheme theme, TerminalContentAdapter content,
                  TextDecoration decoration, Terminal owner, VTScreen screen,
                  float width, float height, int baseFontSize) {
        super(x, y, z, theme, content, decoration, baseFontSize);
        this.owner = owner;
        this.screen = screen;
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
    protected CaretPosition caret() {
        return screen.isCursorVisible() ? super.caret() : null;
    }

    @Override
    public void handleChar(char c) {
        owner.sendInput(String.valueOf(c));
    }

    @Override
    public void handleBackspace() {
        owner.sendInput(BACKSPACE_BYTE);
    }

    @Override
    public void handleDelete() {
        owner.sendInput(ESC + "[3~");
    }

    @Override
    public void handleNewLine() {
        owner.sendInput("\r");
    }

    @Override
    public void handleCursorLeft() {
        owner.sendInput(ESC + "[D");
    }

    @Override
    public void handleCursorRight() {
        owner.sendInput(ESC + "[C");
    }

    @Override
    public void handleCursorUp() {
        owner.sendInput(ESC + "[A");
    }

    @Override
    public void handleCursorDown() {
        owner.sendInput(ESC + "[B");
    }

    @Override
    public void handleHome() {
        owner.sendInput(ESC + "[H");
    }

    @Override
    public void handleEnd() {
        owner.sendInput(ESC + "[F");
    }

    /** Ctrl+C means "interrupt the running program" on a real terminal, not clipboard copy. */
    @Override
    public void handleCopy() {
        owner.sendInput(INTERRUPT_BYTE);
    }

    @Override
    public void handleCut() {
        // No terminal meaning for Ctrl+X; unlike Ctrl+C, leave it a no-op
        // rather than repurposing it.
    }

    @Override
    public void handlePaste() {
        owner.sendClipboardText();
    }
}
