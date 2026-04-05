package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.coding.shell.LocalShellCommandExecutor;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandRequest;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandResult;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalShellCommandExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRunCommandInsideWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-shell").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .build();

        LocalShellCommandExecutor executor = new LocalShellCommandExecutor(workspaceContext, 10000L);
        ShellCommandResult result = executor.execute(ShellCommandRequest.builder()
                .command("echo hello-ai4j")
                .build());

        assertFalse(result.isTimedOut());
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().toLowerCase().contains("hello-ai4j"));
    }

    @Test
    public void shouldExplainHowToHandleTimedOutInteractiveOrLongRunningCommands() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-shell-timeout").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .build();

        LocalShellCommandExecutor executor = new LocalShellCommandExecutor(workspaceContext, 200L);
        ShellCommandResult result = executor.execute(ShellCommandRequest.builder()
                .command(timeoutCommand())
                .build());

        assertTrue(result.isTimedOut());
        assertEquals(-1, result.getExitCode());
        assertTrue(result.getStderr().contains("bash action=start"));
    }

    private String timeoutCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return "ping 127.0.0.1 -n 5 > nul";
        }
        return "sleep 5";
    }
}
