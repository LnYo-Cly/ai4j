package io.github.lnyocly.ai4j.tui.io;

import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiKeyStroke;
import io.github.lnyocly.ai4j.tui.TuiKeyType;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PushbackReader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DefaultStreamsTerminalIO implements TerminalIO {

    private static final String TERMINAL_ENCODING_PROPERTY = "ai4j.terminal.encoding";
    private static final String TERMINAL_ENCODING_ENV = "AI4J_TERMINAL_ENCODING";
    private static final String ANSI_CLEAR = "\u001b[2J\u001b[H";
    private static final String ANSI_ALT_SCREEN_ON = "\u001b[?1049h";
    private static final String ANSI_ALT_SCREEN_OFF = "\u001b[?1049l";
    private static final String ANSI_CURSOR_HIDE = "\u001b[?25l";
    private static final String ANSI_CURSOR_SHOW = "\u001b[?25h";
    private static final String ANSI_CURSOR_HOME = "\u001b[H";

    private final InputStream inputStream;
    private final PushbackReader reader;
    private final PrintWriter out;
    private final PrintWriter err;
    private final boolean ansiSupported;
    private boolean inputClosed;

    public DefaultStreamsTerminalIO(InputStream in, OutputStream out, OutputStream err) {
        this(in, out, err, resolveTerminalCharset(), detectAnsiSupport());
    }

    protected DefaultStreamsTerminalIO(InputStream in, OutputStream out, OutputStream err, boolean ansiSupported) {
        this(in, out, err, resolveTerminalCharset(), ansiSupported);
    }

    protected DefaultStreamsTerminalIO(InputStream in,
                      OutputStream out,
                      OutputStream err,
                      Charset charset,
                      boolean ansiSupported) {
        this.inputStream = in;
        Charset ioCharset = charset == null ? resolveTerminalCharset() : charset;
        this.reader = new PushbackReader(new InputStreamReader(in, ioCharset), 8);
        this.out = new PrintWriter(new OutputStreamWriter(out, ioCharset), true);
        this.err = new PrintWriter(new OutputStreamWriter(err, ioCharset), true);
        this.ansiSupported = ansiSupported;
    }

    @Override
    public synchronized String readLine(String prompt) throws IOException {
        print(prompt);
        StringBuilder builder = new StringBuilder();
        while (true) {
            int ch = reader.read();
            if (ch < 0) {
                inputClosed = true;
                return builder.length() == 0 ? null : builder.toString();
            }
            if (ch == '\n') {
                return builder.toString();
            }
            if (ch == '\r') {
                int next = reader.read();
                if (next >= 0 && next != '\n') {
                    reader.unread(next);
                }
                return builder.toString();
            }
            builder.append((char) ch);
        }
    }

    @Override
    public synchronized TuiKeyStroke readKeyStroke() throws IOException {
        int ch = reader.read();
        if (ch < 0) {
            inputClosed = true;
            return null;
        }
        switch (ch) {
            case '\r':
            case '\n':
                return TuiKeyStroke.of(TuiKeyType.ENTER);
            case '\t':
                return TuiKeyStroke.of(TuiKeyType.TAB);
            case '\b':
            case 127:
                return TuiKeyStroke.of(TuiKeyType.BACKSPACE);
            case 12:
                return TuiKeyStroke.of(TuiKeyType.CTRL_L);
            case 16:
                return TuiKeyStroke.of(TuiKeyType.CTRL_P);
            case 18:
                return TuiKeyStroke.of(TuiKeyType.CTRL_R);
            case 27:
                return readEscapeSequence();
            default:
                if (Character.isISOControl((char) ch)) {
                    return TuiKeyStroke.of(TuiKeyType.UNKNOWN);
                }
                return TuiKeyStroke.character(String.valueOf((char) ch));
        }
    }

    @Override
    public synchronized TuiKeyStroke readKeyStroke(long timeoutMs) throws IOException {
        if (inputStream != System.in) {
            return readKeyStroke();
        }
        if (timeoutMs <= 0L) {
            return readKeyStroke();
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (hasBufferedInput()) {
                return readKeyStroke();
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return hasBufferedInput() ? readKeyStroke() : null;
    }

    @Override
    public void print(String message) {
        out.print(message == null ? "" : message);
        out.flush();
    }

    @Override
    public void println(String message) {
        out.println(message == null ? "" : message);
        out.flush();
    }

    @Override
    public void errorln(String message) {
        err.println(message == null ? "" : message);
        err.flush();
    }

    @Override
    public boolean supportsAnsi() {
        return ansiSupported;
    }

    @Override
    public boolean supportsRawInput() {
        return inputStream != System.in;
    }

    @Override
    public synchronized boolean isInputClosed() {
        return inputClosed;
    }

    @Override
    public void clearScreen() {
        if (!ansiSupported) {
            return;
        }
        out.print(ANSI_CLEAR);
        out.flush();
    }

    @Override
    public void enterAlternateScreen() {
        emitAnsi(ANSI_ALT_SCREEN_ON);
    }

    @Override
    public void exitAlternateScreen() {
        emitAnsi(ANSI_ALT_SCREEN_OFF);
    }

    @Override
    public void hideCursor() {
        emitAnsi(ANSI_CURSOR_HIDE);
    }

    @Override
    public void showCursor() {
        emitAnsi(ANSI_CURSOR_SHOW);
    }

    @Override
    public void moveCursorHome() {
        emitAnsi(ANSI_CURSOR_HOME);
    }

    @Override
    public int getTerminalRows() {
        return parsePositiveInt(System.getenv("LINES"), 24);
    }

    @Override
    public int getTerminalColumns() {
        return parsePositiveInt(System.getenv("COLUMNS"), 80);
    }

    private TuiKeyStroke readEscapeSequence() throws IOException {
        int next = reader.read();
        if (next < 0) {
            return TuiKeyStroke.of(TuiKeyType.ESCAPE);
        }
        if (next != '[' && next != 'O') {
            reader.unread(next);
            return TuiKeyStroke.of(TuiKeyType.ESCAPE);
        }
        int code = reader.read();
        if (code < 0) {
            return TuiKeyStroke.of(TuiKeyType.ESCAPE);
        }
        switch (code) {
            case 'A':
                return TuiKeyStroke.of(TuiKeyType.ARROW_UP);
            case 'B':
                return TuiKeyStroke.of(TuiKeyType.ARROW_DOWN);
            case 'C':
                return TuiKeyStroke.of(TuiKeyType.ARROW_RIGHT);
            case 'D':
                return TuiKeyStroke.of(TuiKeyType.ARROW_LEFT);
            default:
                return TuiKeyStroke.of(TuiKeyType.ESCAPE);
        }
    }

    private void emitAnsi(String value) {
        if (!ansiSupported) {
            return;
        }
        out.print(value);
        out.flush();
    }

    private boolean hasBufferedInput() throws IOException {
        return reader.ready() || (inputStream != null && inputStream.available() > 0);
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static boolean detectAnsiSupport() {
        if (System.console() == null) {
            return false;
        }
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return true;
        }
        return System.getenv("WT_SESSION") != null
                || System.getenv("ANSICON") != null
                || "ON".equalsIgnoreCase(System.getenv("ConEmuANSI"))
                || hasTermSupport(System.getenv("TERM"));
    }

    private static boolean hasTermSupport(String term) {
        return term != null && term.toLowerCase().contains("xterm");
    }

    public static Charset resolveTerminalCharset() {
        return resolveTerminalCharset(
                new String[]{
                        System.getProperty(TERMINAL_ENCODING_PROPERTY),
                        System.getenv(TERMINAL_ENCODING_ENV)
                },
                new String[]{
                        System.getProperty("stdin.encoding"),
                        System.getProperty("sun.stdin.encoding"),
                        System.getProperty("stdout.encoding"),
                        System.getProperty("sun.stdout.encoding"),
                        consoleCharsetName()
                },
                new String[]{
                        System.getProperty("native.encoding"),
                        System.getProperty("sun.jnu.encoding"),
                        System.getProperty("file.encoding")
                },
                shouldPreferUtf8()
        );
    }

    static Charset resolveTerminalCharset(String[] explicitCandidates,
                                          String[] ioCandidates,
                                          String[] platformCandidates,
                                          boolean preferUtf8) {
        Charset explicit = firstSupportedCharset(explicitCandidates);
        if (explicit != null) {
            return explicit;
        }
        Charset io = firstSupportedCharset(ioCandidates);
        if (io != null) {
            return io;
        }
        if (preferUtf8) {
            return StandardCharsets.UTF_8;
        }
        Charset platform = firstSupportedCharset(platformCandidates);
        if (platform != null) {
            return platform;
        }
        return Charset.defaultCharset();
    }

    private static Charset firstSupportedCharset(String[] candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            Charset resolved = toCharset(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static boolean shouldPreferUtf8() {
        return hasUtf8Locale(System.getenv("LC_ALL"))
                || hasUtf8Locale(System.getenv("LC_CTYPE"))
                || hasUtf8Locale(System.getenv("LANG"))
                || System.getenv("WT_SESSION") != null
                || hasTermSupport(System.getenv("TERM"));
    }

    private static boolean hasUtf8Locale(String value) {
        return value != null && value.toUpperCase().contains("UTF-8");
    }

    private static String consoleCharsetName() {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        try {
            Method method = console.getClass().getMethod("charset");
            Object value = method.invoke(console);
            if (value instanceof Charset) {
                return ((Charset) value).name();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Charset toCharset(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Charset.forName(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

