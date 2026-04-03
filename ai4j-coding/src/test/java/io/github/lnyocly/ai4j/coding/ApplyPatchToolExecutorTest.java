package io.github.lnyocly.ai4j.coding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
        JSONObject add = JSON.parseObject(addResult);
        assertEquals(1, add.getIntValue("filesChanged"));
        assertEquals("notes/todo.txt", add.getJSONArray("fileChanges").getJSONObject(0).getString("path"));
        assertEquals("add", add.getJSONArray("fileChanges").getJSONObject(0).getString("operation"));
        assertEquals(2, add.getJSONArray("fileChanges").getJSONObject(0).getIntValue("linesAdded"));
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
        JSONObject update = JSON.parseObject(updateResult);
        assertEquals(1, update.getIntValue("operationsApplied"));
        assertEquals("demo.txt", update.getJSONArray("fileChanges").getJSONObject(0).getString("path"));
        assertEquals("update", update.getJSONArray("fileChanges").getJSONObject(0).getString("operation"));
        assertEquals(1, update.getJSONArray("fileChanges").getJSONObject(0).getIntValue("linesAdded"));
        assertEquals(1, update.getJSONArray("fileChanges").getJSONObject(0).getIntValue("linesRemoved"));
        assertEquals("first\nupdated-second\nthird",
                new String(Files.readAllBytes(workspaceRoot.resolve("demo.txt")), StandardCharsets.UTF_8));

        String deleteResult = executor.execute(call(patch(
                "*** Begin Patch",
                "*** Delete File: demo.txt",
                "*** End Patch"
        )));
        JSONObject delete = JSON.parseObject(deleteResult);
        assertEquals(1, delete.getIntValue("filesChanged"));
        assertEquals("demo.txt", delete.getJSONArray("fileChanges").getJSONObject(0).getString("path"));
        assertEquals("delete", delete.getJSONArray("fileChanges").getJSONObject(0).getString("operation"));
        assertEquals(3, delete.getJSONArray("fileChanges").getJSONObject(0).getIntValue("linesRemoved"));
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
        JSONObject add = JSON.parseObject(addResult);
        assertEquals(1, add.getIntValue("filesChanged"));
        assertEquals("calculator.py", add.getJSONArray("fileChanges").getJSONObject(0).getString("path"));
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
        JSONObject update = JSON.parseObject(updateResult);
        assertEquals(1, update.getIntValue("operationsApplied"));
        assertEquals("sample.txt", update.getJSONArray("fileChanges").getJSONObject(0).getString("path"));
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
        JSONObject update = JSON.parseObject(updateResult);
        assertEquals(1, update.getIntValue("operationsApplied"));
        assertEquals("sample.txt", update.getJSONArray("fileChanges").getJSONObject(0).getString("path"));
        assertEquals(1, update.getJSONArray("fileChanges").getJSONObject(0).getIntValue("linesAdded"));
        assertEquals(1, update.getJSONArray("fileChanges").getJSONObject(0).getIntValue("linesRemoved"));
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

    private AgentToolCall call(String patch) {
        JSONObject arguments = new JSONObject();
        arguments.put("patch", patch);
        return AgentToolCall.builder()
                .name(CodingToolNames.APPLY_PATCH)
                .arguments(arguments.toJSONString())
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
