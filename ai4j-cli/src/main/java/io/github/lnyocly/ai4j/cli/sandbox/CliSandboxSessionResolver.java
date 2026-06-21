package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;

/**
 * Resolves a CLI sandbox binding into a runnable sandbox session.
 */
public interface CliSandboxSessionResolver {

    SandboxSession resolve(CliSandboxBinding binding) throws SandboxException;
}
