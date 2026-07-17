package de.e_nexus.vrplatform.scenecore;

/** Common contract for anything placed in the scene as a sized, positioned panel. */
public interface PanelComponent extends FrameElement {

    PanelPosition position();

    PanelSize size();
}
