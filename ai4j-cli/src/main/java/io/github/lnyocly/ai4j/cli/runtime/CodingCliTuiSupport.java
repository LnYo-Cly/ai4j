package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.tui.TuiConfig;
import io.github.lnyocly.ai4j.tui.TuiRenderer;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiTheme;

public class CodingCliTuiSupport {

    private final TuiConfig config;
    private final TuiTheme theme;
    private final TuiRenderer renderer;
    private final TuiRuntime runtime;

    public CodingCliTuiSupport(TuiConfig config,
                               TuiTheme theme,
                               TuiRenderer renderer,
                               TuiRuntime runtime) {
        this.config = config;
        this.theme = theme;
        this.renderer = renderer;
        this.runtime = runtime;
    }

    public TuiConfig getConfig() {
        return config;
    }

    public TuiTheme getTheme() {
        return theme;
    }

    public TuiRenderer getRenderer() {
        return renderer;
    }

    public TuiRuntime getRuntime() {
        return runtime;
    }
}
