package de.e_nexus.vrplatform.cuboidframe;

import de.e_nexus.vrplatform.scenecore.FrameElement;

/**
 * Splits a {@link CuboidFrame}'s interior in two along one axis. Each side
 * ({@link #first()}/{@link #second()}) is itself either a leaf
 * {@link PanelComponent} or a further nested {@link FrameSplit}.
 */
public final class FrameSplit implements FrameElement {

    public enum Direction {
        /** Left/right: splits the frame into a left and a right side. */
        X,
        /** Up/down: splits the frame into a top and a bottom side. */
        Y,
        /** Front/back (depth): splits the frame into a near and a far side. */
        Z
    }

    private final Direction direction;
    private final float minPercent;
    private final float maxPercent;
    private float percent;
    private final float minSize;
    private final float maxSize;
    private final boolean movable;
    private final FrameElement first;
    private final FrameElement second;

    public FrameSplit(Direction direction, float minPercent, float maxPercent, float percent, float minSize,
            float maxSize, boolean movable, FrameElement first, FrameElement second) {
        this.direction = direction;
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
        this.percent = percent;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.movable = movable;
        this.first = first;
        this.second = second;
    }

    public Direction direction() {
        return direction;
    }

    public float minPercent() {
        return minPercent;
    }

    public float maxPercent() {
        return maxPercent;
    }

    public float percent() {
        return percent;
    }

    /** World-space lower bound (meters, along {@link #direction()}) on the split's first side. */
    public float minSize() {
        return minSize;
    }

    /** World-space upper bound (meters, along {@link #direction()}) on the split's first side. */
    public float maxSize() {
        return maxSize;
    }

    public boolean movable() {
        return movable;
    }

    public FrameElement first() {
        return first;
    }

    public FrameElement second() {
        return second;
    }

    /** Moves the split, clamped to [{@link #minPercent()}, {@link #maxPercent()}]. Only allowed when {@link #movable()}. */
    public void setPercent(float percent) {
        if (!movable) {
            throw new IllegalStateException("this split is not movable");
        }
        this.percent = Math.max(minPercent, Math.min(maxPercent, percent));
    }
}
