package de.e_nexus.vrplatform.scenecore;

import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.SingleLineTextContent;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;

/**
 * A {@link TypeableTextPanel} that never breaks onto a second line: Enter
 * and Up/Down are no-ops (inherited from the base class), and an overlong
 * value shrinks via {@code TextPanel}'s font-fit fallback instead of
 * wrapping. See {@link MultiLineTextPanel} for a field that supports
 * several lines.
 */
public class SingleLineTextPanel extends TypeableTextPanel {

    public SingleLineTextPanel(float x, float y, float z,
                                PanelTheme theme, SingleLineTextContent content, TextDecoration decoration) {
        super(x, y, z, theme, content, decoration);
    }

    public SingleLineTextPanel(float x, float y, float z, PanelTheme theme, SingleLineTextContent content,
                                TextDecoration decoration, int baseFontSize) {
        super(x, y, z, theme, content, decoration, baseFontSize);
    }
}
