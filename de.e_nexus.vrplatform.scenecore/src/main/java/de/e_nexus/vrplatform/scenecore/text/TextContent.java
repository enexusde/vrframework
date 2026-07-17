package de.e_nexus.vrplatform.scenecore.text;

import java.util.List;

/** Plain text lines a panel displays, with no styling information. */
public interface TextContent {
    List<String> lines();

    /**
     * 1-based line number to show for a display line, or a non-positive
     * value to show none (e.g. a soft-wrapped continuation of a longer
     * logical line). Defaults to plain sequential numbering, matching
     * content with no wrapping concept.
     */
    default int lineNumber(int displayLineIndex) {
        return displayLineIndex + 1;
    }

    /**
     * Absolute character offset (within whatever coordinate space the
     * content's caret/selection use) where a display line begins. Used to
     * align selection highlighting with wrapped lines. Defaults to a plain
     * running sum assuming one real line break between each returned line.
     */
    default int lineStartOffset(int displayLineIndex) {
        List<String> lines = lines();
        int offset = 0;
        for (int i = 0; i < displayLineIndex && i < lines.size(); i++) {
            offset += lines.get(i).length() + 1;
        }
        return offset;
    }
}
