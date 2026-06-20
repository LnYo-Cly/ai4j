package io.github.lnyocly.ai4j.coding.shell;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

/**
 * Executes foreground shell commands through a live SandboxSession.
 */
public class SandboxShellCommandExecutor implements ShellCommandExecutor {

    private final WorkspaceContext workspaceContext;
    private final SandboxSession sandboxSession;
    private final long defaultTimeoutMs;

    public SandboxShellCommandExecutor(WorkspaceContext workspaceContext,
                                       SandboxSession sandboxSession,
                                       long defaultTimeoutMs) {
        if (sandboxSession == null) {
            throw new IllegalArgumentException("sandboxSession is required");
        }
        this.workspaceContext = workspaceContext;
        this.sandboxSession = sandboxSession;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    @Override
    public ShellCommandResult execute(ShellCommandRequest request) throws Exception {
        if (request == null || isBlank(request.getCommand())) {
            throw new IllegalArgumentException("command is required");
        }
        Long timeoutMs = request.getTimeoutMs() == null || request.getTimeoutMs().longValue() <= 0
                ? Long.valueOf(defaultTimeoutMs)
                : request.getTimeoutMs();
        String workingDirectory = resolveWorkingDirectory(request.getWorkingDirectory());
        SandboxResult result = sandboxSession.execute(SandboxCommand.builder()
                .command(request.getCommand())
                .workingDirectory(workingDirectory)
                .timeoutMillis(timeoutMs)
                .metadata("tool", "bash")
                .metadata("routing", "ai4j-coding")
                .build());
        return ShellCommandResult.builder()
                .command(request.getCommand())
                .workingDirectory(workingDirectory)
                .executionEnvironment("sandbox")
                .sandboxSessionId(sandboxSession.getSessionId())
                .sandboxProviderId(sandboxSession.getProviderId())
                .stdout(result == null ? null : result.getStdout())
                .stderr(result == null ? null : result.getStderr())
                .exitCode(result == null || result.getExitCode() == null ? 0 : result.getExitCode().intValue())
                .timedOut(result != null && result.isTimedOut())
                .build();
    }

    private String resolveWorkingDirectory(String requestedWorkingDirectory) {
        if (!isBlank(requestedWorkingDirectory)) {
            return requestedWorkingDirectory.trim();
        }
        if (sandboxSession.getSpec() != null && !isBlank(sandboxSession.getSpec().getWorkspaceId())) {
            return sandboxSession.getSpec().getWorkspaceId();
        }
        if (workspaceContext != null && !isBlank(workspaceContext.getRootPath())) {
            return workspaceContext.getRootPath();
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
