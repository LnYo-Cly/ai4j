package io.github.lnyocly.ai4j.extension.command;

public interface ExtensionCommandHandler {

    String handle(ExtensionCommandRequest request) throws Exception;
}
