package io.github.lnyocly.ai4j.coding.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;

/**
 * Runtime handle for a live sandbox bound to one coding-agent session.
 *
 * <p>This object intentionally carries the live {@link SandboxSession}; the
 * persisted AgentSession sandbox binding remains a non-sensitive summary owned
 * by {@code ai4j-agent}.</p>
 */
public final class CodingSandboxRuntime {

    private final SandboxSession sandboxSession;

    private CodingSandboxRuntime(SandboxSession sandboxSession) {
        if (sandboxSession == null) {
            throw new IllegalArgumentException("sandboxSession is required");
        }
        this.sandboxSession = sandboxSession;
    }

    public static CodingSandboxRuntime of(SandboxSession sandboxSession) {
        return new CodingSandboxRuntime(sandboxSession);
    }

    public SandboxSession getSandboxSession() {
        return sandboxSession;
    }
}
