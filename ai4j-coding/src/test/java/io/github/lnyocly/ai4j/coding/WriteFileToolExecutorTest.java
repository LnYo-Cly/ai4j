package io.github.lnyocly.ai4j.coding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.WriteFileToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WriteFileToolExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldCreateOverwriteAndAppendFiles() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-write").toPath();
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        JSONObject created = JSON.parseObject(executor.execute(call("notes/todo.txt", "alpha", "create")));
        assertTrue(created.getBooleanValue("created"));
        assertFalse(created.getBooleanValue("appended"));
        assertEquals("alpha", new String(Files.readAllBytes(workspaceRoot.resolve("notes/todo.txt")), StandardCharsets.UTF_8));

        JSONObject overwritten = JSON.parseObject(executor.execute(call("notes/todo.txt", "beta", "overwrite")));
        assertFalse(overwritten.getBooleanValue("created"));
        assertFalse(overwritten.getBooleanValue("appended"));
        assertEquals("beta", new String(Files.readAllBytes(workspaceRoot.resolve("notes/todo.txt")), StandardCharsets.UTF_8));

        JSONObject appended = JSON.parseObject(executor.execute(call("notes/todo.txt", "\ngamma", "append")));
        assertFalse(appended.getBooleanValue("created"));
        assertTrue(appended.getBooleanValue("appended"));
        assertEquals("beta\ngamma", new String(Files.readAllBytes(workspaceRoot.resolve("notes/todo.txt")), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldAllowWritingAbsolutePathOutsideWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-write-outside").toPath();
        Path outsideFile = temporaryFolder.newFolder("outside-root").toPath().resolve("outside.txt");
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        JSONObject result = JSON.parseObject(executor.execute(call(outsideFile.toString(), "outside", "overwrite")));
        assertTrue(Files.exists(outsideFile));
        assertEquals("outside", new String(Files.readAllBytes(outsideFile), StandardCharsets.UTF_8));
        assertEquals(outsideFile.toAbsolutePath().normalize().toString(), result.getString("resolvedPath"));
    }

    private AgentToolCall call(String path, String content, String mode) {
        JSONObject arguments = new JSONObject();
        arguments.put("path", path);
        arguments.put("content", content);
        arguments.put("mode", mode);
        return AgentToolCall.builder()
                .name(CodingToolNames.WRITE_FILE)
                .arguments(arguments.toJSONString())
                .build();
    }
}
