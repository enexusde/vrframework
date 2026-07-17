package de.e_nexus.vrplatform.scrollbox;

import de.e_nexus.vrplatform.scenecore.FrameElement;
import de.e_nexus.vrplatform.scenecore.PanelComponent;
import de.e_nexus.vrplatform.scenecore.PanelPosition;
import de.e_nexus.vrplatform.scenecore.PanelSize;

/**
 * A scrollable viewport onto a single {@link FrameElement}, like a scroll
 * pane but scrolling along one of three axes ({@link Direction#X},
 * {@link Direction#Y}, or {@link Direction#Z}) instead of being limited to
 * the horizontal/vertical pair a 2D scroll pane offers.
 *
 * <p>Like {@code CuboidFrame}, this is a pure data holder: it carries
 * {@link #target()} and the scroll range/offset, but does not itself lay
 * out, clip, or render that target -- the scene author positions the
 * target's own content and reads {@link #scrollPosition()} to decide what's
 * currently visible.
 */
public class ScrollBox implements PanelComponent {

    public enum Direction {
        /** Left/right scrolling. */
        X,
        /** Up/down scrolling. */
        Y,
        /** Forward/back (depth) scrolling. */
        Z
    }

    private final PanelPosition position;
    private final PanelSize size;
    private final Direction direction;
    private final long start;
    private final long end;
    // Named scrollPosition rather than position: PanelComponent.position()
    // already returns this box's own PanelPosition (its world-space
    // location), so the scroll offset needs a distinct name.
    private long scrollPosition;
    private FrameElement target;

    public ScrollBox(float x, float y, float z, float width, float height, Direction direction, long start,
            long end) {
        this.position = new PanelPosition(x, y, z);
        this.size = new PanelSize(width, height);
        this.direction = direction;
        this.start = start;
        this.end = end;
        this.scrollPosition = start;
    }

    @Override
    public PanelPosition position() {
        return position;
    }

    @Override
    public PanelSize size() {
        return size;
    }

    public Direction direction() {
        return direction;
    }

    public long start() {
        return start;
    }

    public long end() {
        return end;
    }

    public long scrollPosition() {
        return scrollPosition;
    }

    /** Moves the scroll offset, clamped to [{@link #start()}, {@link #end()}]. */
    public void setScrollPosition(long scrollPosition) {
        this.scrollPosition = Math.max(start, Math.min(end, scrollPosition));
    }

    /** The scrolled content, either a leaf {@link PanelComponent} or a nested split. Unset ({@code null}) until {@link #setTarget(FrameElement)} is called. */
    public FrameElement target() {
        return target;
    }

    public void setTarget(FrameElement target) {
        this.target = target;
    }
}
