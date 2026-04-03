package io.github.lnyocly.ai4j.tui.io;

import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiKeyStroke;
import io.github.lnyocly.ai4j.tui.TuiKeyType;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DefaultJlineTerminalIO implements TerminalIO {

    private static final long ESCAPE_SEQUENCE_TIMEOUT_MS = 25L;

    private final Terminal terminal;
    private final LineReader lineReader;
    private final NonBlockingReader reader;
    private final PrintWriter out;
    private final PrintWriter err;
    private Attributes originalAttributes;
    private boolean rawMode;
    private boolean closed;
    private boolean inputClosed;
    private Integer pendingChar;

    protected DefaultJlineTerminalIO(Terminal terminal, OutputStream errStream) {
        this.terminal = terminal;
        this.lineReader = LineReaderBuilder.builder().terminal(terminal).build();
        this.reader = terminal.reader();
        this.out = terminal.writer();
        Charset charset = terminal.encoding() == null ? StandardCharsets.UTF_8 : terminal.encoding();
        this.err = new PrintWriter(new OutputStreamWriter(errStream, charset), true);
    }

    public static DefaultJlineTerminalIO openSystem(OutputStream errStream) throws IOException {
        Charset charset = StreamsTerminalIO.resolveTerminalCharset();
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .encoding(charset)
                .build();
        return new DefaultJlineTerminalIO(terminal, errStream);
    }

    @Override
    public String readLine(String prompt) throws IOException {
        restoreRawMode();
        print(prompt == null ? "" : prompt);
        StringBuilder builder = new StringBuilder();
        while (true) {
            int ch;
            try {
                ch = readChar();
            } catch (EndOfFileException ex) {
                inputClosed = true;
                return builder.length() == 0 ? null : builder.toString();
            } catch (UserInterruptException ex) {
                return "";
            }
            if (ch < 0) {
                inputClosed = true;
                return builder.length() == 0 ? null : builder.toString();
            }
            if (ch == '\n') {
                return builder.toString();
            }
            if (ch == '\r') {
                int next = readChar(5L);
                if (next >= 0 && next != '\n') {
                    unreadChar(next);
                }
                return builder.toString();
            }
            builder.append((char) ch);
        }
    }

    @Override
    public synchronized TuiKeyStroke readKeyStroke() throws IOException {
        ensureRawMode();
        int ch = readChar();
        return toKeyStroke(ch);
    }

    @Override
    public synchronized TuiKeyStroke readKeyStroke(long timeoutMs) throws IOException {
        ensureRawMode();
        int ch = timeoutMs <= 0L ? readChar() : readChar(timeoutMs);
        if (ch == NonBlockingReader.READ_EXPIRED) {
            return null;
        }
        return toKeyStroke(ch);
    }

    @Override
    public void print(String message) {
        out.print(message == null ? "" : message);
        out.flush();
        terminal.flush();
    }

    @Override
    public void println(String message) {
        out.println(message == null ? "" : message);
        out.flush();
        terminal.flush();
    }

    @Override
    public void errorln(String message) {
        err.println(message == null ? "" : message);
        err.flush();
    }

    @Override
    public boolean supportsAnsi() {
        return terminal != null && !"dumb".equalsIgnoreCase(terminal.getType());
    }

    @Override
    public boolean supportsRawInput() {
        return terminal != null;
    }

    @Override
    public synchronized boolean isInputClosed() {
        return inputClosed;
    }

    @Override
    public void clearScreen() {
        puts(InfoCmp.Capability.clear_screen);
    }

    @Override
    public void enterAlternateScreen() {
        puts(InfoCmp.Capability.enter_ca_mode);
    }

    @Override
    public void exitAlternateScreen() {
        puts(InfoCmp.Capability.exit_ca_mode);
    }

    @Override
    public void hideCursor() {
        puts(InfoCmp.Capability.cursor_invisible);
    }

    @Override
    public void showCursor() {
        puts(InfoCmp.Capability.cursor_normal);
    }

    @Override
    public void moveCursorHome() {
        puts(InfoCmp.Capability.cursor_home);
    }

    @Override
    public int getTerminalRows() {
        return terminal == null ? 0 : Math.max(0, terminal.getHeight());
    }

    @Override
    public int getTerminalColumns() {
        return terminal == null ? 0 : Math.max(0, terminal.getWidth());
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        restoreRawMode();
        terminal.close();
        closed = true;
    }

    private TuiKeyStroke toKeyStroke(int ch) throws IOException {
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

    private TuiKeyStroke readEscapeSequence() throws IOException {
        int next = readChar(ESCAPE_SEQUENCE_TIMEOUT_MS);
        if (next == NonBlockingReader.READ_EXPIRED || next < 0) {
            return TuiKeyStroke.of(TuiKeyType.ESCAPE);
        }
        if (next != '[' && next != 'O') {
            unreadChar(next);
            return TuiKeyStroke.of(TuiKeyType.ESCAPE);
        }
        int code = readChar(ESCAPE_SEQUENCE_TIMEOUT_MS);
        if (code == NonBlockingReader.READ_EXPIRED || code < 0) {
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

    private void ensureRawMode() {
        if (rawMode || terminal == null) {
            return;
        }
        originalAttributes = terminal.enterRawMode();
        rawMode = true;
    }

    private void restoreRawMode() {
        if (!rawMode || terminal == null || originalAttributes == null) {
            return;
        }
        terminal.setAttributes(originalAttributes);
        rawMode = false;
    }

    private void puts(InfoCmp.Capability capability) {
        if (!supportsAnsi() || capability == null) {
            return;
        }
        terminal.puts(capability);
        terminal.flush();
    }

    private int readChar() throws IOException {
        if (pendingChar != null) {
            int value = pendingChar.intValue();
            pendingChar = null;
            return value;
        }
        return reader.read();
    }

    private int readChar(long timeoutMs) throws IOException {
        if (pendingChar != null) {
            int value = pendingChar.intValue();
            pendingChar = null;
            return value;
        }
        return reader.read(timeoutMs);
    }

    private void unreadChar(int ch) {
        if (ch >= 0) {
            pendingChar = Integer.valueOf(ch);
        }
    }
}
