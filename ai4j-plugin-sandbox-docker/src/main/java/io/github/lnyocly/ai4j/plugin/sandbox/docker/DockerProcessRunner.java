package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import java.io.IOException;
import java.util.List;

/**
 * Injectable process runner abstraction for executing Docker CLI commands.
 * Enables deterministic offline testing without requiring a live Docker daemon.
 */
public interface DockerProcessRunner {

    DockerProcessOutput run(List<String> command, String stdin, long timeoutMillis) throws IOException;
}
