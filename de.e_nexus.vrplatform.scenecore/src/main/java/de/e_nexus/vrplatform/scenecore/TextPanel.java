package de.e_nexus.vrplatform.scenecore;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import de.e_nexus.vrplatform.scenecore.text.CaretPosition;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.SelectionRange;
import de.e_nexus.vrplatform.scenecore.text.TextContent;
import de.e_nexus.vrplatform.scenecore.text.TextDecoration;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a floating text panel as a textured quad. Subclasses supply the
 * {@link PanelTheme} (background/border chrome), {@link TextContent} (plain
 * lines) and {@link TextDecoration} (per-character font and color) so those
 * three concerns can vary independently.
 */
public abstract class TextPanel implements PanelComponent {

    // Doubled from the original 1024: PAD is a fixed pixel amount, so every
    // "fit N columns/rows into TEX_SIZE" calculation (Checkbox, Terminal,
    // LoremIpsumPanel) scales its chosen font size up along with this,
    // giving small-factor text (e.g. VariedFontSizeDecoration's 0.5x words)
    // enough source pixels to still look sharp after mipmap downsampling --
    // at 1024 they had so little detail to begin with that mipmapping (see
    // GL_LINEAR_MIPMAP_LINEAR below) blurred them into illegibility.
    public static final int TEX_SIZE = 2048;
    public static final int PAD = 48;
    public static final int BASE_FONT_SIZE = 90;
    private static final int LINE_NUMBER_FONT_SIZE = 140; // 5x the original 28
    private static final int GUTTER_MARGIN = 40;

    // Matches common desktop-terminal cursor blink rates (~500-530ms/phase).
    private static final long CARET_BLINK_MILLIS = 530;

    /**
     * Whether a blinking caret is in its visible half-cycle right now, driven
     * by wall-clock time so every panel blinks in phase. {@link #drawCaret}
     * checks this itself, but a panel that only calls {@link #refresh()} on
     * content changes (e.g. Terminal, between bytes from its pty) needs to
     * call this once per frame too, to notice the phase flip and force a
     * refresh purely for the blink -- otherwise the caret would just freeze
     * in whatever phase it last happened to be redrawn in.
     */
    public static boolean caretBlinkOn() {
        return (System.currentTimeMillis() / CARET_BLINK_MILLIS) % 2 == 0;
    }

    private final Matrix4f modelMatrix;
    private final int baseFontSize;
    private int vao, vbo, ebo, textureId;
    private ShaderProgram shader;

    protected TextPanel(float x, float y, float z) {
        this(x, y, z, BASE_FONT_SIZE);
    }

    /**
     * @param baseFontSize starting font size for rendered text, in place of
     *                     {@link #BASE_FONT_SIZE} — lets a panel that needs a
     *                     specific character grid (e.g. a terminal wanting an
     *                     exact column/row count within the fixed texture
     *                     resolution) pick its own size instead of the large
     *                     VR-readable default.
     */
    protected TextPanel(float x, float y, float z, int baseFontSize) {
        this.modelMatrix = new Matrix4f().translate(x, y, z);
        this.baseFontSize = baseFontSize;
    }

    @Override
    public PanelPosition position() {
        Vector3f t = modelMatrix.getTranslation(new Vector3f());
        return new PanelPosition(t.x, t.y, t.z);
    }

    @Override
    public PanelSize size() {
        return new PanelSize(panelWidth(), panelHeight());
    }

    protected abstract PanelTheme theme();

    protected abstract TextContent content();

    protected abstract TextDecoration decoration();

    /** Caret to draw as a thin bar between characters, or null for none (the default). */
    protected CaretPosition caret() {
        return null;
    }

    /** Current text selection as absolute buffer offsets, or null for none (the default). */
    protected SelectionRange selection() {
        return null;
    }

    /** Whether to reserve a left-hand gutter with 1-based line numbers. Off by default. */
    protected boolean showLineNumbers() {
        return false;
    }

    /** Whether this panel currently has input focus; draws a highlighted border. Off by default. */
    protected boolean focused() {
        return false;
    }

    /** Whether this panel participates in focus at all (only true text fields do). */
    protected boolean focusable() {
        return false;
    }

    /** World-space quad width in meters. 1 m by default; override to size a panel to its content. */
    protected float panelWidth() {
        return 1f;
    }

    /** World-space quad height in meters. 1 m by default; override to size a panel to its content. */
    protected float panelHeight() {
        return 1f;
    }

    /**
     * Characters that fit on one line at the base font size, given this
     * panel's current gutter state (see {@link #showLineNumbers()}) — the
     * same width math {@link #renderPixels()} uses, so a caller-side
     * line-length limit (e.g. {@code TypedTextContent.maxLineLength}) stays
     * in sync with the actual rendered width instead of a separate guess.
     */
    protected int estimateMaxLineLength() {
        int gutterWidth = showLineNumbers() ? measureGutterWidth() : 0;
        int textAreaWidth = TEX_SIZE - 2 * PAD - gutterWidth;
        FontMetrics fm = scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, baseFontSize));
        int charWidth = Math.max(1, fm.charWidth('0'));
        return Math.max(1, textAreaWidth / charWidth);
    }

    private static int measureGutterWidth() {
        FontMetrics fm = scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, LINE_NUMBER_FONT_SIZE));
        return fm.stringWidth("99") + GUTTER_MARGIN;
    }

    public static FontMetrics scratchFontMetrics(Font font) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return scratch.createGraphics().getFontMetrics(font);
    }

    public void initialize() {
        shader = new ShaderProgram(TextPanel.class, "/shaders/text_vertex.glsl", "/shaders/text_fragment.glsl");
        shader.compile();

        // panelWidth() × panelHeight() quad (1 m × 1 m by default), CCW front face (normal = +Z, toward camera)
        // UV: v=0 maps to GL texture row 0 = first uploaded byte = AWT row 0 = top of image
        float halfW = panelWidth() / 2f;
        float halfH = panelHeight() / 2f;
        float[] verts = {
            -halfW, -halfH, 0f,  0f, 1f,   // bottom-left  → AWT bottom-left
             halfW, -halfH, 0f,  1f, 1f,   // bottom-right → AWT bottom-right
             halfW,  halfH, 0f,  1f, 0f,   // top-right    → AWT top-right
            -halfW,  halfH, 0f,  0f, 0f,   // top-left     → AWT top-left
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
        // Mipmapped + trilinear: without mipmaps, small on-screen text (a
        // panel viewed from a few meters away, or any word rendered at a
        // small font size) minifies straight from the full-res texture every
        // frame, and tiny sub-pixel changes in viewing angle/distance shift
        // which texels get sampled -- visible as shimmering/flickering
        // glyphs. Mipmaps give the sampler pre-filtered lower-res versions to
        // blend between instead, which is stable frame to frame.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);

        refresh();
    }

    /** Re-renders content/theme/decoration onto the panel's texture. Call after mutating them. */
    public void refresh() {
        ByteBuffer buf = renderPixels();
        try {
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, TEX_SIZE, TEX_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private ByteBuffer renderPixels() {
        TextContent content = content();
        List<String> lines = content.lines();
        PanelTheme theme = theme();
        TextDecoration decoration = decoration();
        CaretPosition caret = caret();
        SelectionRange selection = selection();

        BufferedImage img = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int corner = (int) theme.cornerRadius();
        boolean dimmed = focusable() && !focused();
        g.setColor(theme.backgroundColor(dimmed));
        g.fillRoundRect(0, 0, TEX_SIZE, TEX_SIZE, corner, corner);

        g.setColor(theme.borderColor());
        g.setStroke(new BasicStroke(theme.borderWidth()));
        g.drawRoundRect(4, 4, TEX_SIZE - 8, TEX_SIZE - 8, corner, corner);

        if (focused()) {
            g.setColor(Color.CYAN);
            g.setStroke(new BasicStroke(10));
            g.drawRoundRect(4, 4, TEX_SIZE - 8, TEX_SIZE - 8, corner, corner);
        }

        // The texture is always a square TEX_SIZE x TEX_SIZE image, but the
        // quad it's mapped onto isn't (see panelWidth()/panelHeight()) -- a
        // panel wider than it is tall stretches every texel horizontally
        // when displayed, which fattens glyph shapes. Pre-squeezing glyphs
        // by the inverse ratio here cancels that back out, so letterforms
        // still look right; only the (unaffected) cursorX advance uses the
        // panel's actual character-cell spacing, so layout/wrap math is untouched.
        float glyphScaleX = panelHeight() / panelWidth();

        int lineSpacing = 12;
        boolean numbers = showLineNumbers();
        // Same gutter math as estimateMaxLineLength(), so the two stay in sync.
        FontMetrics numberFm = g.getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, LINE_NUMBER_FONT_SIZE));
        int gutterWidth = numbers ? measureGutterWidth() : 0;
        int x = PAD + gutterWidth; // left-aligned, after the optional gutter
        int y = PAD; // top-aligned: lines flow downward from here, independent of line count
        int textAreaWidth = TEX_SIZE - 2 * PAD - gutterWidth;

        if (numbers) {
            g.setColor(new Color(1f, 1f, 1f, 0.15f));
            g.drawLine(x - 12, PAD, x - 12, TEX_SIZE - PAD);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int fontSize = baseFontSize;
            // Measured with the first character's font; per-character fonts
            // are expected to share a size so this stays a good fit estimate.
            Font font = decoration.font(i, line, 0, fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            while (fm.stringWidth(line) > textAreaWidth && fontSize > 8) {
                fontSize -= 2;
                font = decoration.font(i, line, 0, fontSize);
                g.setFont(font);
                fm = g.getFontMetrics();
            }

            // Rows need to be tall enough for the (much bigger) line-number
            // glyphs too, or the first row's number clips at the panel top
            // and later rows collide with the number above them.
            int rowAscent  = numbers ? Math.max(fm.getAscent(),  numberFm.getAscent())  : fm.getAscent();
            int rowDescent = numbers ? Math.max(fm.getDescent(), numberFm.getDescent()) : fm.getDescent();

            y += rowAscent;

            if (selection != null) {
                int lineStartOffset = content.lineStartOffset(i);
                int lineEndOffset = lineStartOffset + line.length();
                int selStart = Math.max(selection.start(), lineStartOffset);
                int selEnd   = Math.min(selection.end(), lineEndOffset);
                if (selStart < selEnd) {
                    drawSelection(g, line, fm, x, y, selStart - lineStartOffset, selEnd - lineStartOffset, glyphScaleX);
                }
            }

            if (numbers) {
                int number = content.lineNumber(i);
                if (number > 0) {
                    drawLineNumber(g, number, PAD, gutterWidth, y, glyphScaleX);
                }
            }
            drawChars(g, line, fontSize, x, y, i, decoration, glyphScaleX);

            if (caret != null && caret.line() == i) {
                drawCaret(g, line, fm, x, y, caret.column(), glyphScaleX);
            }

            y += rowDescent + lineSpacing;
        }
        g.dispose();

        int[] pixels = new int[TEX_SIZE * TEX_SIZE];
        img.getRGB(0, 0, TEX_SIZE, TEX_SIZE, pixels, 0, TEX_SIZE);

        ByteBuffer buf = MemoryUtil.memAlloc(TEX_SIZE * TEX_SIZE * 4);
        for (int p : pixels) {
            buf.put((byte) ((p >> 16) & 0xFF));
            buf.put((byte) ((p >>  8) & 0xFF));
            buf.put((byte) ( p        & 0xFF));
            buf.put((byte) ((p >> 24) & 0xFF));
        }
        buf.flip();
        return buf;
    }

    private static void drawChars(Graphics2D g, String line, int fontSize, int x, int y,
                                   int lineIndex, TextDecoration decoration, float glyphScaleX) {
        int cursorX = x;
        for (int c = 0; c < line.length(); c++) {
            String ch = String.valueOf(line.charAt(c));
            g.setFont(decoration.font(lineIndex, line, c, fontSize));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(decoration.color(lineIndex, line, c));

            AffineTransform saved = g.getTransform();
            g.translate(cursorX, y);
            g.scale(glyphScaleX, 1.0);
            g.drawString(ch, 0, 0);
            g.setTransform(saved);

            // Scaled to match the glyph actually drawn above (g.scale(glyphScaleX, 1.0)):
            // advancing by the unscaled width here left a gap after every character
            // whenever glyphScaleX was far from 1.0 (e.g. a wide, short panel).
            cursorX += Math.round(fm.stringWidth(ch) * glyphScaleX);
        }
    }

    // Drawn behind the text (fillRect before drawChars) so the highlighted
    // characters remain readable on top of it.
    private static void drawSelection(Graphics2D g, String line, FontMetrics fm, int lineX, int baselineY,
                                       int fromCol, int toCol, float glyphScaleX) {
        int x1 = lineX + Math.round(fm.stringWidth(line.substring(0, fromCol)) * glyphScaleX);
        int x2 = lineX + Math.round(fm.stringWidth(line.substring(0, toCol)) * glyphScaleX);
        int top = baselineY - fm.getAscent();
        int height = fm.getAscent() + fm.getDescent();
        g.setColor(new Color(0.3f, 0.5f, 1f, 0.45f));
        g.fillRect(x1, top, Math.max(1, x2 - x1), height);
    }

    // Drawn as a thin bar positioned between characters, not an inserted
    // character in the text stream — a real character would shift everything
    // after it and take up a whole cell, unlike a real editor's caret. Offset
    // half a (monospaced) character width further right than the raw column
    // boundary: at the bare boundary the bar visually lands on top of the
    // glyph before/after it rather than in the gap between them.
    private static void drawCaret(Graphics2D g, String line, FontMetrics fm, int lineX, int baselineY, int column,
                                   float glyphScaleX) {
        if (!caretBlinkOn()) {
            return;
        }
        int col = Math.min(column, line.length());
        float halfCharWidth = fm.charWidth('0') / 2f;
        int caretX = lineX + Math.round((fm.stringWidth(line.substring(0, col)) + halfCharWidth) * glyphScaleX);
        int caretTop = baselineY - fm.getAscent();
        int caretHeight = fm.getAscent() + fm.getDescent();
        g.setColor(Color.YELLOW);
        g.fillRect(caretX, caretTop, Math.max(2, caretHeight / 16), caretHeight);
    }

    private static void drawLineNumber(Graphics2D g, int number, int gutterX, int gutterWidth, int baselineY,
                                        float glyphScaleX) {
        Font numberFont = new Font(Font.MONOSPACED, Font.PLAIN, LINE_NUMBER_FONT_SIZE);
        g.setFont(numberFont);
        g.setColor(new Color(0.55f, 0.55f, 0.62f));
        String text = String.valueOf(number);
        FontMetrics fm = g.getFontMetrics();
        int rightMargin = 20;
        int xPos = gutterX + gutterWidth - Math.round(fm.stringWidth(text) * glyphScaleX) - rightMargin;

        AffineTransform saved = g.getTransform();
        g.translate(xPos, baselineY);
        g.scale(glyphScaleX, 1.0);
        g.drawString(text, 0, 0);
        g.setTransform(saved);
    }

    public void render(Matrix4f proj, Matrix4f view, int backdropTexture, int viewportWidth, int viewportHeight) {
        shader.use();
        shader.setMatrix4f("model",      modelMatrix);
        shader.setMatrix4f("view",       view);
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
        if (shader != null) shader.destroy();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteTextures(textureId);
    }
}
