package io.github.lnyocly.ai4j.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BuiltInToolExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldExecuteReadWriteAndApplyPatchTools() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("built-in-tools").toPath();
        Files.write(workspaceRoot.resolve("notes.txt"), "hello\nworld\n".getBytes(StandardCharsets.UTF_8));
        BuiltInToolContext context = BuiltInToolContext.builder()
                .workspaceRoot(workspaceRoot.toString())
                .build();

        JSONObject readResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.READ_FILE,
                "{\"path\":\"notes.txt\"}",
                context
        ));
        Assert.assertEquals("notes.txt", readResult.getString("path"));
        Assert.assertTrue(readResult.getString("content").contains("hello"));

        JSONObject writeResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.WRITE_FILE,
                "{\"path\":\"nested/output.txt\",\"content\":\"abc\",\"mode\":\"create\"}",
                context
        ));
        Assert.assertTrue(writeResult.getBooleanValue("created"));
        Assert.assertEquals("abc", new String(Files.readAllBytes(workspaceRoot.resolve("nested").resolve("output.txt")), StandardCharsets.UTF_8));

        String patch = "*** Begin Patch\n"
                + "*** Update File: nested/output.txt\n"
                + "@@\n"
                + "-abc\n"
                + "+updated\n"
                + "*** End Patch\n";
        JSONObject patchResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.APPLY_PATCH,
                JSON.toJSONString(new JSONObject() {{
                    put("patch", patch);
                }}),
                context
        ));
        Assert.assertEquals(1, patchResult.getIntValue("filesChanged"));
        Assert.assertEquals("updated", new String(Files.readAllBytes(workspaceRoot.resolve("nested").resolve("output.txt")), StandardCharsets.UTF_8));
        Assert.assertEquals(1, BuiltInTools.tools(BuiltInTools.READ_FILE).size());
    }

    @Test
    public void shouldExecuteBashToolsWithinContext() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("built-in-bash").toPath();
        BuiltInToolContext context = BuiltInToolContext.builder()
                .workspaceRoot(workspaceRoot.toString())
                .build();

        JSONObject execResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.BASH,
                "{\"action\":\"exec\",\"command\":\"echo hello\"}",
                context
        ));
        Assert.assertEquals(0, execResult.getIntValue("exitCode"));
        Assert.assertTrue(execResult.getString("stdout").toLowerCase().contains("hello"));

        String command = isWindows()
                ? "ping 127.0.0.1 -n 6 >nul"
                : "sleep 5";
        JSONObject startResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.BASH,
                JSON.toJSONString(new JSONObject() {{
                    put("action", "start");
                    put("command", command);
                }}),
                context
        ));
        String processId = startResult.getString("processId");
        Assert.assertNotNull(processId);

        JSONObject listResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.BASH,
                "{\"action\":\"list\"}",
                context
        ));
        Assert.assertFalse(listResult.getJSONArray("processes").isEmpty());

        JSONObject stopResult = JSON.parseObject(ToolUtil.invoke(
                BuiltInTools.BASH,
                JSON.toJSONString(new JSONObject() {{
                    put("action", "stop");
                    put("processId", processId);
                }}),
                context
        ));
        Assert.assertEquals(processId, stopResult.getString("processId"));
        Assert.assertEquals("STOPPED", stopResult.getString("status"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
