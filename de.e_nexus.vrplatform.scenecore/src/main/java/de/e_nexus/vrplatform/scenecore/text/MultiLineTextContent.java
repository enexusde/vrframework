package de.e_nexus.vrplatform.scenecore.text;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, live-typed multi-line content: a text buffer with a single cursor
 * and an optional selection (for copy/cut/paste), where Enter creates real
 * new lines and long lines can soft-wrap. Not a CDI singleton — each text
 * field needs its own independent instance, constructed per field by the
 * application's scene. See {@link SingleLineTextContent} for the
 * single-line counterpart.
 *
 * <p>Distinguishes <em>logical</em> lines (created by {@link #newLine()},
 * i.e. a real {@code '\n'} in the buffer) from <em>display</em> lines (what
 * {@link #lines()} returns): a logical line longer than {@code maxLineLength}
 * is split into several display lines purely for rendering — no character is
 * ever inserted into the buffer for this, so backspacing across a wrap point
 * is exactly like backspacing across any other character, and only the first
 * display line of a logical line gets a line number.
 */
public class MultiLineTextContent implements EditableTextContent {

    private static final int MAX_LENGTH = 200;

    // Default chosen to comfortably fit the panel width at the base font
    // size, even with a line-number gutter, so a full line never needs
    // TextPanel's font-shrink fallback in normal use.
    private static final int DEFAULT_MAX_LINE_LENGTH = 20;

    private final StringBuilder buffer = new StringBuilder();
    private int cursor = 0;

    // Total buffer capacity in characters. Instance-level (not just the
    // MAX_LENGTH default) so content that accumulates a lot of text over
    // time, like a terminal's scrollback, can ask for more room.
    private final int maxLength;

    private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;

    // Once a logical line exceeds maxLineLength: true soft-wraps it across
    // several display lines (no line number on the continuations), false
    // rejects further characters on that line instead of shrinking the font.
    private boolean wrapEnabled = true;

    // -1 = no selection; otherwise the fixed end of the selection, with
    // `cursor` as the moving end (standard anchor/caret selection model).
    private int selectionAnchor = -1;

    // Notepad-style "sticky" column: remembered across consecutive Up/Down
    // presses so passing through a shorter line doesn't lose the original
    // column. Any other cursor movement resets it. -1 = not set.
    private int preferredColumn = -1;

    /** One rendered row: a slice of a logical line, never containing a real newline. */
    private record DisplaySegment(String text, int startOffset, int logicalLineNumber,
                                   boolean firstOfLogicalLine, boolean wrapContinues) {
    }

    public MultiLineTextContent() {
        this(MAX_LENGTH);
    }

    public MultiLineTextContent(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public List<String> lines() {
        List<String> result = new ArrayList<>();
        for (DisplaySegment seg : computeDisplayLines()) {
            result.add(seg.text());
        }
        return result;
    }

    @Override
    public int lineNumber(int displayLineIndex) {
        List<DisplaySegment> segments = computeDisplayLines();
        if (displayLineIndex < 0 || displayLineIndex >= segments.size()) {
            return -1;
        }
        DisplaySegment seg = segments.get(displayLineIndex);
        return seg.firstOfLogicalLine() ? seg.logicalLineNumber() : -1;
    }

    @Override
    public int lineStartOffset(int displayLineIndex) {
        List<DisplaySegment> segments = computeDisplayLines();
        if (displayLineIndex < 0 || displayLineIndex >= segments.size()) {
            return 0;
        }
        return segments.get(displayLineIndex).startOffset();
    }

    /** Splits the buffer's logical (newline-delimited) lines into wrap-aware display segments. */
    private List<DisplaySegment> computeDisplayLines() {
        List<DisplaySegment> result = new ArrayList<>();
        String[] logicalLines = buffer.toString().split("\n", -1);
        int offset = 0;
        for (int logicalIndex = 0; logicalIndex < logicalLines.length; logicalIndex++) {
            String logicalLine = logicalLines[logicalIndex];
            int logicalNumber = logicalIndex + 1;

            if (!wrapEnabled || logicalLine.length() <= maxLineLength) {
                result.add(new DisplaySegment(logicalLine, offset, logicalNumber, true, false));
            } else {
                int pos = 0;
                boolean first = true;
                while (pos < logicalLine.length()) {
                    int end = Math.min(pos + maxLineLength, logicalLine.length());
                    boolean continues = end < logicalLine.length();
                    result.add(new DisplaySegment(logicalLine.substring(pos, end), offset + pos,
                        logicalNumber, first, continues));
                    first = false;
                    pos = end;
                }
            }
            offset += logicalLine.length() + 1; // +1 for the real '\n' this split() consumed
        }
        return result;
    }

    /** Where the caret should be drawn: a display line/column, not a character itself. */
    @Override
    public CaretPosition caret() {
        List<DisplaySegment> segments = computeDisplayLines();
        for (int i = 0; i < segments.size(); i++) {
            DisplaySegment seg = segments.get(i);
            int segEnd = seg.startOffset() + seg.text().length();
            // A wrap boundary has no real character at it, so a cursor sitting
            // exactly there belongs to the *next* display line (where typing
            // would continue), not the end of this one.
            boolean canSitAtEnd = !seg.wrapContinues();
            if (cursor >= seg.startOffset() && (cursor < segEnd || (canSitAtEnd && cursor == segEnd))) {
                return new CaretPosition(i, cursor - seg.startOffset());
            }
        }
        if (!segments.isEmpty()) {
            DisplaySegment last = segments.get(segments.size() - 1);
            return new CaretPosition(segments.size() - 1, cursor - last.startOffset());
        }
        return new CaretPosition(0, 0);
    }

    /** The current selection as absolute buffer offsets, or null if nothing is selected. */
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
        if (!wrapEnabled && currentLineLength() >= maxLineLength) {
            return; // line is full and wrapping is off: reject the character
        }
        if (buffer.length() >= maxLength) {
            return;
        }
        buffer.insert(cursor, c);
        cursor++;
        preferredColumn = -1;
    }

    public boolean isWrapEnabled() {
        return wrapEnabled;
    }

    public void setWrapEnabled(boolean wrapEnabled) {
        this.wrapEnabled = wrapEnabled;
    }

    public int getMaxLineLength() {
        return maxLineLength;
    }

    public void setMaxLineLength(int maxLineLength) {
        this.maxLineLength = maxLineLength;
    }

    public void newLine() {
        deleteSelectionInternal();
        if (buffer.length() >= maxLength) {
            return;
        }
        buffer.insert(cursor, '\n');
        cursor++;
        preferredColumn = -1;
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
        preferredColumn = -1;
    }

    /** Forward delete (the Delete/Entf key): removes the selection, or the character after the cursor. */
    @Override
    public void delete() {
        if (hasSelection()) {
            deleteSelectionInternal();
            return;
        }
        if (cursor < buffer.length()) {
            buffer.deleteCharAt(cursor);
        }
        preferredColumn = -1;
    }

    @Override
    public void moveCursorLeft() {
        selectionAnchor = -1;
        if (cursor > 0) {
            cursor--;
        }
        preferredColumn = -1;
    }

    @Override
    public void moveCursorRight() {
        selectionAnchor = -1;
        if (cursor < buffer.length()) {
            cursor++;
        }
        preferredColumn = -1;
    }

    public void moveCursorUp() {
        selectionAnchor = -1;
        cursorUpInternal();
    }

    public void moveCursorDown() {
        selectionAnchor = -1;
        cursorDownInternal();
    }

    @Override
    public void moveHome() {
        selectionAnchor = -1;
        homeInternal();
    }

    @Override
    public void moveEnd() {
        selectionAnchor = -1;
        endInternal();
    }

    @Override
    public void extendSelectionLeft() {
        ensureAnchor();
        if (cursor > 0) {
            cursor--;
        }
        preferredColumn = -1;
    }

    @Override
    public void extendSelectionRight() {
        ensureAnchor();
        if (cursor < buffer.length()) {
            cursor++;
        }
        preferredColumn = -1;
    }

    public void extendSelectionUp() {
        ensureAnchor();
        cursorUpInternal();
    }

    public void extendSelectionDown() {
        ensureAnchor();
        cursorDownInternal();
    }

    @Override
    public void extendSelectionHome() {
        ensureAnchor();
        homeInternal();
    }

    @Override
    public void extendSelectionEnd() {
        ensureAnchor();
        endInternal();
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
        String clean = text.replace("\r\n", "\n").replace("\r", "\n");
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
        preferredColumn = -1;
    }

    @Override
    public void clear() {
        buffer.setLength(0);
        cursor = 0;
        selectionAnchor = -1;
        preferredColumn = -1;
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
        preferredColumn = -1;
    }

    private void cursorUpInternal() {
        int line = cursorLine();
        if (line == 0) {
            return;
        }
        if (preferredColumn < 0) {
            preferredColumn = cursorColumn();
        }
        String[] parts = buffer.toString().split("\n", -1);
        int targetLine = line - 1;
        cursor = lineStart(targetLine) + Math.min(preferredColumn, parts[targetLine].length());
    }

    private void cursorDownInternal() {
        String[] parts = buffer.toString().split("\n", -1);
        int line = cursorLine();
        if (line >= parts.length - 1) {
            return;
        }
        if (preferredColumn < 0) {
            preferredColumn = cursorColumn();
        }
        int targetLine = line + 1;
        cursor = lineStart(targetLine) + Math.min(preferredColumn, parts[targetLine].length());
    }

    private void homeInternal() {
        cursor = lineStart(cursorLine());
        preferredColumn = -1;
    }

    private void endInternal() {
        String[] parts = buffer.toString().split("\n", -1);
        int line = cursorLine();
        cursor = lineStart(line) + parts[line].length();
        preferredColumn = -1;
    }

    private int currentLineLength() {
        int start = lineStart(cursorLine());
        int end = buffer.indexOf("\n", start);
        if (end < 0) {
            end = buffer.length();
        }
        return end - start;
    }

    private int cursorLine() {
        int line = 0;
        for (int i = 0; i < cursor; i++) {
            if (buffer.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int cursorColumn() {
        int col = 0;
        for (int i = cursor - 1; i >= 0 && buffer.charAt(i) != '\n'; i--) {
            col++;
        }
        return col;
    }

    private int lineStart(int lineIndex) {
        int idx = 0;
        for (int line = 0; line < lineIndex; line++) {
            idx = buffer.indexOf("\n", idx) + 1;
        }
        return idx;
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
