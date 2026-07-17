package de.e_nexus.vrplatform.scenecore.text;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * Mutable, live-typed single-line content: no newlines, no wrapping — the
 * buffer is always exactly one line. A value too wide for the panel simply
 * shrinks via {@code TextPanel}'s ordinary font-fit fallback instead of ever
 * splitting onto a second line. Not a CDI singleton — each field needs its
 * own instance. See {@link MultiLineTextContent} for the multi-line counterpart.
 */
public class SingleLineTextContent implements EditableTextContent {

    private static final int MAX_LENGTH = 60;

    private final StringBuilder buffer = new StringBuilder();
    private int cursor = 0;
    private final int maxLength;

    public SingleLineTextContent() {
        this(MAX_LENGTH);
    }

    public SingleLineTextContent(int maxLength) {
        this.maxLength = maxLength;
    }

    // -1 = no selection; otherwise the fixed end of the selection, with
    // `cursor` as the moving end (standard anchor/caret selection model).
    private int selectionAnchor = -1;

    @Override
    public List<String> lines() {
        return List.of(buffer.toString());
    }

    @Override
    public CaretPosition caret() {
        return new CaretPosition(0, cursor);
    }

    @Override
    public SelectionRange selectionRange() {
        return hasSelection() ? new SelectionRange(selectionStart(), selectionEnd()) : null;
    }

    @Override
    public void append(char c) {
        if (c < 0x20) {
            return;
        }
        deleteSelectionInternal();
        if (buffer.length() >= maxLength) {
            return;
        }
        buffer.insert(cursor, c);
        cursor++;
    }

    @Override
    public void backspace() {
        if (hasSelection()) {
            deleteSelectionInternal();
            return;
        }
        if (cursor > 0) {
            buffer.deleteCharAt(cursor - 1);
            cursor--;
        }
    }

    @Override
    public void delete() {
        if (hasSelection()) {
            deleteSelectionInternal();
            return;
        }
        if (cursor < buffer.length()) {
            buffer.deleteCharAt(cursor);
        }
    }

    @Override
    public void moveCursorLeft() {
        selectionAnchor = -1;
        if (cursor > 0) {
            cursor--;
        }
    }

    @Override
    public void moveCursorRight() {
        selectionAnchor = -1;
        if (cursor < buffer.length()) {
            cursor++;
        }
    }

    @Override
    public void moveHome() {
        selectionAnchor = -1;
        cursor = 0;
    }

    @Override
    public void moveEnd() {
        selectionAnchor = -1;
        cursor = buffer.length();
    }

    @Override
    public void extendSelectionLeft() {
        ensureAnchor();
        if (cursor > 0) {
            cursor--;
        }
    }

    @Override
    public void extendSelectionRight() {
        ensureAnchor();
        if (cursor < buffer.length()) {
            cursor++;
        }
    }

    @Override
    public void extendSelectionHome() {
        ensureAnchor();
        cursor = 0;
    }

    @Override
    public void extendSelectionEnd() {
        ensureAnchor();
        cursor = buffer.length();
    }

    @Override
    public void copy() {
        String text = selectedText();
        if (!text.isEmpty()) {
            setClipboardText(text);
        }
    }

    @Override
    public void cut() {
        String text = selectedText();
        if (!text.isEmpty()) {
            setClipboardText(text);
            deleteSelectionInternal();
        }
    }

    @Override
    public void paste() {
        String text = getClipboardText();
        if (text == null || text.isEmpty()) {
            return;
        }
        // No line concept here: collapse any newlines in the pasted text
        // instead of splitting the value across several lines.
        String clean = text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        deleteSelectionInternal();
        int room = maxLength - buffer.length();
        if (room <= 0) {
            return;
        }
        if (clean.length() > room) {
            clean = clean.substring(0, room);
        }
        buffer.insert(cursor, clean);
        cursor += clean.length();
    }

    @Override
    public void clear() {
        buffer.setLength(0);
        cursor = 0;
        selectionAnchor = -1;
    }

    private void ensureAnchor() {
        if (selectionAnchor < 0) {
            selectionAnchor = cursor;
        }
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursor;
    }

    private int selectionStart() {
        return Math.min(selectionAnchor, cursor);
    }

    private int selectionEnd() {
        return Math.max(selectionAnchor, cursor);
    }

    private String selectedText() {
        return hasSelection() ? buffer.substring(selectionStart(), selectionEnd()) : "";
    }

    private void deleteSelectionInternal() {
        if (!hasSelection()) {
            return;
        }
        int start = selectionStart();
        buffer.delete(start, selectionEnd());
        cursor = start;
        selectionAnchor = -1;
    }

    private static void setClipboardText(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    private static String getClipboardText() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }
}
