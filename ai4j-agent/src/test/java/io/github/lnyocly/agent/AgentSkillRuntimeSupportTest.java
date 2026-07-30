package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillResolver;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillScope;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class AgentSkillRuntimeSupportTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String originalUserHome;

    @Before
    public void isolateUserSkillRoots() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryFolder.newFolder("isolated-user-home").getAbsolutePath());
    }

    @After
    public void restoreUserSkillRoots() {
        if (originalUserHome == null) {
            System.clearProperty("user.home");
            return;
        }
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    public void shouldLeaveAgentPromptUntouchedWhenNoSkillsAreDiscovered() throws Exception {
        Path workspace = temporaryFolder.newFolder("empty-skill-root").toPath();
        RecordingModelClient modelClient = new RecordingModelClient(textResult("done"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .systemPrompt("host-system-boundary")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        agent.run(AgentRequest.builder().input("no skills").build());

        AgentPrompt prompt = modelClient.prompts.get(0);
        Assert.assertTrue(prompt.getSystemPrompt().contains("host-system-boundary"));
        Assert.assertFalse(prompt.getSystemPrompt().contains("<available_skills>"));
        Assert.assertFalse(hasTool(prompt, "read_file"));
    }

    @Test
    public void shouldRefreshSkillCatalogPerRunAndExposeManualOnlyOnlyWhenSelected() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-refresh").toPath();
        Path reviewer = writeSkill(workspace, "reviewer", "Review source changes.", false);
        writeSkill(workspace, "manual-runbook", "Run only when explicitly selected.", true);

        RecordingModelClient modelClient = new RecordingModelClient(
                textResult("first"), textResult("second"), textResult("selected"), textResult("refreshed"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        agent.run(AgentRequest.builder().input("first").build());
        agent.run(AgentRequest.builder().input("second").build());
        agent.run(AgentRequest.builder()
                .input("selected")
                .selectedSkills(Collections.singletonList("manual-runbook"))
                .build());

        Files.delete(reviewer);
        writeSkill(workspace, "writer", "Write a focused change.", false);
        agent.run(AgentRequest.builder().input("refreshed").build());

        Assert.assertEquals(4, modelClient.prompts.size());
        String firstSystem = modelClient.prompts.get(0).getSystemPrompt();
        Assert.assertTrue(firstSystem.contains("<available_skills>"));
        Assert.assertTrue(firstSystem.contains("reviewer"));
        Assert.assertFalse(firstSystem.contains("manual-runbook"));
        Assert.assertEquals("unchanged catalog should preserve a stable system prompt for prompt cache reuse",
                firstSystem, modelClient.prompts.get(1).getSystemPrompt());
        Assert.assertTrue(hasTool(modelClient.prompts.get(0), "read_file"));

        String selectedSystem = modelClient.prompts.get(2).getSystemPrompt();
        Assert.assertTrue(selectedSystem.contains("<selected_skills>"));
        Assert.assertTrue(selectedSystem.contains("manual-runbook"));
        Assert.assertTrue(selectedSystem.contains("# manual-runbook"));

        String refreshedSystem = modelClient.prompts.get(3).getSystemPrompt();
        Assert.assertTrue(refreshedSystem.contains("writer"));
        Assert.assertFalse(refreshedSystem.contains("reviewer"));
        Assert.assertFalse(refreshedSystem.contains("manual-runbook"));
    }

    @Test
    public void shouldReadOnlySkillDirectoryAndHonorPermissionPolicy() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-read").toPath();
        Path skillFile = writeSkill(workspace, "reader", "Read the Skill instructions.", false);
        Path secretFile = workspace.resolve("outside.txt");
        Files.write(secretFile, "outside-secret".getBytes(StandardCharsets.UTF_8));

        AgentResult allowed = runWithToolCall(workspace, skillFile, null);
        Assert.assertEquals("done", allowed.getOutputText());
        Assert.assertEquals(1, allowed.getToolResults().size());
        Assert.assertTrue(allowed.getToolResults().get(0).getOutput().contains("Read the Skill instructions."));

        AgentResult outside = runWithToolCall(workspace, secretFile, null);
        Assert.assertEquals("done", outside.getOutputText());
        Assert.assertEquals(1, outside.getToolResults().size());
        Assert.assertTrue(outside.getToolResults().get(0).getOutput().contains("Path escapes workspace root"));

        AgentResult denied = runWithToolCall(workspace, skillFile,
                AgentPermissionPolicies.denyTools(Collections.singleton("read_file"), "host denied Skill reads"));
        Assert.assertEquals("done", denied.getOutputText());
        Assert.assertEquals(1, denied.getToolResults().size());
        Assert.assertTrue(denied.getToolResults().get(0).getOutput().contains("host denied Skill reads"));
    }

    @Test
    public void shouldRejectAConflictingReadFileTool() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-conflict").toPath();
        writeSkill(workspace, "reader", "Read the Skill instructions.", false);
        Tool conflict = new Tool();
        Tool.Function function = new Tool.Function();
        function.setName("read_file");
        conflict.setFunction(function);

        Agent agent = Agents.react()
                .modelClient(new RecordingModelClient(textResult("unused")))
                .model("test-model")
                .skills(workspace)
                .toolRegistry(() -> Collections.<Object>singletonList(conflict))
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        try {
            agent.run(AgentRequest.builder().input("conflict").build());
            Assert.fail("expected read_file conflict");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("reserves read_file"));
        }
    }

    @Test
    public void shouldKeepCustomResolverRootsIsolatedFromDefaultSkillRoots() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-isolation-workspace").toPath();
        writeSkill(workspace, "workspace-only", "Must not be exposed to the tenant resolver.", false);
        Path tenantRoot = temporaryFolder.newFolder("skill-isolation-tenant").toPath();
        writeSkillUnderRoot(tenantRoot, "tenant-only", "Available through the host policy.", false);

        RecordingModelClient modelClient = new RecordingModelClient(textResult("done"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skillResolver(new AgentSkillResolver() {
                    @Override
                    public AgentSkillScope resolve(AgentRequest request) {
                        return AgentSkillScope.builder()
                                .workspaceRoot(workspace)
                                .skillDirectories(Collections.singletonList(tenantRoot.toString()))
                                .build();
                    }
                })
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        agent.run(AgentRequest.builder().input("isolation").build());

        String systemPrompt = modelClient.prompts.get(0).getSystemPrompt();
        Assert.assertTrue(systemPrompt.contains("tenant-only"));
        Assert.assertFalse(systemPrompt.contains("workspace-only"));
    }

    @Test
    public void shouldResolveRelativeCustomResolverRootsAgainstItsWorkspace() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-relative-resolver-workspace").toPath();
        writeSkillUnderRoot(workspace.resolve("tenant-skills"), "tenant-relative",
                "Available through a relative tenant policy root.", false);

        RecordingModelClient modelClient = new RecordingModelClient(textResult("done"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skillResolver(new AgentSkillResolver() {
                    @Override
                    public AgentSkillScope resolve(AgentRequest request) {
                        return AgentSkillScope.builder()
                                .workspaceRoot(workspace)
                                .skillDirectories(Collections.singletonList("tenant-skills"))
                                .build();
                    }
                })
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        agent.run(AgentRequest.builder().input("relative root").build());

        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("tenant-relative"));
    }

    @Test
    public void shouldApplySkillsToCodeActRuntime() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-codeact").toPath();
        writeSkill(workspace, "code-review", "Review code changes.", true);

        RecordingModelClient modelClient = new RecordingModelClient(textResult("{\"type\":\"final\",\"output\":\"done\"}"));
        Agent agent = Agents.codeAct()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder()
                .input("review")
                .selectedSkills(Collections.singletonList("code-review"))
                .build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(1, modelClient.prompts.size());
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("code-review"));
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("# code-review"));
        Assert.assertFalse(modelClient.prompts.get(0).getSystemPrompt().contains("<available_skills>"));
    }

    @Test
    public void shouldRejectSymlinkEscapeWhenSupported() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-symlink").toPath();
        Path skillFile = writeSkill(workspace, "reader", "Read the Skill instructions.", false);
        Path secretFile = workspace.resolve("outside.txt");
        Files.write(secretFile, "outside-secret".getBytes(StandardCharsets.UTF_8));
        Path escape = skillFile.getParent().resolve("escape.txt");
        try {
            Files.createSymbolicLink(escape, secretFile);
        } catch (UnsupportedOperationException ex) {
            Assume.assumeNoException(ex);
        } catch (SecurityException ex) {
            Assume.assumeNoException(ex);
        } catch (java.io.IOException ex) {
            Assume.assumeNoException(ex);
        }

        AgentResult result = runWithToolCall(workspace, escape, null);

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(1, result.getToolResults().size());
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("Path escapes workspace root"));
    }

    private AgentResult runWithToolCall(Path workspace,
                                        Path target,
                                        io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy permissionPolicy) throws Exception {
        RecordingModelClient modelClient = new RecordingModelClient(
                toolCallResult("read-1", "read_file", "{\"path\":\"" + escapeJson(target.toString()) + "\"}"),
                textResult("done"));
        io.github.lnyocly.ai4j.agent.AgentBuilder builder = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(3).build());
        if (permissionPolicy != null) {
            builder.permissionPolicy(permissionPolicy);
        }
        return builder.build().run(AgentRequest.builder().input("read").build());
    }

    private Path writeSkill(Path workspace, String name, String description, boolean manualOnly) throws Exception {
        return writeSkillUnderRoot(workspace.resolve(".ai4j").resolve("skills"), name, description, manualOnly);
    }

    private Path writeSkillUnderRoot(Path root, String name, String description, boolean manualOnly) throws Exception {
        Path skillFile = root.resolve(name).resolve("SKILL.md");
        Files.createDirectories(skillFile.getParent());
        String content = "---\n"
                + "name: " + name + "\n"
                + "description: " + description + "\n"
                + "disable-model-invocation: " + manualOnly + "\n"
                + "---\n\n"
                + "# " + name + "\n\n"
                + description + "\n";
        Files.write(skillFile, content.getBytes(StandardCharsets.UTF_8));
        return skillFile;
    }

    private static boolean hasTool(AgentPrompt prompt, String name) {
        if (prompt == null || prompt.getTools() == null) {
            return false;
        }
        for (Object candidate : prompt.getTools()) {
            if (candidate instanceof Tool) {
                Tool tool = (Tool) candidate;
                if (tool.getFunction() != null && name.equals(tool.getFunction().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AgentModelResult textResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    private static AgentModelResult toolCallResult(String callId, String name, String arguments) {
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .callId(callId)
                        .name(name)
                        .arguments(arguments)
                        .type("function_call")
                        .build()))
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class RecordingModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();
        private final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();

        private RecordingModelClient(AgentModelResult... responses) {
            results.addAll(Arrays.asList(responses));
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            return results.isEmpty() ? textResult("") : results.removeFirst();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }
}
