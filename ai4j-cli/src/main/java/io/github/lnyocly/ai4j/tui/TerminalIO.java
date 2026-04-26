package io.github.lnyocly.ai4j.tui;

import java.io.IOException;

public interface TerminalIO {

    String readLine(String prompt) throws IOException;

    default TuiKeyStroke readKeyStroke() throws IOException {
        return null;
    }

    default TuiKeyStroke readKeyStroke(long timeoutMs) throws IOException {
        return readKeyStroke();
    }

    void print(String message);

    void println(String message);

    void errorln(String message);

    default boolean supportsAnsi() {
        return false;
    }

    default boolean supportsRawInput() {
        return false;
    }

    default boolean isInputClosed() {
        return false;
    }

    default void clearScreen() {
    }

    default void enterAlternateScreen() {
    }

    default void exitAlternateScreen() {
    }

    default void hideCursor() {
    }

    default void showCursor() {
    }

    default void moveCursorHome() {
    }

    default int getTerminalRows() {
        return 0;
    }

    default int getTerminalColumns() {
        return 0;
    }

    default void close() throws IOException {
    }
}

