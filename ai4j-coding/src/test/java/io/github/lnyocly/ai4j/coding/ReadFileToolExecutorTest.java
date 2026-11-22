package io.github.lnyocly.ai4j.coding;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.ReadFileToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.LocalWorkspaceFileService;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReadFileToolExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldReadFileInsideWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-read").toPath();
        Files.write(workspaceRoot.resolve("demo.txt"), "alpha\nbeta".getBytes(StandardCharsets.UTF_8));

        ReadFileToolExecutor executor = new ReadFileToolExecutor(
                new LocalWorkspaceFileService(WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()),
                CodingAgentOptions.builder().build()
        );

        String raw = executor.execute(AgentToolCall.builder()
                .name(CodingToolNames.READ_FILE)
                .arguments("{\"path\":\"demo.txt\"}")
                .build());

        ObjectNode result = CodingJson.parseObject(raw);
        assertEquals("demo.txt", CodingJson.getString(result, "path"));
        assertEquals("alpha\nbeta", CodingJson.getString(result, "content"));
        assertFalse(CodingJson.getBooleanValue(result, "truncated"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPathOutsideWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-read-escape").toPath();
        ReadFileToolExecutor executor = new ReadFileToolExecutor(
                new LocalWorkspaceFileService(WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()),
                CodingAgentOptions.builder().build()
        );

        executor.execute(AgentToolCall.builder()
                .name(CodingToolNames.READ_FILE)
                .arguments("{\"path\":\"../escape.txt\"}")
                .build());
    }
}
