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
import static org.junit.Assert.fail;

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
    public void shouldRejectWritingAbsolutePathOutsideWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-write-outside").toPath();
        Path outsideFile = temporaryFolder.newFolder("outside-root").toPath().resolve("outside.txt");
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        try {
            executor.execute(call(outsideFile.toString(), "outside", "overwrite"));
            fail("Expected IllegalArgumentException for path outside workspace");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("escapes workspace") || expected.getMessage().contains("outside"));
        }
        assertFalse(Files.exists(outsideFile));
    }

    @Test
    public void shouldRejectParentDirectoryTraversal() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-traversal").toPath();
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        try {
            executor.execute(call("../../../etc/passwd", "evil", "overwrite"));
            fail("Expected IllegalArgumentException for parent directory traversal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("escapes workspace") || expected.getMessage().contains("outside"));
        }
    }

    @Test
    public void shouldRejectWriteToGitHooks() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-git-hooks").toPath();
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        try {
            executor.execute(call(".git/hooks/post-commit", "#!/bin/sh\nevil", "overwrite"));
            fail("Expected IllegalArgumentException for .git/hooks write");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("blocked") || expected.getMessage().contains(".git"));
        }
    }

    @Test
    public void shouldRejectWriteToSshDirectory() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-ssh").toPath();
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        try {
            executor.execute(call(".ssh/authorized_keys", "ssh-rsa ...", "overwrite"));
            fail("Expected IllegalArgumentException for .ssh write");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("blocked") || expected.getMessage().contains(".ssh"));
        }
    }

    @Test
    public void shouldRejectWriteToAwsDirectory() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-aws").toPath();
        WriteFileToolExecutor executor = new WriteFileToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        try {
            executor.execute(call(".aws/credentials", "[default]\naws_access_key_id=...", "overwrite"));
            fail("Expected IllegalArgumentException for .aws write");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("blocked") || expected.getMessage().contains(".aws"));
        }
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
