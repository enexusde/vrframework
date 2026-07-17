package de.e_nexus.vrplatform.demo.scene.text;

import de.e_nexus.vrplatform.scenecore.text.TextContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Three short Lorem Ipsum paragraphs, word-wrapped to {@value #WRAP_COLUMNS}
 * characters per line so they fit {@link de.e_nexus.vrplatform.demo.scene.LoremIpsumPanel}'s
 * 20x10 character grid.
 *
 * <p>Not {@code @Named}: registered explicitly by {@code AppConfig} (see its
 * comment for why -- OSGi bundles aren't reliably classpath-scannable).
 */
public class LoremIpsumContent implements TextContent {

    private static final int WRAP_COLUMNS = 20;

    private static final String[] PARAGRAPHS = {
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
        "Sed do eiusmod tempor incididunt ut labore et dolore.",
        "Ut enim ad minim veniam quis nostrud exercitation.",
    };

    private final List<String> lines = buildLines();

    @Override
    public List<String> lines() {
        return lines;
    }

    private static List<String> buildLines() {
        List<String> result = new ArrayList<>();
        for (String paragraph : PARAGRAPHS) {
            result.addAll(wrap(paragraph, WRAP_COLUMNS));
        }
        return result;
    }

    private static List<String> wrap(String text, int columns) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            int lengthWithWord = current.isEmpty() ? word.length() : current.length() + 1 + word.length();
            if (lengthWithWord > columns && !current.isEmpty()) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }
}
