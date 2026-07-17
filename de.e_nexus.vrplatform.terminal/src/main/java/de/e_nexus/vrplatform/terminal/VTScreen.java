package de.e_nexus.vrplatform.terminal;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A fixed-size character grid driven incrementally by raw bytes from a real
 * terminal program (ssh, a remote shell, {@code claude}, ...), the same way
 * an xterm-family emulator drives its own screen: printable characters
 * advance the cursor, control characters and a common subset of ANSI/VT100
 * CSI escape sequences (cursor movement, erase, SGR color/bold) update it in
 * place. Only ever touched from the render thread (see {@code Terminal.update()}),
 * so no synchronization is needed here.
 */
public class VTScreen {

    public static final Color DEFAULT_FG = new Color(0.85f, 0.85f, 0.85f);

    private static final Color[] ANSI = {
        new Color(0.10f, 0.10f, 0.10f), // black
        new Color(0.80f, 0.20f, 0.20f), // red
        new Color(0.20f, 0.75f, 0.20f), // green
        new Color(0.80f, 0.75f, 0.15f), // yellow
        new Color(0.25f, 0.45f, 0.90f), // blue
        new Color(0.75f, 0.25f, 0.75f), // magenta
        new Color(0.20f, 0.75f, 0.80f), // cyan
        new Color(0.80f, 0.80f, 0.80f), // white
    };

    private static final Color[] ANSI_BRIGHT = {
        new Color(0.40f, 0.40f, 0.40f),
        new Color(1.00f, 0.35f, 0.35f),
        new Color(0.35f, 1.00f, 0.35f),
        new Color(1.00f, 0.95f, 0.35f),
        new Color(0.45f, 0.60f, 1.00f),
        new Color(1.00f, 0.45f, 1.00f),
        new Color(0.40f, 1.00f, 1.00f),
        new Color(1.00f, 1.00f, 1.00f),
    };

    private enum State { NORMAL, ESC, ESC_SKIP_ONE, CSI, OSC, OSC_ESC }

    private final int rows;
    private final int cols;
    private final char[][] grid;
    private final Color[][] fg;
    private final boolean[][] bold;

    private int cursorRow = 0;
    private int cursorCol = 0;
    private boolean cursorVisible = true;

    private int savedRow = 0;
    private int savedCol = 0;

    private Color currentFg = DEFAULT_FG;
    private boolean currentBold = false;

    private State state = State.NORMAL;
    private final StringBuilder csiParams = new StringBuilder();

    public VTScreen(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.grid = new char[rows][cols];
        this.fg = new Color[rows][cols];
        this.bold = new boolean[rows][cols];
        clearAll();
    }

    public int cursorRow() {
        return cursorRow;
    }

    public int cursorCol() {
        return cursorCol;
    }

    public boolean isCursorVisible() {
        return cursorVisible;
    }

    public char charAt(int row, int col) {
        return grid[row][col];
    }

    public Color foreground(int row, int col) {
        return fg[row][col];
    }

    public boolean isBold(int row, int col) {
        return bold[row][col];
    }

    /** One string per row, trailing blanks trimmed -- what {@link TerminalContentAdapter#lines()} shows. */
    public List<String> lines() {
        List<String> result = new ArrayList<>(rows);
        for (int r = 0; r < rows; r++) {
            int end = cols;
            while (end > 0 && grid[r][end - 1] == ' ') {
                end--;
            }
            result.add(new String(grid[r], 0, end));
        }
        return result;
    }

    /** Feed one decoded character of the program's raw output through the emulator. */
    public void feed(char c) {
        switch (state) {
            case NORMAL -> {
                if (c == 0x1B) {
                    state = State.ESC;
                } else {
                    handleControlOrPrintable(c);
                }
            }
            case ESC -> {
                if (c == '[') {
                    csiParams.setLength(0);
                    state = State.CSI;
                } else if (c == ']') {
                    state = State.OSC;
                } else if (c == '(' || c == ')' || c == '#' || c == '%') {
                    state = State.ESC_SKIP_ONE;
                } else if (c == '7') {
                    savedRow = cursorRow;
                    savedCol = cursorCol;
                    state = State.NORMAL;
                } else if (c == '8') {
                    cursorRow = savedRow;
                    cursorCol = savedCol;
                    state = State.NORMAL;
                } else if (c == 'c') {
                    clearAll();
                    state = State.NORMAL;
                } else {
                    state = State.NORMAL;
                }
            }
            case ESC_SKIP_ONE -> state = State.NORMAL;
            case CSI -> {
                if ((c >= '0' && c <= '9') || c == ';' || c == '?' || c == '=' || c == '<' || c == '>') {
                    csiParams.append(c);
                } else {
                    dispatchCsi(c, csiParams.toString());
                    state = State.NORMAL;
                }
            }
            case OSC -> {
                if (c == 0x07) {
                    state = State.NORMAL;
                } else if (c == 0x1B) {
                    state = State.OSC_ESC;
                }
            }
            case OSC_ESC -> state = (c == '\\') ? State.NORMAL : State.OSC;
        }
    }

    private void handleControlOrPrintable(char c) {
        if (c == '\r') {
            cursorCol = 0;
        } else if (c == '\n') {
            lineFeed();
        } else if (c == '\b') {
            if (cursorCol > 0) {
                cursorCol--;
            }
        } else if (c == '\t') {
            cursorCol = Math.min(cols - 1, ((cursorCol / 8) + 1) * 8);
        } else if (c >= 0x20) {
            writePrintable(c);
        }
        // other control bytes (bell, etc.) are silently ignored
    }

    private void writePrintable(char c) {
        grid[cursorRow][cursorCol] = c;
        fg[cursorRow][cursorCol] = currentFg;
        bold[cursorRow][cursorCol] = currentBold;
        cursorCol++;
        if (cursorCol >= cols) {
            cursorCol = 0;
            lineFeed();
        }
    }

    private void lineFeed() {
        cursorRow++;
        if (cursorRow >= rows) {
            scrollUp();
            cursorRow = rows - 1;
        }
    }

    private void scrollUp() {
        for (int r = 0; r < rows - 1; r++) {
            grid[r] = grid[r + 1];
            fg[r] = fg[r + 1];
            bold[r] = bold[r + 1];
        }
        grid[rows - 1] = new char[cols];
        fg[rows - 1] = new Color[cols];
        bold[rows - 1] = new boolean[cols];
        clearRow(rows - 1, 0, cols);
    }

    private void clearAll() {
        for (int r = 0; r < rows; r++) {
            clearRow(r, 0, cols);
        }
        cursorRow = 0;
        cursorCol = 0;
        currentFg = DEFAULT_FG;
        currentBold = false;
        cursorVisible = true;
    }

    private void clearRow(int row, int fromCol, int toColExclusive) {
        for (int c = fromCol; c < toColExclusive; c++) {
            grid[row][c] = ' ';
            fg[row][c] = DEFAULT_FG;
            bold[row][c] = false;
        }
    }

    private void dispatchCsi(char finalByte, String rawParams) {
        boolean privateMode = rawParams.startsWith("?");
        String digits = privateMode ? rawParams.substring(1) : rawParams;
        int[] params = parseParams(digits);

        switch (finalByte) {
            case 'A' -> cursorRow = Math.max(0, cursorRow - moveAmount(params));
            case 'B' -> cursorRow = Math.min(rows - 1, cursorRow + moveAmount(params));
            case 'C' -> cursorCol = Math.min(cols - 1, cursorCol + moveAmount(params));
            case 'D' -> cursorCol = Math.max(0, cursorCol - moveAmount(params));
            case 'G' -> cursorCol = clamp(moveAmount(params) - 1, 0, cols - 1);
            case 'd' -> cursorRow = clamp(moveAmount(params) - 1, 0, rows - 1);
            case 'H', 'f' -> {
                int r = params.length > 0 && params[0] > 0 ? params[0] : 1;
                int c = params.length > 1 && params[1] > 0 ? params[1] : 1;
                cursorRow = clamp(r - 1, 0, rows - 1);
                cursorCol = clamp(c - 1, 0, cols - 1);
            }
            case 'J' -> eraseDisplay(params.length > 0 ? params[0] : 0);
            case 'K' -> eraseLine(params.length > 0 ? params[0] : 0);
            case 'm' -> applySgr(params);
            case 'h', 'l' -> {
                if (privateMode) {
                    for (int p : params) {
                        if (p == 25) {
                            cursorVisible = (finalByte == 'h');
                        }
                    }
                }
            }
            default -> { /* unsupported final byte: ignore */ }
        }
    }

    private static int moveAmount(int[] params) {
        return (params.length > 0 && params[0] > 0) ? params[0] : 1;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int[] parseParams(String digits) {
        if (digits.isEmpty()) {
            return new int[0];
        }
        String[] parts = digits.split(";", -1);
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private void eraseDisplay(int mode) {
        if (mode == 0) {
            clearRow(cursorRow, cursorCol, cols);
            for (int r = cursorRow + 1; r < rows; r++) {
                clearRow(r, 0, cols);
            }
        } else if (mode == 1) {
            for (int r = 0; r < cursorRow; r++) {
                clearRow(r, 0, cols);
            }
            clearRow(cursorRow, 0, cursorCol + 1);
        } else {
            for (int r = 0; r < rows; r++) {
                clearRow(r, 0, cols);
            }
        }
    }

    private void eraseLine(int mode) {
        if (mode == 0) {
            clearRow(cursorRow, cursorCol, cols);
        } else if (mode == 1) {
            clearRow(cursorRow, 0, cursorCol + 1);
        } else {
            clearRow(cursorRow, 0, cols);
        }
    }

    private void applySgr(int[] params) {
        if (params.length == 0) {
            currentFg = DEFAULT_FG;
            currentBold = false;
            return;
        }
        for (int i = 0; i < params.length; i++) {
            int p = params[i];
            if (p == 0) {
                currentFg = DEFAULT_FG;
                currentBold = false;
            } else if (p == 1) {
                currentBold = true;
            } else if (p == 22) {
                currentBold = false;
            } else if (p >= 30 && p <= 37) {
                currentFg = ANSI[p - 30];
            } else if (p == 38) {
                if (i + 2 < params.length && params[i + 1] == 5) {
                    currentFg = palette256(params[i + 2]);
                    i += 2;
                } else if (i + 4 < params.length && params[i + 1] == 2) {
                    currentFg = new Color(clampByte(params[i + 2]), clampByte(params[i + 3]), clampByte(params[i + 4]));
                    i += 4;
                }
            } else if (p == 39) {
                currentFg = DEFAULT_FG;
            } else if (p >= 90 && p <= 97) {
                currentFg = ANSI_BRIGHT[p - 90];
            }
            // background (40-49, 100-107) and other SGR codes: no per-cell
            // background rendering exists in TextPanel, so intentionally ignored.
        }
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static Color palette256(int idx) {
        if (idx < 8) {
            return ANSI[idx];
        }
        if (idx < 16) {
            return ANSI_BRIGHT[idx - 8];
        }
        if (idx < 232) {
            int i = idx - 16;
            int r = i / 36;
            int g = (i / 6) % 6;
            int b = i % 6;
            return new Color(cubeLevel(r), cubeLevel(g), cubeLevel(b));
        }
        int gray = 8 + (idx - 232) * 10;
        return new Color(clampByte(gray), clampByte(gray), clampByte(gray));
    }

    private static int cubeLevel(int n) {
        return n == 0 ? 0 : 55 + n * 40;
    }
}
