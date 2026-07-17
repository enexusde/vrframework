package de.e_nexus.vrplatform.demo.scene;

import de.e_nexus.vrplatform.demo.scene.text.LoremIpsumContent;
import de.e_nexus.vrplatform.demo.scene.text.VariedFontSizeDecoration;
import de.e_nexus.vrplatform.scenecore.TextPanel;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.TextContent;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;
import jakarta.inject.Inject;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

/**
 * A static 20-column x 10-row showcase panel: three short Lorem Ipsum
 * paragraphs rendered with {@link VariedFontSizeDecoration} cycling through
 * ten different font sizes.
 *
 * <p>A CDI-managed bean (default {@code @Dependent} scope), added explicitly
 * to the Weld SE container instead of relying on classpath scanning (see
 * VRApplication for why).
 */
public class LoremIpsumPanel extends TextPanel {

    private static final float DEFAULT_X = 0f, DEFAULT_Y = 0f, DEFAULT_Z = -2.2f;
    private static final int COLUMNS = 20;
    private static final int ROWS = 10;

    // 0-indexed: "Lorem"=0, "ipsum"=1, "dolor"=2 -- the word whose rendered
    // size gets promoted to the theme's standard text size (see VRScene).
    private static final int STANDARD_SIZE_WORD_INDEX = 2;

    private final PanelTheme theme;
    private final TextContent content;
    private final TextDecoration decoration;
    private final int gridFontSize;

    @Inject
    public LoremIpsumPanel(PanelTheme theme, LoremIpsumContent content, VariedFontSizeDecoration decoration) {
        super(DEFAULT_X, DEFAULT_Y, DEFAULT_Z, fontSizeForGrid());
        this.theme = theme;
        this.content = content;
        this.decoration = decoration;
        this.gridFontSize = fontSizeForGrid();
    }

    /**
     * The absolute font size the sample text's third word ("dolor") actually
     * renders at right now -- asks the live content/decoration instead of
     * re-deriving the word/slot math by hand, so it stays correct even if
     * the paragraph text or the decoration's size pattern changes later.
     */
    public int thirdWordFontSize() {
        int wordIndex = 0;
        List<String> lines = content.lines();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            int charIndex = 0;
            for (String word : line.split(" ")) {
                if (wordIndex == STANDARD_SIZE_WORD_INDEX) {
                    return decoration.font(lineIndex, line, charIndex, gridFontSize).getSize();
                }
                wordIndex++;
                charIndex += word.length() + 1;
            }
        }
        throw new IllegalStateException("sample text has fewer than " + (STANDARD_SIZE_WORD_INDEX + 1) + " words");
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

    // Largest font size at which a COLUMNS x ROWS character grid still fits
    // the panel's fixed texture resolution (see TextPanel.TEX_SIZE/PAD).
    private static int fontSizeForGrid() {
        int textAreaWidth = TEX_SIZE - 2 * PAD;
        int textAreaHeight = TEX_SIZE - 2 * PAD;
        int lineSpacing = 12; // matches TextPanel.renderPixels()'s per-row spacing
        int fontSize = BASE_FONT_SIZE;
        while (fontSize > 8) {
            FontMetrics fm = scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
            boolean fitsWidth = fm.charWidth('0') * COLUMNS <= textAreaWidth;
            boolean fitsHeight = (fm.getAscent() + fm.getDescent() + lineSpacing) * ROWS <= textAreaHeight;
            if (fitsWidth && fitsHeight) {
                break;
            }
            fontSize--;
        }
        return fontSize;
    }
}
