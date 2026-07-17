package de.e_nexus.vrplatform.numberedtextpanel;

import de.e_nexus.vrplatform.scenecore.MultiLineTextPanel;
import de.e_nexus.vrplatform.scenecore.text.MultiLineTextContent;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

/**
 * A {@link MultiLineTextPanel} with a left-hand line-number gutter, toggleable
 * at runtime. Not a CDI singleton: the application's scene creates one per
 * text field with its own {@link MultiLineTextContent} so each field's buffer
 * is independent.
 */
public class NumberedTypeableTextPanel extends MultiLineTextPanel {

    private boolean lineNumbersVisible = true;

    public NumberedTypeableTextPanel(float x, float y, float z,
                                      PanelTheme theme, MultiLineTextContent content, TextDecoration decoration) {
        super(x, y, z, theme, content, decoration);
        // The gutter (reserved by showLineNumbers()) wasn't accounted for yet
        // when the superclass constructor made its estimate, since this
        // field hadn't been initialized — recompute now that it has.
        multiLineContent.setMaxLineLength(estimateMaxLineLength());
    }

    @Override
    protected boolean showLineNumbers() {
        return lineNumbersVisible;
    }

    public boolean isLineNumbersVisible() {
        return lineNumbersVisible;
    }

    public void setLineNumbersVisible(boolean visible) {
        lineNumbersVisible = visible;
        multiLineContent.setMaxLineLength(estimateMaxLineLength());
        refresh();
    }
}
