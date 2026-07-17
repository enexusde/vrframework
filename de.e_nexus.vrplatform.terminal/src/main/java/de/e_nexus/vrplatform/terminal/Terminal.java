package de.e_nexus.vrplatform.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import de.e_nexus.vrplatform.scenecore.TextPanel;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A real terminal: a persistent {@code ssh.exe} process running under an
 * actual pseudo-console (see {@link PtyProcessBuilder}), so whatever runs
 * over that SSH session -- the remote shell, {@code claude}, anything that
 * needs a real TTY -- behaves exactly like it would in a native terminal
 * window. Every keystroke {@link TerminalPanel} receives while focused is
 * written straight to the pty's stdin; the pty's own output (including the
 * remote side's echo of what was typed) is decoded and fed into a {@link
 * VTScreen}, which is what's actually rendered.
 */
public class Terminal {

    // Fixed grid: the panel's texture budget (see TextPanel.TEX_SIZE) is
    // shared with everything else on it, so this is chosen generously
    // without needing the font-shrink fallback to kick in.
    private static final int COLUMNS = 60;
    private static final int ROWS = 18;

    // Login is automated end-to-end (see maybeAutoLogin below) rather than
    // left for the user to type -- this is a single-user desktop VR app
    // talking to its own machine's OpenSSH server, not a shared system.
    private static final String SSH_HOST = "localhost";
    private static final String SSH_USER = "Guest";
    private static final String SSH_PASSWORD = "comein";

    private final VTScreen screen;
    private final TerminalPanel panel;
    private final PtyProcess process;
    private final OutputStream stdin;
    private final ConcurrentLinkedQueue<Character> pendingChars = new ConcurrentLinkedQueue<>();

    // update() only refreshes the panel's texture when the pty actually
    // produced output; a blinking caret needs a redraw purely on its own
    // phase flip too, even with no new bytes, or it would just freeze in
    // whatever phase it was last drawn in (see TextPanel.caretBlinkOn()).
    private boolean lastBlinkPhase = TextPanel.caretBlinkOn();

    // Rolling tail of raw incoming text, used only to notice the remote
    // "password:" prompt once; unrelated to what VTScreen renders.
    private final StringBuilder promptTail = new StringBuilder();
    private boolean passwordSent = false;

    public Terminal(float x, float y, float z, PanelTheme theme) {
        int fontSize = Math.min(fontSizeForColumns(COLUMNS), fontSizeForRows(ROWS));
        float width = widthForColumns(COLUMNS, fontSize);
        float height = heightForRows(ROWS, fontSize);

        screen = new VTScreen(ROWS, COLUMNS);
        TerminalContentAdapter content = new TerminalContentAdapter(screen);
        TerminalDecoration decoration = new TerminalDecoration(screen);
        panel = new TerminalPanel(x, y, z, theme, content, decoration, this, screen, width, height, fontSize);

        try {
            Map<String, String> env = new HashMap<>(System.getenv());
            env.put("TERM", "xterm-256color");
            PtyProcessBuilder builder = new PtyProcessBuilder()
                .setCommand(new String[] {
                    "ssh.exe", "-tt", "-o", "StrictHostKeyChecking=accept-new", "-o", "NumberOfPasswordPrompts=1",
                    SSH_USER + "@" + SSH_HOST
                })
                .setEnvironment(env)
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(COLUMNS)
                .setInitialRows(ROWS);
            process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start ssh under a pty", e);
        }
        stdin = process.getOutputStream();
        startReaderThread();
    }

    /** This terminal's single grid panel, usable both as scene content and as a CuboidFrame layout leaf. */
    public TerminalPanel panel() {
        return panel;
    }

    public void initialize() {
        panel.initialize();
    }

    /** Writes raw bytes to the pty's stdin, exactly as if a real keyboard had sent them. */
    void sendInput(String s) {
        try {
            stdin.write(s.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            // ssh has likely exited; nothing meaningful to do about a dead pty.
        }
    }

    /** Ctrl+V: inject the system clipboard's text as if it had been typed. */
    void sendClipboardText() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data instanceof String text) {
                sendInput(text.replace("\r\n", "\r").replace("\n", "\r"));
            }
        } catch (Exception e) {
            // Clipboard empty/not text/unavailable: nothing to paste.
        }
    }

    private void startReaderThread() {
        Thread thread = new Thread(() -> {
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                int ch;
                while ((ch = reader.read()) != -1) {
                    pendingChars.add((char) ch);
                }
            } catch (IOException e) {
                // pty closed / ssh exited: reader thread just ends.
            }
        }, "terminal-pty-reader");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Call once per frame on the render thread: moves whatever the pty
     * produced since the last frame onto the visible grid (VTScreen mutation
     * and the texture upload it drives must both happen on the GL thread).
     */
    public void update() {
        boolean changed = false;
        Character c;
        while ((c = pendingChars.poll()) != null) {
            screen.feed(c);
            maybeAutoLogin(c);
            changed = true;
        }

        boolean blinkPhase = TextPanel.caretBlinkOn();
        if (blinkPhase != lastBlinkPhase) {
            lastBlinkPhase = blinkPhase;
            changed = true;
        }

        if (changed) {
            panel.refresh();
        }
    }

    // Watches the raw incoming stream (not the parsed screen) for ssh's own
    // "...'s password:" prompt and answers it once, exactly like a user
    // typing the password themselves would -- the prompt line itself is
    // still visible on screen since it went through screen.feed() above too.
    private void maybeAutoLogin(char c) {
        if (passwordSent) {
            return;
        }
        promptTail.append(c);
        if (promptTail.length() > 32) {
            promptTail.delete(0, promptTail.length() - 32);
        }
        String tail = promptTail.toString().toLowerCase();
        if (tail.endsWith("password:") || tail.endsWith("password: ")) {
            passwordSent = true;
            sendInput(SSH_PASSWORD + "\r");
        }
    }

    /** Terminates the ssh process so closing the app doesn't leave it running. */
    public void destroy() {
        process.destroy();
    }

    // Largest font size (down from the panel default) at which `columns`
    // monospace characters still fit the fixed texture's width budget.
    private static int fontSizeForColumns(int columns) {
        int textAreaWidth = TextPanel.TEX_SIZE - 2 * TextPanel.PAD;
        int fontSize = TextPanel.BASE_FONT_SIZE;
        while (fontSize > 8) {
            FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
            if (fm.charWidth('0') * columns <= textAreaWidth) {
                break;
            }
            fontSize--;
        }
        return fontSize;
    }

    // Largest font size at which `rows` lines still fit the texture's height
    // budget, using the same per-row spacing TextPanel.renderPixels() adds.
    private static int fontSizeForRows(int rows) {
        int textAreaHeight = TextPanel.TEX_SIZE - 2 * TextPanel.PAD;
        int lineSpacing = 12;
        int fontSize = TextPanel.BASE_FONT_SIZE;
        while (fontSize > 8) {
            FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
            if ((fm.getAscent() + fm.getDescent() + lineSpacing) * rows <= textAreaHeight) {
                break;
            }
            fontSize--;
        }
        return fontSize;
    }

    // World-space panel width (meters) that exactly fits `columns` characters
    // at `fontSize`, at the same texels-per-meter density as a 1 m panel.
    private static float widthForColumns(int columns, int fontSize) {
        FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
        return (fm.charWidth('0') * columns + 2f * TextPanel.PAD) / (float) TextPanel.TEX_SIZE;
    }

    // World-space panel height (meters) that exactly fits `rows` lines at
    // `fontSize`, using the same per-row spacing fontSizeForRows() budgeted for.
    private static float heightForRows(int rows, int fontSize) {
        FontMetrics fm = TextPanel.scratchFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
        int lineSpacing = 12;
        return ((fm.getAscent() + fm.getDescent() + lineSpacing) * rows + 2f * TextPanel.PAD)
                / (float) TextPanel.TEX_SIZE;
    }
}
