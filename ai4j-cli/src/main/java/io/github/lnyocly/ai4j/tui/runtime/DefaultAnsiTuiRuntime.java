package io.github.lnyocly.ai4j.tui.runtime;

import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiKeyStroke;
import io.github.lnyocly.ai4j.tui.TuiRenderer;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiScreenModel;
import java.io.IOException;

public class DefaultAnsiTuiRuntime implements TuiRuntime {

    private final TerminalIO terminal;
    private final TuiRenderer renderer;
    private final boolean useAlternateScreen;
    private String lastFrame;

    public DefaultAnsiTuiRuntime(TerminalIO terminal, TuiRenderer renderer) {
        this(terminal, renderer, true);
    }

    public DefaultAnsiTuiRuntime(TerminalIO terminal, TuiRenderer renderer, boolean useAlternateScreen) {
        this.terminal = terminal;
        this.renderer = renderer;
        this.useAlternateScreen = useAlternateScreen;
    }

    @Override
    public boolean supportsRawInput() {
        return terminal != null && terminal.supportsRawInput();
    }

    @Override
    public void enter() {
        if (terminal == null) {
            return;
        }
        lastFrame = null;
        if (useAlternateScreen) {
            terminal.enterAlternateScreen();
        }
        terminal.hideCursor();
    }

    @Override
    public void exit() {
        if (terminal == null) {
            return;
        }
        lastFrame = null;
        terminal.showCursor();
        if (useAlternateScreen) {
            terminal.exitAlternateScreen();
        }
    }

    @Override
    public TuiKeyStroke readKeyStroke(long timeoutMs) throws IOException {
        return terminal == null ? null : terminal.readKeyStroke(timeoutMs);
    }

    @Override
    public synchronized void render(TuiScreenModel screenModel) {
        if (terminal == null || renderer == null) {
            return;
        }
        String frame = renderer.render(screenModel);
        if (frame == null) {
            frame = "";
        }
        if (frame.equals(lastFrame)) {
            return;
        }
        lastFrame = frame;
        if (useAlternateScreen) {
            terminal.clearScreen();
            terminal.print(frame);
            return;
        }
        terminal.moveCursorHome();
        terminal.print(frame);
        if (terminal.supportsAnsi()) {
            terminal.print("\u001b[J");
        }
    }
}
