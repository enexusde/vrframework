package de.e_nexus.vrplatform.scenecore;

/**
 * A node in a cuboid frame's layout tree: either a leaf {@link PanelComponent}
 * or a further split (see {@code FrameSplit} in vrplatform-cuboid-frame).
 *
 * <p>Not {@code sealed}: its two implementations live in separate Maven
 * modules (this one and vrplatform-cuboid-frame), and a sealed type's
 * {@code permits} list must be compilable together with the type itself --
 * incompatible with {@code FrameSplit} living downstream of this module.
 */
public interface FrameElement {
}
