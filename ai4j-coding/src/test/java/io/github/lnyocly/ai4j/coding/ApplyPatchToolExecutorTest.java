package io.github.lnyocly.ai4j.coding;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.tool.ApplyPatchToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
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

public class ApplyPatchToolExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldAddUpdateAndDeleteFiles() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .build();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(workspaceContext);

        String addResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Add File: notes/todo.txt",
                "+alpha",
                "+beta",
                "*** End Patch"
        )));
        ObjectNode add = CodingJson.parseObject(addResult);
        assertEquals(1, CodingJson.getIntValue(add, "filesChanged"));
        assertEquals("notes/todo.txt", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(add, "fileChanges"), 0), "path"));
        assertEquals("add", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(add, "fileChanges"), 0), "operation"));
        assertEquals(2, CodingJson.getIntValue(CodingJson.getObject(CodingJson.getArray(add, "fileChanges"), 0), "linesAdded"));
        assertTrue(Files.exists(workspaceRoot.resolve("notes/todo.txt")));
        assertEquals("alpha\nbeta", new String(Files.readAllBytes(workspaceRoot.resolve("notes/todo.txt")), StandardCharsets.UTF_8));

        Files.write(workspaceRoot.resolve("demo.txt"), "first\nsecond\nthird".getBytes(StandardCharsets.UTF_8));
        String updateResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Update File: demo.txt",
                "@@",
                " first",
                "-second",
                "+updated-second",
                " third",
                "*** End Patch"
        )));
        ObjectNode update = CodingJson.parseObject(updateResult);
        assertEquals(1, CodingJson.getIntValue(update, "operationsApplied"));
        assertEquals("demo.txt", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "path"));
        assertEquals("update", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "operation"));
        assertEquals(1, CodingJson.getIntValue(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "linesAdded"));
        assertEquals(1, CodingJson.getIntValue(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "linesRemoved"));
        assertEquals("first\nupdated-second\nthird",
                new String(Files.readAllBytes(workspaceRoot.resolve("demo.txt")), StandardCharsets.UTF_8));

        String deleteResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Delete File: demo.txt",
                "*** End Patch"
        )));
        ObjectNode delete = CodingJson.parseObject(deleteResult);
        assertEquals(1, CodingJson.getIntValue(delete, "filesChanged"));
        assertEquals("demo.txt", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(delete, "fileChanges"), 0), "path"));
        assertEquals("delete", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(delete, "fileChanges"), 0), "operation"));
        assertEquals(3, CodingJson.getIntValue(CodingJson.getObject(CodingJson.getArray(delete, "fileChanges"), 0), "linesRemoved"));
        assertFalse(Files.exists(workspaceRoot.resolve("demo.txt")));
    }

    @Test
    public void shouldAcceptShortDirectiveAliases() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch-short-directives").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .build();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(workspaceContext);

        String addResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Add: calculator.py",
                "+print('ok')",
                "*** End Patch"
        )));
        ObjectNode add = CodingJson.parseObject(addResult);
        assertEquals(1, CodingJson.getIntValue(add, "filesChanged"));
        assertEquals("calculator.py", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(add, "fileChanges"), 0), "path"));
        assertTrue(Files.exists(workspaceRoot.resolve("calculator.py")));
        assertEquals("print('ok')", new String(Files.readAllBytes(workspaceRoot.resolve("calculator.py")), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldAcceptDirectiveWithoutSpaceAfterColonAndWorkspaceRootPath() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch-relaxed-directive").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .build();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(workspaceContext);

        Files.write(workspaceRoot.resolve("sample.txt"), "value=1".getBytes(StandardCharsets.UTF_8));

        String updateResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Update File:/sample.txt",
                "@@",
                "-value=1",
                "+value=2",
                "*** End Patch"
        )));
        ObjectNode update = CodingJson.parseObject(updateResult);
        assertEquals(1, CodingJson.getIntValue(update, "operationsApplied"));
        assertEquals("sample.txt", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "path"));
        assertEquals("value=2", new String(Files.readAllBytes(workspaceRoot.resolve("sample.txt")), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldAcceptUnifiedDiffMetadataBeforePatchHunk() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch-unified-diff").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .build();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(workspaceContext);

        Files.write(workspaceRoot.resolve("sample.txt"), "value=1".getBytes(StandardCharsets.UTF_8));

        String updateResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Update File:/sample.txt",
                "--- a/sample.txt",
                "+++ b/sample.txt",
                "@@",
                "-value=1",
                "+value=2",
                "*** End Patch"
        )));
        ObjectNode update = CodingJson.parseObject(updateResult);
        assertEquals(1, CodingJson.getIntValue(update, "operationsApplied"));
        assertEquals("sample.txt", CodingJson.getString(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "path"));
        assertEquals(1, CodingJson.getIntValue(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "linesAdded"));
        assertEquals(1, CodingJson.getIntValue(CodingJson.getObject(CodingJson.getArray(update, "fileChanges"), 0), "linesRemoved"));
        assertEquals("value=2", new String(Files.readAllBytes(workspaceRoot.resolve("sample.txt")), StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectEscapingWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch-escape").toPath();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        executor.execute(call(patch(
                "*** Begin Patch",
                "*** Add File: ../escape.txt",
                "+blocked",
                "*** End Patch"
        )));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectAddFileToGitHooks() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch-git-hooks").toPath();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        executor.execute(call(patch(
                "*** Begin Patch",
                "*** Add File: .git/hooks/post-commit",
                "+#!/bin/sh",
                "+evil",
                "*** End Patch"
        )));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectAddFileToSshDirectory() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-patch-ssh").toPath();
        ApplyPatchToolExecutor executor = new ApplyPatchToolExecutor(
                WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build()
        );

        executor.execute(call(patch(
                "*** Begin Patch",
                "*** Add File: .ssh/authorized_keys",
                "+ssh-rsa ...",
                "*** End Patch"
        )));
    }

    private AgentToolCall call(String patch) {
        ObjectNode arguments = CodingJson.newObject();
        arguments.put("patch", patch);
        return AgentToolCall.builder()
                .name(CodingToolNames.APPLY_PATCH)
                .arguments(CodingJson.toJson(arguments))
                .build();
    }

    private String patch(String... lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }
}
