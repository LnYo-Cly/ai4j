package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.extension.ExtensionAgentTools;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillResolver;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillScope;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailDecision;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRequest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;
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
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        Assert.assertEquals("explicit Skill content must not invalidate the stable system prefix",
                firstSystem, selectedSystem);
        Assert.assertFalse(selectedSystem.contains("manual-runbook"));
        Assert.assertTrue(promptItemsText(modelClient.prompts.get(2)).contains("<selected_skills>"));
        Assert.assertTrue(promptItemsText(modelClient.prompts.get(2)).contains("# manual-runbook"));

        String refreshedSystem = modelClient.prompts.get(3).getSystemPrompt();
        Assert.assertTrue(refreshedSystem.contains("writer"));
        Assert.assertFalse(refreshedSystem.contains("reviewer"));
        Assert.assertFalse(refreshedSystem.contains("manual-runbook"));
        Assert.assertFalse("request-scoped Skill content must not persist into later Agent runs",
                promptItemsText(modelClient.prompts.get(3)).contains("# manual-runbook"));
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
    public void shouldExposeReadSkillFileWhenHostOwnsReadFile() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-conflict").toPath();
        Path skillFile = writeSkill(workspace, "reader", "Read the Skill instructions.", false);
        Tool conflict = new Tool();
        Tool.Function function = new Tool.Function();
        function.setName("read_file");
        conflict.setFunction(function);
        final AtomicInteger hostCalls = new AtomicInteger();
        RecordingModelClient modelClient = new RecordingModelClient(
                toolCallResult("skill-read", "read_skill_file", "{\"path\":\"" + escapeJson(skillFile.toString()) + "\"}"),
                textResult("done"));

        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .toolRegistry(() -> Collections.<Object>singletonList(conflict))
                .toolExecutor(new ToolExecutor() {
                    @Override
                    public String execute(AgentToolCall call) {
                        hostCalls.incrementAndGet();
                        return "host-owned read_file";
                    }
                })
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("conflict").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(0, hostCalls.get());
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("Read the Skill instructions."));
        Assert.assertTrue(hasTool(modelClient.prompts.get(0), "read_file"));
        Assert.assertTrue(hasTool(modelClient.prompts.get(0), "read_skill_file"));
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("read_skill_file"));
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
    public void shouldLoadHostProvidedMemorySkillOnDemand() throws Exception {
        Path workspace = temporaryFolder.newFolder("memory-skill-workspace").toPath();
        String virtualPath = "tenant://acme/runbooks/reconcile/SKILL.md";
        SkillDescriptor skill = inMemorySkill("tenant-reconcile", "Reconcile a tenant invoice safely.",
                "# Tenant reconcile\n\nReturn the tenant reconciliation checklist.", virtualPath, false);
        RecordingModelClient modelClient = new RecordingModelClient(
                toolCallResult("read-memory-skill", "read_file", "{\"path\":\"" + escapeJson(virtualPath) + "\"}"),
                textResult("done"));

        AgentResult result = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skillResolver(hostProvidedResolver(workspace, skill))
                .options(AgentOptions.builder().maxSteps(3).build())
                .build()
                .run(AgentRequest.builder().input("reconcile this tenant invoice").build());

        Assert.assertEquals("done", result.getOutputText());
        String systemPrompt = modelClient.prompts.get(0).getSystemPrompt();
        Assert.assertTrue(systemPrompt.contains("tenant-reconcile"));
        Assert.assertTrue(systemPrompt.contains(virtualPath));
        Assert.assertFalse("full in-memory Skill content must remain progressive", systemPrompt.contains("Tenant reconcile"));
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("tenant reconciliation checklist"));
    }

    @Test
    public void shouldApplySelectedHostProvidedMemorySkillAsRequestOverlay() throws Exception {
        Path workspace = temporaryFolder.newFolder("memory-skill-selected").toPath();
        String virtualPath = "tenant://acme/runbooks/manual/SKILL.md";
        SkillDescriptor skill = inMemorySkill("tenant-manual", "Host-selected tenant procedure.",
                "# Tenant manual\n\nAlways produce MANUAL_SKILL_APPLIED.", virtualPath, true);
        RecordingModelClient modelClient = new RecordingModelClient(textResult("first"), textResult("second"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skillResolver(hostProvidedResolver(workspace, skill))
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        agent.run(AgentRequest.builder().input("use manual runbook")
                .selectedSkills(Collections.singletonList("tenant-manual"))
                .build());
        agent.run(AgentRequest.builder().input("ordinary follow-up").build());

        Assert.assertFalse(modelClient.prompts.get(0).getSystemPrompt().contains("tenant-manual"));
        Assert.assertTrue(promptItemsText(modelClient.prompts.get(0)).contains("MANUAL_SKILL_APPLIED"));
        Assert.assertFalse("selected in-memory content must not persist into the next run",
                promptItemsText(modelClient.prompts.get(1)).contains("MANUAL_SKILL_APPLIED"));
    }

    @Test
    public void shouldRejectDuplicateHostProvidedSkillName() throws Exception {
        Path workspace = temporaryFolder.newFolder("memory-skill-duplicate").toPath();
        writeSkill(workspace, "shared", "File-backed shared Skill.", false);
        SkillDescriptor duplicate = inMemorySkill("shared", "Memory-backed shared Skill.",
                "# Shared\n\nThis must not shadow a file Skill.", "tenant://acme/shared/SKILL.md", false);
        RecordingModelClient modelClient = new RecordingModelClient(textResult("unused"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skillResolver(new AgentSkillResolver() {
                    @Override
                    public AgentSkillScope resolve(AgentRequest request) {
                        return AgentSkillScope.builder()
                                .workspaceRoot(workspace)
                                .skillDirectories(Collections.singletonList(".ai4j/skills"))
                                .providedSkills(Collections.singletonList(duplicate))
                                .build();
                    }
                })
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        try {
            agent.run(AgentRequest.builder().input("shared").build());
            Assert.fail("expected duplicate Skill name rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Duplicate Skill name"));
        }
        Assert.assertTrue(modelClient.prompts.isEmpty());
    }

    @Test
    public void shouldApplyAutomaticSkillsAndScopedReaderToCodeActRuntime() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-codeact").toPath();
        final Path skillFile = writeSkill(workspace, "code-review", "Review code changes.", false);
        final AtomicReference<String> readOutput = new AtomicReference<String>();

        RecordingModelClient modelClient = new RecordingModelClient(
                textResult("{\"type\":\"code\",\"language\":\"js\",\"code\":\"load skill\"}"));
        Agent agent = Agents.codeAct()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .codeExecutor(new CodeExecutor() {
                    @Override
                    public CodeExecutionResult execute(CodeExecutionRequest request) throws Exception {
                        Assert.assertTrue(request.getToolNames().contains("read_file"));
                        readOutput.set(request.getToolExecutor().execute(AgentToolCall.builder()
                                .name("read_file")
                                .arguments("{\"path\":\"" + escapeJson(skillFile.toString()) + "\"}")
                                .callId("code-read")
                                .type("function_call")
                                .build()));
                        return CodeExecutionResult.builder().result("done").build();
                    }
                })
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("review").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(1, modelClient.prompts.size());
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("code-review"));
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("<available_skills>"));
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("read_file"));
        Assert.assertTrue(readOutput.get().contains("Review code changes."));
    }

    @Test
    public void shouldKeepUserHomeSkillsOptInOnly() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-workspace-defaults").toPath();
        Path userHomeSkills = Paths.get(System.getProperty("user.home"))
                .resolve(".ai4j").resolve("skills");
        writeSkillUnderRoot(userHomeSkills, "user-home-only", "Available only to local developer mode.", false);

        RecordingModelClient workspaceOnlyClient = new RecordingModelClient(textResult("done"));
        Agents.react()
                .modelClient(workspaceOnlyClient)
                .model("test-model")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build()
                .run(AgentRequest.builder().input("workspace only").build());

        RecordingModelClient localClient = new RecordingModelClient(textResult("done"));
        Agents.react()
                .modelClient(localClient)
                .model("test-model")
                .skillsIncludingUserHome(workspace)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build()
                .run(AgentRequest.builder().input("local roots").build());

        Assert.assertFalse(workspaceOnlyClient.prompts.get(0).getSystemPrompt().contains("user-home-only"));
        Assert.assertTrue(localClient.prompts.get(0).getSystemPrompt().contains("user-home-only"));
    }

    @Test
    public void shouldKeepCatalogStableDuringToolLoopAndRefreshOnNextRun() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-loop-cache").toPath();
        Path reviewer = writeSkill(workspace, "reviewer", "Review source changes.", false);
        RecordingModelClient modelClient = new RecordingModelClient(
                toolCallResult("read-reviewer", "read_file", "{\"path\":\"" + escapeJson(reviewer.toString()) + "\"}"),
                textResult("first run done"),
                textResult("second run done"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        agent.run(AgentRequest.builder().input("first run").build());
        Files.delete(reviewer);
        writeSkill(workspace, "writer", "Write a focused change.", false);
        agent.run(AgentRequest.builder().input("second run").build());

        Assert.assertEquals(3, modelClient.prompts.size());
        Assert.assertEquals("one run must reuse exactly one cacheable catalog across model turns",
                modelClient.prompts.get(0).getSystemPrompt(), modelClient.prompts.get(1).getSystemPrompt());
        Assert.assertTrue(modelClient.prompts.get(0).getSystemPrompt().contains("reviewer"));
        Assert.assertTrue(modelClient.prompts.get(2).getSystemPrompt().contains("writer"));
        Assert.assertFalse(modelClient.prompts.get(2).getSystemPrompt().contains("reviewer"));
    }

    @Test
    public void shouldApplyExtensionGuardrailToAliasedSkillReader() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-guardrail").toPath();
        Path skillFile = writeSkill(workspace, "reader", "Read the Skill instructions.", false);
        Tool hostReadFile = new Tool();
        Tool.Function hostFunction = new Tool.Function();
        hostFunction.setName("read_file");
        hostReadFile.setFunction(hostFunction);
        final AtomicReference<String> guardedTool = new AtomicReference<String>();
        ExtensionGuardrail guardrail = new ExtensionGuardrail() {
            @Override
            public String name() {
                return "deny-skill-reads";
            }

            @Override
            public GuardrailDecision evaluate(GuardrailRequest request) {
                guardedTool.set(request.getTarget());
                return GuardrailDecision.deny("extension policy");
            }
        };
        ExtensionAgentTools extensionTools = ExtensionAgentTools.from(new ExtensionRuntimeSnapshot(
                Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(guardrail), Collections.emptyList()));
        RecordingModelClient modelClient = new RecordingModelClient(
                toolCallResult("alias-read", "read_skill_file", "{\"path\":\"" + escapeJson(skillFile.toString()) + "\"}"),
                textResult("done"));

        AgentResult result = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .extensions(extensionTools)
                .skills(workspace)
                .toolRegistry(() -> Collections.<Object>singletonList(hostReadFile))
                .options(AgentOptions.builder().maxSteps(2).build())
                .build()
                .run(AgentRequest.builder().input("read guarded skill").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals("read_file", guardedTool.get());
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("extension policy"));
    }

    @Test
    public void shouldRejectOversizedSelectedSkillBeforeModelInvocation() throws Exception {
        Path workspace = temporaryFolder.newFolder("skill-size-limit").toPath();
        Path skillFile = workspace.resolve(".ai4j").resolve("skills").resolve("oversized").resolve("SKILL.md");
        Files.createDirectories(skillFile.getParent());
        StringBuilder content = new StringBuilder("---\nname: oversized\ndescription: Test size limits.\ndisable-model-invocation: true\n---\n");
        for (int index = 0; index <= 12_000; index++) {
            content.append('x');
        }
        Files.write(skillFile, content.toString().getBytes(StandardCharsets.UTF_8));
        RecordingModelClient modelClient = new RecordingModelClient(textResult("unused"));
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        try {
            agent.run(AgentRequest.builder()
                    .input("activate oversized")
                    .selectedSkills(Collections.singletonList("oversized"))
                    .build());
            Assert.fail("expected selected Skill size validation");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("12000 character"));
        }
        Assert.assertTrue(modelClient.prompts.isEmpty());
    }

    @Test
    public void shouldKeepLegacyAgentRequestConstructor() {
        AgentRequest request = new AgentRequest("legacy input", Collections.<String, Object>singletonMap("key", "value"));

        Assert.assertEquals("legacy input", request.getInput());
        Assert.assertEquals("value", request.getMetadataString("key"));
        Assert.assertNull(request.getSelectedSkills());
    }

    @Test
    public void shouldLoadSkillWithNativeAnthropicProviderWhenExplicitlyEnabled() throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: set AI4J_LIVE_SKILLS_TEST=true and ANTHROPIC_API_KEY to run live Skill smoke",
                "true".equalsIgnoreCase(System.getenv("AI4J_LIVE_SKILLS_TEST"))
                        && apiKey != null && !apiKey.trim().isEmpty());
        String baseUrl = valueOrDefault(System.getenv("ANTHROPIC_BASE_URL"),
                "https://open.bigmodel.cn/api/anthropic/");
        String model = valueOrDefault(System.getenv("ANTHROPIC_MODEL"), "glm-5.1");
        Path workspace = temporaryFolder.newFolder("skill-live-provider").toPath();
        writeSkill(workspace, "live-skill", "After reading this Skill, answer with SKILL_LOADED.", false);

        AgentResult result = Agents.react()
                .anthropicMessages(apiKey, baseUrl)
                .model(model)
                .skills(workspace)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build()
                .run(AgentRequest.builder()
                        .input("Use the available live-skill before answering. You must call read_file to load its SKILL.md, then follow its instruction.")
                        .build());

        Assert.assertFalse("live model must return a response", result.getOutputText().trim().isEmpty());
        Assert.assertFalse("live model must load the announced Skill", result.getToolCalls().isEmpty());
        Assert.assertEquals("read_file", result.getToolCalls().get(0).getName());
        Assert.assertTrue("live model must apply the loaded Skill instruction",
                result.getOutputText().contains("SKILL_LOADED"));
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

    private AgentSkillResolver hostProvidedResolver(final Path workspace, final SkillDescriptor... skills) {
        return new AgentSkillResolver() {
            @Override
            public AgentSkillScope resolve(AgentRequest request) {
                return AgentSkillScope.builder()
                        .workspaceRoot(workspace)
                        .providedSkills(Arrays.asList(skills))
                        .build();
            }
        };
    }

    private SkillDescriptor inMemorySkill(String name, String description, String content,
                                          String virtualPath, boolean manualOnly) {
        return SkillDescriptor.builder()
                .name(name)
                .description(description)
                .content(content)
                .skillFilePath(virtualPath)
                .source("tenant")
                .disableModelInvocation(manualOnly)
                .build();
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

    private static String promptItemsText(AgentPrompt prompt) {
        return prompt == null || prompt.getItems() == null ? "" : String.valueOf(prompt.getItems());
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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
