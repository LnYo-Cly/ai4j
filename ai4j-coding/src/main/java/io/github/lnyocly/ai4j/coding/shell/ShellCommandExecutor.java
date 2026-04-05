package io.github.lnyocly.ai4j.coding.shell;

public interface ShellCommandExecutor {

    ShellCommandResult execute(ShellCommandRequest request) throws Exception;
}
