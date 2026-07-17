package de.e_nexus.vrplatform.scenecore.text;

/**
 * Shared editing contract for live-typed text content: cursor movement,
 * selection and clipboard operations common to both {@link SingleLineTextContent}
 * and {@link MultiLineTextContent}. Line-specific concerns (Enter, Up/Down,
 * wrapping) are not part of this contract — they only exist on the
 * multi-line implementation.
 */
public interface EditableTextContent extends TextContent {
    CaretPosition caret();

    SelectionRange selectionRange();

    void append(char c);

    void backspace();

    void delete();

    void moveCursorLeft();

    void moveCursorRight();

    void moveHome();

    void moveEnd();

    void extendSelectionLeft();

    void extendSelectionRight();

    void extendSelectionHome();

    void extendSelectionEnd();

    void copy();

    void cut();

    void paste();

    void clear();
}
