package io.github.lnyocly.ai4j.coding.policy;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingIsolationMode;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CodingToolPolicyResolverTest {

    @Test
    public void readOnlyModeShouldBlockBashWriteFileAndApplyPatch() throws Exception {
        CodingAgentDefinition definition = CodingAgentDefinition.builder()
                .name("reader")
                .allowedToolNames(setOf("bash", "read_file", "write_file", "apply_patch"))
                .isolationMode(CodingIsolationMode.READ_ONLY)
                .build();

        CodingToolPolicyResolver resolver = new CodingToolPolicyResolver();
        CodingToolContextPolicy policy = resolver.resolve(
                        allToolsRegistry(), recordingExecutor(), definition);

        Set<String> allowed = policy.getAllowedToolNames();
        assertFalse("bash must be blocked in READ_ONLY mode", allowed.contains("bash"));
        assertFalse("write_file must be blocked in READ_ONLY mode", allowed.contains("write_file"));
        assertFalse("apply_patch must be blocked in READ_ONLY mode", allowed.contains("apply_patch"));
        assertTrue("read_file must survive in READ_ONLY mode", allowed.contains("read_file"));
    }

    @Test
    public void readOnlyModeShouldBlockEditTool() throws Exception {
        CodingAgentDefinition definition = CodingAgentDefinition.builder()
                .name("reader")
                .allowedToolNames(setOf("edit", "read_file"))
                .isolationMode(CodingIsolationMode.READ_ONLY)
                .build();

        CodingToolPolicyResolver resolver = new CodingToolPolicyResolver();
        CodingToolContextPolicy policy = resolver.resolve(
                        allToolsRegistry(), recordingExecutor(), definition);

        assertFalse("edit must be blocked in READ_ONLY mode", policy.getAllowedToolNames().contains("edit"));
    }

    @Test
    public void readOnlyModeExecutorShouldRejectBashCall() throws Exception {
        CodingAgentDefinition definition = CodingAgentDefinition.builder()
                .name("reader")
                .allowedToolNames(setOf("bash", "read_file"))
                .isolationMode(CodingIsolationMode.READ_ONLY)
                .build();

        CodingToolPolicyResolver resolver = new CodingToolPolicyResolver();
        CodingToolContextPolicy policy = resolver.resolve(
                        allToolsRegistry(), recordingExecutor(), definition);

        try {
            policy.getToolExecutor().execute(AgentToolCall.builder()
                    .name("bash")
                    .arguments("{\"command\":\"rm -rf /\"}")
                    .build());
            fail("Expected bash to be rejected in READ_ONLY mode");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not allowed"));
        }
    }

    @Test
    public void inheritModeShouldKeepAllTools() throws Exception {
        CodingAgentDefinition definition = CodingAgentDefinition.builder()
                .name("full")
                .allowedToolNames(setOf("bash", "read_file", "write_file", "apply_patch"))
                .isolationMode(CodingIsolationMode.INHERIT)
                .build();

        CodingToolPolicyResolver resolver = new CodingToolPolicyResolver();
        CodingToolContextPolicy policy = resolver.resolve(
                        allToolsRegistry(), recordingExecutor(), definition);

        Set<String> allowed = policy.getAllowedToolNames();
        assertTrue("bash must survive in INHERIT mode", allowed.contains("bash"));
        assertTrue("write_file must survive in INHERIT mode", allowed.contains("write_file"));
        assertTrue("apply_patch must survive in INHERIT mode", allowed.contains("apply_patch"));
        assertTrue("read_file must survive in INHERIT mode", allowed.contains("read_file"));
    }

    @Test
    public void writeEnabledModeShouldKeepAllTools() throws Exception {
        CodingAgentDefinition definition = CodingAgentDefinition.builder()
                .name("writer")
                .allowedToolNames(setOf("bash", "write_file"))
                .isolationMode(CodingIsolationMode.WRITE_ENABLED)
                .build();

        CodingToolPolicyResolver resolver = new CodingToolPolicyResolver();
        CodingToolContextPolicy policy = resolver.resolve(
                        allToolsRegistry(), recordingExecutor(), definition);

        assertTrue("bash must survive in WRITE_ENABLED mode", policy.getAllowedToolNames().contains("bash"));
        assertTrue("write_file must survive in WRITE_ENABLED mode", policy.getAllowedToolNames().contains("write_file"));
    }

    private AgentToolRegistry allToolsRegistry() {
        return new StaticToolRegistry(Arrays.asList(
                (Object) BuiltInTools.bashTool(),
                BuiltInTools.readFileTool(),
                BuiltInTools.writeFileTool(),
                BuiltInTools.applyPatchTool()
        ));
    }

    private ToolExecutor recordingExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "{\"ok\":true}";
            }
        };
    }

    private Set<String> setOf(String... names) {
        return new LinkedHashSet<String>(Arrays.asList(names));
    }
}
