package io.github.lnyocly.ai4j.extension.tool;

public interface ExtensionToolExecutor {

    String execute(ExtensionToolCall call) throws Exception;
}
