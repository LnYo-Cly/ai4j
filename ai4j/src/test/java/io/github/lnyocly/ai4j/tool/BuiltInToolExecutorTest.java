package io.github.lnyocly.ai4j.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

        JsonNode readResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.READ_FILE,
                "{\"path\":\"notes.txt\"}",
                context
        ));
        Assert.assertEquals("notes.txt", readResult.path("path").asText());
        Assert.assertTrue(readResult.path("content").asText().contains("hello"));

        JsonNode writeResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.WRITE_FILE,
                "{\"path\":\"nested/output.txt\",\"content\":\"abc\",\"mode\":\"create\"}",
                context
        ));
        Assert.assertTrue(writeResult.path("created").asBoolean());
        Assert.assertEquals("abc", new String(Files.readAllBytes(workspaceRoot.resolve("nested").resolve("output.txt")), StandardCharsets.UTF_8));

        String patch = "*** Begin Patch\n"
                + "*** Update File: nested/output.txt\n"
                + "@@\n"
                + "-abc\n"
                + "+updated\n"
                + "*** End Patch\n";
        Map<String, Object> patchArgs = new HashMap<String, Object>();
        patchArgs.put("patch", patch);
        JsonNode patchResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.APPLY_PATCH,
                JsonCodec.toJson(patchArgs),
                context
        ));
        Assert.assertEquals(1, patchResult.path("filesChanged").asInt());
        Assert.assertEquals("updated", new String(Files.readAllBytes(workspaceRoot.resolve("nested").resolve("output.txt")), StandardCharsets.UTF_8));
        Assert.assertEquals(1, BuiltInTools.tools(BuiltInTools.READ_FILE).size());
    }

    @Test
    public void shouldExecuteBashToolsWithinContext() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("built-in-bash").toPath();
        BuiltInToolContext context = BuiltInToolContext.builder()
                .workspaceRoot(workspaceRoot.toString())
                .build();

        JsonNode execResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.BASH,
                "{\"action\":\"exec\",\"command\":\"echo hello\"}",
                context
        ));
        Assert.assertEquals(0, execResult.path("exitCode").asInt());
        Assert.assertTrue(execResult.path("stdout").asText().toLowerCase().contains("hello"));

        String command = isWindows()
                ? "ping 127.0.0.1 -n 6 >nul"
                : "sleep 5";
        Map<String, Object> startArgs = new HashMap<String, Object>();
        startArgs.put("action", "start");
        startArgs.put("command", command);
        JsonNode startResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.BASH,
                JsonCodec.toJson(startArgs),
                context
        ));
        String processId = startResult.path("processId").asText();
        Assert.assertFalse(processId.isEmpty());

        JsonNode listResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.BASH,
                "{\"action\":\"list\"}",
                context
        ));
        Assert.assertTrue(listResult.path("processes").size() > 0);

        Map<String, Object> stopArgs = new HashMap<String, Object>();
        stopArgs.put("action", "stop");
        stopArgs.put("processId", processId);
        JsonNode stopResult = JsonCodec.readTree(ToolUtil.invoke(
                BuiltInTools.BASH,
                JsonCodec.toJson(stopArgs),
                context
        ));
        Assert.assertEquals(processId, stopResult.path("processId").asText());
        Assert.assertEquals("STOPPED", stopResult.path("status").asText());
    }

    @Test
    public void shouldKeepLegacyBuiltInToolContextConstructor() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("legacy-built-in-context").toPath();

        BuiltInToolContext context = new BuiltInToolContext(
                workspaceRoot.toString(),
                false,
                Collections.<String>emptyList(),
                100,
                200L,
                300,
                400,
                500L,
                null);

        Assert.assertEquals(workspaceRoot.toAbsolutePath().normalize(), context.getWorkspaceRootPath());
        Assert.assertFalse(context.isRestrictReadToAllowedRoots());
        Assert.assertEquals(100, context.getDefaultReadMaxChars());
        Assert.assertEquals(200L, context.getDefaultCommandTimeoutMs());
    }

    @Test
    public void bashToolsDeclareSplitExecAndProcessSurfaces() {
        // bash is exec-only: no action property, command required.
        Tool.Function bash = BuiltInTools.bashTool().getFunction();
        Assert.assertEquals("bash", bash.getName());
        Assert.assertFalse(bash.getParameters().getProperties().containsKey("action"));
        Assert.assertEquals(Collections.singletonList("command"), bash.getParameters().getRequired());

        // bash_process carries the process-management action enum.
        Tool.Function process = BuiltInTools.bashProcessTool().getFunction();
        Assert.assertEquals("bash_process", process.getName());
        Assert.assertTrue(process.getParameters().getProperties().containsKey("action"));
        Assert.assertEquals(Collections.singletonList("action"), process.getParameters().getRequired());
        Assert.assertTrue(process.getParameters().getProperties().get("action").getEnumValues()
                .containsAll(java.util.Arrays.asList("start", "status", "logs", "write", "stop", "list")));

        // The built-in registry exposes both; bash_process is not read-only.
        Assert.assertTrue(BuiltInTools.allCodingToolNames().contains(BuiltInTools.BASH_PROCESS));
        Assert.assertFalse(BuiltInTools.readOnlyCodingToolNames().contains(BuiltInTools.BASH_PROCESS));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
