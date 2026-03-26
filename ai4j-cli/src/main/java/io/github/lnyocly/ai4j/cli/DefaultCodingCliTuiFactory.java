package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.tui.AnsiTuiRuntime;
import io.github.lnyocly.ai4j.tui.AppendOnlyTuiRuntime;
import io.github.lnyocly.ai4j.tui.JlineTerminalIO;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiConfig;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;
import io.github.lnyocly.ai4j.tui.TuiRenderer;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiSessionView;
import io.github.lnyocly.ai4j.tui.TuiTheme;

public class DefaultCodingCliTuiFactory implements CodingCliTuiFactory {

    @Override
    public CodingCliTuiSupport create(CodeCommandOptions options,
                                      TerminalIO terminal,
                                      TuiConfigManager configManager) {
        TuiConfig config = configManager == null ? new TuiConfig() : configManager.load(options == null ? null : options.getTheme());
        TuiTheme theme = configManager == null ? new TuiTheme() : configManager.resolveTheme(config.getTheme());
        TuiRenderer renderer = new TuiSessionView(config, theme, terminal != null && terminal.supportsAnsi());
        boolean useAlternateScreen = config != null && config.isUseAlternateScreen();
        TuiRuntime runtime = !useAlternateScreen && terminal instanceof JlineTerminalIO
                ? new AppendOnlyTuiRuntime(terminal)
                : new AnsiTuiRuntime(terminal, renderer, useAlternateScreen);
        return new CodingCliTuiSupport(config, theme, renderer, runtime);
    }
}
