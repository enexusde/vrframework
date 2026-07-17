package de.e_nexus.vrplatform.scenecore.text;

import java.awt.Color;

/** Overall visual chrome of a text panel: background and border. */
public interface PanelTheme {
    /** @param dimmed true for a focusable panel that currently lacks focus */
    Color backgroundColor(boolean dimmed);
    Color borderColor();
    float borderWidth();
    float cornerRadius();

    /** Standard font size new text panels start at, unless they need a specific size of their own (e.g. to fit an exact character grid). */
    int getFontSize();

    void setFontSize(int fontSize);
}
