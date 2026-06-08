package io.github.lnyocly.ai4j.extension.command;

public interface CommandRegistry {

    void register(ExtensionCommandSpec spec, ExtensionCommandHandler handler);
}
