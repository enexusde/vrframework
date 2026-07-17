package de.e_nexus.vrplatform.tilelist;

/**
 * One entry in a {@link TileList}: a square image with multi-line text below
 * it. {@code imagePath} is a classpath resource path (e.g. {@code
 * "/icons/foo.png"}); when null (or when loading it fails), {@link TileList}
 * draws the default question-mark placeholder instead.
 */
public record Tile(String imagePath, String text) {

    public Tile(String text) {
        this(null, text);
    }
}
