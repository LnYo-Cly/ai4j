package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.tui.runtime.DefaultAnsiTuiRuntime;

public class AnsiTuiRuntime extends DefaultAnsiTuiRuntime {

    public AnsiTuiRuntime(TerminalIO terminal, TuiRenderer renderer) {
        super(terminal, renderer);
    }

    public AnsiTuiRuntime(TerminalIO terminal, TuiRenderer renderer, boolean useAlternateScreen) {
        super(terminal, renderer, useAlternateScreen);
    }
}
