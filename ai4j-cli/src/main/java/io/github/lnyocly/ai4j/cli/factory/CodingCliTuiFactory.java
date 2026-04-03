package io.github.lnyocly.ai4j.cli.factory;

import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.runtime.CodingCliTuiSupport;

import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;

public interface CodingCliTuiFactory {

    CodingCliTuiSupport create(CodeCommandOptions options,
                               TerminalIO terminal,
                               TuiConfigManager configManager);
}

