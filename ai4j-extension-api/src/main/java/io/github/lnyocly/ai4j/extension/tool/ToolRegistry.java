package io.github.lnyocly.ai4j.extension.tool;

public interface ToolRegistry {

    void register(ExtensionToolSpec spec, ExtensionToolExecutor executor);
}
