package de.e_nexus.vrplatform.tilelist;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import de.e_nexus.vrplatform.scenecore.PanelComponent;
import de.e_nexus.vrplatform.scenecore.PanelPosition;
import de.e_nexus.vrplatform.scenecore.PanelSize;
import de.e_nexus.vrplatform.scenecore.TextPanel;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A grid of square-image-plus-caption tiles rendered onto a single textured
 * quad, the same texture-atlas approach {@link TextPanel} uses for text (own
 * {@link ShaderProgram}/VAO/texture, not a subclass of it: {@code
 * TextPanel.renderPixels()} is private and only ever draws character grids,
 * with no hook for arbitrary image content).
 *
 * <p>The panel's own world-space size ({@code panelWidth}/{@code
 * panelHeight}, set once at construction) is fixed; only {@link #tileSize}
 * is a live property. Growing the list or shrinking {@code tileSize} never
 * reflows into scrolling -- whatever grid of tiles fits the fixed panel at
 * the current {@code tileSize} is shown, and every tile past that capacity
 * is simply not drawn (see {@link #visibleCount()}).
 */
public class TileList implements PanelComponent {

    private static final Color PLACEHOLDER_BG = new Color(0.22f, 0.22f, 0.28f);
    private static final Color PLACEHOLDER_FG = new Color(0.55f, 0.55f, 0.65f);
    private static final Color SELECTION_COLOR = Color.CYAN;
    private static final Color TEXT_COLOR = new Color(0.9f, 0.9f, 0.92f);

    // Plain outline, drawn only on aimedTileIndex (whichever tile is being
    // looked at dead-on right now, see hitTestTile) -- distinct from
    // SELECTION_COLOR's separate, thicker ring around selectedTileIndex.
    private static final Color TILE_BORDER_COLOR = Color.WHITE;
    private static final float TILE_BORDER_WIDTH = 2f;

    // Fraction of a tile cell's total height given to the caption beneath
    // the square image -- two successive +30% bumps on top of a plain
    // half-and-half-ish 0.35 split (first handed over from the image
    // shrinking by IMAGE_SCALE, then a further +30% on request), so the
    // caption's auto-fit font comes out correspondingly bigger without the
    // two rows colliding (cellHeightPx, used for grid spacing, is derived
    // from this same value).
    private static final float TEXT_BLOCK_FRACTION = 0.35f * 1.3f * 1.3f;
    private static final int MAX_CAPTION_LINES = 3;

    // The square image (or placeholder) fills its slot edge-to-edge -- image
    // and caption together fill the whole tile, right up to TILE_BORDER_COLOR.
    private static final float IMAGE_SCALE = 1.0f;

    private final Matrix4f modelMatrix;
    private final PanelTheme theme;
    private final float panelWidth;
    private final float panelHeight;

    private final List<Tile> tiles = new ArrayList<>();
    private final Map<String, BufferedImage> imageCache = new HashMap<>();

    private float tileSize;
    private boolean readonly;
    private int selectedTileIndex = -1;

    // Which tile (if any) is currently being looked at dead-on -- distinct
    // from selectedTileIndex, and driven every frame by the owning scene via
    // hitTestTile()/setAimedTileIndex(), not by this component itself.
    private int aimedTileIndex = -1;

    private int vao, vbo, ebo, textureId;
    private ShaderProgram shader;

    public TileList(float x, float y, float z, float panelWidth, float panelHeight, float tileSize, PanelTheme theme) {
        this.modelMatrix = new Matrix4f().translate(x, y, z);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.tileSize = tileSize;
        this.theme = theme;
    }

    @Override
    public PanelPosition position() {
        org.joml.Vector3f t = modelMatrix.getTranslation(new org.joml.Vector3f());
        return new PanelPosition(t.x, t.y, t.z);
    }

    @Override
    public PanelSize size() {
        return new PanelSize(panelWidth, panelHeight);
    }

    public float getTileSize() {
        return tileSize;
    }

    /** Live-resizes the grid: tiles re-flow at the new size, and the fitting/hiding rule re-applies immediately. */
    public void setTileSize(float tileSize) {
        if (this.tileSize != tileSize) {
            this.tileSize = tileSize;
            refresh();
        }
    }

    public boolean isReadonly() {
        return readonly;
    }

    /** Only gates {@link #selectNext()}/{@link #selectPrevious()}; {@link #setSelectedTileIndex} is unaffected. */
    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public int getSelectedTileIndex() {
        return selectedTileIndex;
    }

    /** Programmatic selection: always allowed regardless of {@link #isReadonly()}. Clamped to the visible tiles. */
    public void setSelectedTileIndex(int index) {
        int visible = visibleCount();
        int clamped = visible == 0 ? -1 : Math.max(0, Math.min(index, visible - 1));
        if (this.selectedTileIndex != clamped) {
            this.selectedTileIndex = clamped;
            refresh();
        }
    }

    public int getAimedTileIndex() {
        return aimedTileIndex;
    }

    /** Which tile (if any) draws the plain white outline; -1 shows it on none. Call once per frame from {@link #hitTestTile}. */
    public void setAimedTileIndex(int index) {
        if (this.aimedTileIndex != index) {
            this.aimedTileIndex = index;
            refresh();
        }
    }

    /**
     * Which tile (if any) a ray from {@code rayOrigin} toward {@code rayDirection}
     * points at dead-on, assuming this panel sits unrotated in its XY-plane at
     * its own z (true of every PanelComponent built the way this one is --
     * see the vertex layout in {@code TextPanel.initialize()}). Returns -1 if
     * the ray misses the panel, or lands in the padding/gap around a tile
     * rather than on one. Doesn't itself change {@link #getAimedTileIndex()};
     * pass the result to {@link #setAimedTileIndex} to actually apply it.
     */
    public int hitTestTile(Vector3f rayOrigin, Vector3f rayDirection) {
        PanelPosition centerPos = position();
        if (Math.abs(rayDirection.z) < 1e-6f) {
            return -1;
        }
        float t = (centerPos.z() - rayOrigin.z) / rayDirection.z;
        if (t <= 0) {
            return -1;
        }
        float hitX = rayOrigin.x + t * rayDirection.x;
        float hitY = rayOrigin.y + t * rayDirection.y;

        float halfW = panelWidth / 2f;
        float halfH = panelHeight / 2f;
        if (hitX < centerPos.x() - halfW || hitX > centerPos.x() + halfW
                || hitY < centerPos.y() - halfH || hitY > centerPos.y() + halfH) {
            return -1;
        }

        float localU = (hitX - (centerPos.x() - halfW)) / panelWidth;
        float localV = ((centerPos.y() + halfH) - hitY) / panelHeight; // v=0 at the top, matching the UVs in TextPanel
        int pixelX = Math.round(localU * TextPanel.TEX_SIZE);
        int pixelY = Math.round(localV * TextPanel.TEX_SIZE);

        Grid grid = computeGrid();
        int visible = Math.min(tiles.size(), grid.cols() * grid.rows());
        int pad = TextPanel.PAD;
        for (int i = 0; i < visible; i++) {
            int col = i % grid.cols();
            int row = i / grid.cols();
            int cellX = pad + col * (grid.cellWidthPx() + grid.gapPx());
            int cellY = pad + row * (grid.cellHeightPx() + grid.gapPx());
            // Matches the border's own shrunk extent (see renderPixels), not
            // the full per-row slot: the dead space below a short caption
            // isn't part of this tile visually, so it shouldn't be aimable either.
            int contentHeightPx = actualContentHeightPx(tiles.get(i), grid);
            if (pixelX >= cellX && pixelX < cellX + grid.cellWidthPx()
                    && pixelY >= cellY && pixelY < cellY + contentHeightPx) {
                return i;
            }
        }
        return -1;
    }

    /** User-facing navigation (e.g. bound to an aim gesture or arrow keys): no-op while {@link #isReadonly()}. */
    public void selectNext() {
        if (readonly) {
            return;
        }
        int visible = visibleCount();
        if (visible == 0) {
            return;
        }
        selectedTileIndex = (selectedTileIndex + 1 + visible) % visible;
        refresh();
    }

    /** User-facing navigation (e.g. bound to an aim gesture or arrow keys): no-op while {@link #isReadonly()}. */
    public void selectPrevious() {
        if (readonly) {
            return;
        }
        int visible = visibleCount();
        if (visible == 0) {
            return;
        }
        selectedTileIndex = (selectedTileIndex - 1 + visible) % visible;
        refresh();
    }

    public void addTile(Tile tile) {
        tiles.add(tile);
        refresh();
    }

    public void setTiles(List<Tile> newTiles) {
        tiles.clear();
        tiles.addAll(newTiles);
        if (selectedTileIndex >= visibleCount()) {
            selectedTileIndex = visibleCount() == 0 ? -1 : visibleCount() - 1;
        }
        refresh();
    }

    public List<Tile> getTiles() {
        return List.copyOf(tiles);
    }

    /** How many tiles the current panel size / tileSize combination can actually show. */
    public int visibleCount() {
        Grid grid = computeGrid();
        return Math.min(tiles.size(), grid.cols * grid.rows);
    }

    public void initialize() {
        shader = new ShaderProgram(TextPanel.class, "/shaders/text_vertex.glsl", "/shaders/text_fragment.glsl");
        shader.compile();

        float halfW = panelWidth / 2f;
        float halfH = panelHeight / 2f;
        float[] verts = {
            -halfW, -halfH, 0f,  0f, 1f,
             halfW, -halfH, 0f,  1f, 1f,
             halfW,  halfH, 0f,  1f, 0f,
            -halfW,  halfH, 0f,  0f, 0f,
        };
        int[] indices = { 0, 1, 2,  2, 3, 0 };

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(verts.length);
        try {
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(fb);
        }

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = MemoryUtil.memAllocInt(indices.length);
        try {
            ib.put(indices).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(ib);
        }

        int stride = 5 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);

        refresh();
    }

    public void refresh() {
        ByteBuffer buf = renderPixels();
        try {
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, TextPanel.TEX_SIZE, TextPanel.TEX_SIZE, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, buf);
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    // Same shader/uniform contract as TextPanel.render(...) (text_vertex/text_fragment.glsl,
    // including the backdrop-blur reflection sampled through uBackdrop): this
    // renders into the same pass, not a separate one, so it must match exactly.
    public void render(Matrix4f proj, Matrix4f view, int backdropTexture, int viewportWidth, int viewportHeight) {
        shader.use();
        shader.setMatrix4f("model", modelMatrix);
        shader.setMatrix4f("view", view);
        shader.setMatrix4f("projection", proj);
        shader.setVec2("uViewportSize", viewportWidth, viewportHeight);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        shader.setInt("uTexture", 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, backdropTexture);
        shader.setInt("uBackdrop", 1);

        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void destroy() {
        glDeleteTextures(textureId);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        if (shader != null) {
            shader.destroy();
        }
    }

    private record Grid(int tileSizePx, int textBlockPx, int cellWidthPx, int cellHeightPx, int gapPx,
                         int cols, int rows) {
    }

    private Grid computeGrid() {
        int pad = TextPanel.PAD;
        int gap = Math.round(pad / 2f);
        int tileSizePx = Math.max(1, Math.round(tileSize * TextPanel.TEX_SIZE));
        int textBlockPx = Math.round(tileSizePx * TEXT_BLOCK_FRACTION);
        int cellWidthPx = tileSizePx;
        int cellHeightPx = tileSizePx + textBlockPx;

        int availableWidthPx = TextPanel.TEX_SIZE - 2 * pad;
        int availableHeightPx = TextPanel.TEX_SIZE - 2 * pad;
        int cols = Math.max(1, (availableWidthPx + gap) / (cellWidthPx + gap));
        int rows = Math.max(1, (availableHeightPx + gap) / (cellHeightPx + gap));
        return new Grid(tileSizePx, textBlockPx, cellWidthPx, cellHeightPx, gap, cols, rows);
    }

    private ByteBuffer renderPixels() {
        BufferedImage img = new BufferedImage(TextPanel.TEX_SIZE, TextPanel.TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Texture is always TEX_SIZE x TEX_SIZE, but the quad it's mapped
        // onto may not be square (see panelWidth/panelHeight) -- pre-squeeze
        // horizontally by the inverse ratio, same correction TextPanel uses,
        // so tiles/glyphs aren't stretched when the panel is wider/narrower
        // than it is tall. Grid layout math itself stays in unscaled texture
        // pixels; only the actual drawing is scaled.
        float glyphScaleX = panelHeight / panelWidth;

        int corner = (int) theme.cornerRadius();
        g.setColor(theme.backgroundColor(false));
        g.fillRoundRect(0, 0, TextPanel.TEX_SIZE, TextPanel.TEX_SIZE, corner, corner);
        g.setColor(theme.borderColor());
        g.setStroke(new BasicStroke(theme.borderWidth()));
        g.drawRoundRect(4, 4, TextPanel.TEX_SIZE - 8, TextPanel.TEX_SIZE - 8, corner, corner);

        Grid grid = computeGrid();
        int visible = Math.min(tiles.size(), grid.cols() * grid.rows());

        // Squeezed once for the whole grid, not per tile: applying it inside
        // each tile's own translate but computing cellX/cellY spacing in
        // unscaled units (as below) would squeeze each tile's content while
        // leaving the slots between them full width, drifting the two out of
        // alignment more with every column -- exactly the gap between the
        // placeholder and its border seen in testing. One outer scale keeps
        // every position (tile content, its border, the selection ring)
        // consistent, since they all then live in the same squeezed space.
        AffineTransform gridSaved = g.getTransform();
        g.scale(glyphScaleX, 1.0);

        int pad = TextPanel.PAD;
        for (int i = 0; i < visible; i++) {
            int col = i % grid.cols();
            int row = i / grid.cols();
            int cellX = pad + col * (grid.cellWidthPx() + grid.gapPx());
            int cellY = pad + row * (grid.cellHeightPx() + grid.gapPx());

            AffineTransform saved = g.getTransform();
            g.translate(cellX, cellY);
            drawTile(g, tiles.get(i), grid);
            g.setTransform(saved);

            // Own bottom edge pulled up to hug this tile's actual content
            // (image + however many caption lines it really has), not the
            // uniform per-row slot reserved for up to MAX_CAPTION_LINES --
            // that slot still governs row-to-row spacing (see cellY above),
            // just not where a tile's own border/aim region ends.
            int contentHeightPx = actualContentHeightPx(tiles.get(i), grid);

            if (i == aimedTileIndex) {
                g.setColor(TILE_BORDER_COLOR);
                g.setStroke(new BasicStroke(TILE_BORDER_WIDTH));
                g.drawRect(cellX, cellY, grid.cellWidthPx(), contentHeightPx);
            }

            if (i == selectedTileIndex) {
                g.setColor(SELECTION_COLOR);
                g.setStroke(new BasicStroke(Math.max(2, theme.borderWidth() / 2f)));
                g.drawRoundRect(cellX - 2, cellY - 2, grid.cellWidthPx() + 4, contentHeightPx + 4, 16, 16);
            }
        }
        g.setTransform(gridSaved);

        g.dispose();

        int[] pixels = new int[TextPanel.TEX_SIZE * TextPanel.TEX_SIZE];
        img.getRGB(0, 0, TextPanel.TEX_SIZE, TextPanel.TEX_SIZE, pixels, 0, TextPanel.TEX_SIZE);

        ByteBuffer buf = MemoryUtil.memAlloc(TextPanel.TEX_SIZE * TextPanel.TEX_SIZE * 4);
        for (int p : pixels) {
            buf.put((byte) ((p >> 16) & 0xFF));
            buf.put((byte) ((p >> 8) & 0xFF));
            buf.put((byte) (p & 0xFF));
            buf.put((byte) ((p >> 24) & 0xFF));
        }
        buf.flip();
        return buf;
    }

    // Drawn in a coordinate space already translated to the cell's top-left
    // corner (and pre-scaled horizontally by glyphScaleX -- see caller), so
    // everything here is plain unscaled texture pixels relative to (0,0).
    private void drawTile(Graphics2D g, Tile tile, Grid grid) {
        BufferedImage image = resolveImage(tile.imagePath());
        int drawnSize = Math.round(grid.tileSizePx() * IMAGE_SCALE);
        int inset = (grid.tileSizePx() - drawnSize) / 2;
        if (image != null) {
            g.drawImage(image, inset, inset, drawnSize, drawnSize, null);
        } else {
            drawPlaceholder(g, inset, drawnSize);
        }
        // Caption position/wrap width is the full cell (grid.tileSizePx()),
        // not the shrunk image -- text wraps against the tile, not the icon.
        drawCaption(g, tile.text(), grid);
    }

    private void drawPlaceholder(Graphics2D g, int inset, int size) {
        g.setColor(PLACEHOLDER_BG);
        g.fillRoundRect(inset, inset, size, size, 24, 24);

        int fontSize = Math.max(8, size / 2);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        String mark = "?";
        int textX = inset + (size - fm.stringWidth(mark)) / 2;
        int textY = inset + (size + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(PLACEHOLDER_FG);
        g.drawString(mark, textX, textY);
    }

    private void drawCaption(Graphics2D g, String text, Grid grid) {
        int fontSize = captionFontSize(grid.textBlockPx());
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        List<String> lines = wrap(text == null ? "" : text, fm, grid.tileSizePx(), MAX_CAPTION_LINES);
        int lineHeight = fm.getAscent() + fm.getDescent() + 4;
        // Flush against the image's own bottom edge, no gap: a short caption
        // (fewer than MAX_CAPTION_LINES lines) leaves any spare room at the
        // tile's bottom edge instead of between the image and the text.
        int y = grid.tileSizePx() + fm.getAscent();
        g.setColor(TEXT_COLOR);
        for (String line : lines) {
            int x = Math.max(0, (grid.tileSizePx() - fm.stringWidth(line)) / 2);
            g.drawString(line, x, y);
            y += lineHeight;
        }
    }

    // How tall this specific tile's actual content (image + only as many
    // caption lines as it really needs) is, as opposed to grid.cellHeightPx(),
    // the uniform per-row slot reserved for up to MAX_CAPTION_LINES.
    private int actualContentHeightPx(Tile tile, Grid grid) {
        int fontSize = captionFontSize(grid.textBlockPx());
        FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
        List<String> lines = wrap(tile.text() == null ? "" : tile.text(), fm, grid.tileSizePx(), MAX_CAPTION_LINES);
        int lineHeight = fm.getAscent() + fm.getDescent() + 4;
        return grid.tileSizePx() + lines.size() * lineHeight;
    }

    // Largest size at which MAX_CAPTION_LINES rows of text still fit the
    // caption band's height budget, same shrink-to-fit approach Terminal
    // uses for its own fixed character grid.
    private static int captionFontSize(int textBlockPx) {
        int fontSize = 40;
        while (fontSize > 8) {
            FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
            int totalHeight = (fm.getAscent() + fm.getDescent() + 4) * MAX_CAPTION_LINES;
            if (totalHeight <= textBlockPx) {
                break;
            }
            fontSize--;
        }
        return fontSize;
    }

    // Greedy word-wrap capped at maxLines; anything left over past that is
    // simply dropped (no scrolling/expansion within a tile's fixed caption
    // band), with an ellipsis on the last shown line to signal the cut.
    private static List<String> wrap(String text, FontMetrics fm, int maxWidthPx, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (fm.stringWidth(candidate) <= maxWidthPx || current.isEmpty()) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
                if (lines.size() == maxLines) {
                    break;
                }
            }
        }
        if (lines.size() < maxLines && !current.isEmpty()) {
            lines.add(current.toString());
        }
        boolean truncated = lines.size() >= maxLines
                && words.length > 0
                && !String.join(" ", lines).equals(text.trim());
        if (truncated) {
            String last = lines.get(lines.size() - 1);
            while (fm.stringWidth(last + "…") > maxWidthPx && last.length() > 1) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(lines.size() - 1, last + "…");
        }
        return lines;
    }

    private BufferedImage resolveImage(String path) {
        if (path == null) {
            return null;
        }
        return imageCache.computeIfAbsent(path, p -> {
            try (InputStream in = TileList.class.getResourceAsStream(p)) {
                return in == null ? null : ImageIO.read(in);
            } catch (IOException e) {
                return null;
            }
        });
    }
}
