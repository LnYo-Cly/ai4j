package io.github.lnyocly.ai4j.agent.extension;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequest;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestDecision;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptDecision;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptRequest;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallDecision;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallRequest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic agent-loop tests for extension-contributed interceptors (the
 * {@code interceptor} capability). A scripted model client emits one tool call then a final
 * answer; the extension interceptor decides allow / block / modify / routeTo and we assert the
 * runtime honors it through the same interception surfaces as host-registered hooks.
 */
public class ExtensionInterceptorsTest {

    private static AgentToolCall call(String args) {
        return AgentToolCall.builder().name("do_thing").callId("c1").arguments(args).build();
    }

    private static AgentToolRegistry doThingRegistry() {
        Tool.Function fn = new Tool.Function();
        fn.setName("do_thing");
        fn.setDescription("does a thing");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        param.setProperties(props);
        param.setRequired(Collections.<String>emptyList());
        fn.setParameters(param);
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));
    }

    static final class ScriptedModelClient implements AgentModelClient {
        final List<AgentModelResult> scripted;
        final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();
        int idx = 0;

        ScriptedModelClient(List<AgentModelResult> scripted) {
            this.scripted = scripted;
        }

        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            return scripted.get(Math.min(idx++, scripted.size() - 1));
        }

        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener l) {
            return create(prompt);
        }
    }

    static final class RecordingExecutor implements ToolExecutor {
        final List<AgentToolCall> executed = new ArrayList<AgentToolCall>();

        public String execute(AgentToolCall call) {
            executed.add(call);
            return "real-output";
        }
    }

    static final class FakeSandboxSession implements SandboxSession {
        final List<SandboxCommand> executed = new ArrayList<SandboxCommand>();
        private final SandboxResult result;

        FakeSandboxSession(SandboxResult result) {
            this.result = result;
        }

        public String getSessionId() { return "fake-sid"; }
        public String getProviderId() { return "fake"; }
        public SandboxSpec getSpec() { return null; }
        public SandboxStatus getStatus() { return SandboxStatus.RUNNING; }
        public SandboxResult execute(SandboxCommand command) { executed.add(command); return result; }
        public boolean cancel(String commandId) { return false; }
        public List<SandboxArtifact> listArtifacts() { return Collections.<SandboxArtifact>emptyList(); }
        public void close() { }
    }

    static final class FakeSandboxProvider implements SandboxProvider {
        final List<SandboxSpec> created = new ArrayList<SandboxSpec>();
        private final SandboxResult result;

        FakeSandboxProvider(SandboxResult result) {
            this.result = result;
        }

        public String getProviderId() { return "fake"; }
        public boolean supports(SandboxSpec spec) { return true; }
        public SandboxSession createSession(SandboxSpec spec) {
            created.add(spec);
            return new FakeSandboxSession(result);
        }
    }

    /** Extension declaring the {@code interceptor} capability and registering one of each kind. */
    private abstract static class InterceptorExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("intercept-pack")
                    .name("Intercept Pack")
                    .capability(ExtensionCapability.INTERCEPTOR)
                    .build();
        }
    }

    private static ScriptedModelClient toolThenText(AgentToolCall requested) {
        return new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
    }

    private static Agent baseAgent(ScriptedModelClient model,
                                   RecordingExecutor exec,
                                   ExtensionRegistry registry) {
        AgentBuilder builder = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolRegistry(doThingRegistry())
                .options(AgentOptions.builder().maxSteps(4).build());
        if (exec != null) {
            builder.toolExecutor(exec);
        }
        if (registry != null) {
            builder.extensions(registry);
        }
        return builder.build();
    }

    @Test
    public void shouldDenyInterceptorRegistrationWithoutCapability() {
        Ai4jExtension extension = new Ai4jExtension() {
            public ExtensionManifest manifest() {
                return ExtensionManifest.builder()
                        .id("no-cap")
                        .name("No Cap")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }

            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "sneaky"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        return ExtensionToolCallDecision.allow();
                    }
                });
            }
        };

        try {
            ExtensionRegistry.of(extension).enable("no-cap").snapshot();
            Assert.fail("expected ExtensionException for missing interceptor capability");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("interceptor"));
        }
    }

    @Test
    public void shouldApplyExtensionBlockDecisionInsideAgentLoop() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "block-danger"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        return ExtensionToolCallDecision.block("policy: do_thing is disabled");
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = toolThenText(call("{\"x\":1}"));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = baseAgent(model, exec, registry);
        AgentResult result = agent.run(AgentRequest.builder().input("go").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(0, exec.executed.size());
        Assert.assertEquals(1, result.getToolResults().size());
        String output = result.getToolResults().get(0).getOutput();
        Assert.assertTrue("blocked reason must be fed back to the model: " + output,
                output.contains("policy: do_thing is disabled"));
    }

    @Test
    public void shouldApplyExtensionModifyDecisionInsideAgentLoop() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "rewrite-args"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        return ExtensionToolCallDecision.modify(null, "{\"x\":42}");
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = toolThenText(call("{\"x\":1}"));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = baseAgent(model, exec, registry);
        AgentResult result = agent.run(AgentRequest.builder().input("go").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(1, exec.executed.size());
        Assert.assertEquals("executor must see the rewritten arguments",
                "{\"x\":42}", exec.executed.get(0).getArguments());
        Assert.assertEquals("callId must be preserved through modify",
                "c1", exec.executed.get(0).getCallId());
    }

    @Test
    public void shouldApplyExtensionRouteToInsideAgentLoop() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "sandbox-everything"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        return ExtensionToolCallDecision.routeTo("fake", null, "echo routed");
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = toolThenText(call("{\"x\":1}"));
        RecordingExecutor exec = new RecordingExecutor();
        FakeSandboxProvider provider = new FakeSandboxProvider(
                SandboxResult.builder().exitCode(Integer.valueOf(0)).stdout("sandboxed-output").build());

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolRegistry(doThingRegistry())
                .toolExecutor(exec)
                .sandboxProvider(provider)
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();
        agent.run(AgentRequest.builder().input("go").build());

        Assert.assertEquals("local executor must be bypassed when routed to sandbox",
                0, exec.executed.size());
        Assert.assertEquals("a sandbox session must be created", 1, provider.created.size());
        Assert.assertEquals("fake", provider.created.get(0).getProviderId());
        String fedBack = JSON.toJSONString(model.prompts.get(1));
        Assert.assertTrue("sandbox output must be fed back to the model: " + fedBack,
                fedBack.contains("sandboxed-output"));
    }

    @Test
    public void shouldApplyExtensionPromptModification() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerPrompt(new ExtensionPromptInterceptor() {
                    public String name() { return "redact"; }
                    public ExtensionPromptDecision intercept(ExtensionPromptRequest request) {
                        return ExtensionPromptDecision.modify(request.getInput() + " [tenant=acme]");
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = new ScriptedModelClient(Collections.singletonList(
                AgentModelResult.builder().outputText("done").build()));

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();
        agent.run(AgentRequest.builder().input("hello").build());

        Assert.assertEquals(1, model.prompts.size());
        String sent = JSON.toJSONString(model.prompts.get(0));
        Assert.assertTrue("model must see the rewritten input: " + sent,
                sent.contains("[tenant=acme]"));
    }

    @Test
    public void shouldApplyExtensionPromptBlock() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerPrompt(new ExtensionPromptInterceptor() {
                    public String name() { return "block-prompt"; }
                    public ExtensionPromptDecision intercept(ExtensionPromptRequest request) {
                        return ExtensionPromptDecision.block("input rejected by policy");
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = new ScriptedModelClient(Collections.singletonList(
                AgentModelResult.builder().outputText("done").build()));

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();
        AgentResult result = agent.run(AgentRequest.builder().input("hello").build());

        Assert.assertEquals("blocked reason becomes the run output",
                "PROMPT_BLOCKED: input rejected by policy", result.getOutputText());
        Assert.assertTrue("model must never be invoked for a blocked prompt",
                model.prompts.isEmpty());
    }

    @Test
    public void shouldApplyExtensionModelRequestOverrides() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerModelRequest(new ExtensionModelRequestInterceptor() {
                    public String name() { return "tenant-scope"; }
                    public ExtensionModelRequestDecision intercept(ExtensionModelRequest request) {
                        return ExtensionModelRequestDecision.withSystemPrompt("tenant system")
                                .merge(ExtensionModelRequestDecision.withTemperature(Double.valueOf(0.1)));
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = new ScriptedModelClient(Collections.singletonList(
                AgentModelResult.builder().outputText("done").build()));

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .systemPrompt("original system")
                .temperature(Double.valueOf(0.7))
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();
        agent.run(AgentRequest.builder().input("hello").build());

        Assert.assertEquals(1, model.prompts.size());
        AgentPrompt sent = model.prompts.get(0);
        Assert.assertEquals("tenant system", sent.getSystemPrompt());
        Assert.assertEquals(Double.valueOf(0.1), sent.getTemperature());
    }

    @Test
    public void shouldEvaluateHostInterceptorBeforeExtensions() throws Exception {
        final int[] extensionCalls = {0};
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "never-reached"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        extensionCalls[0]++;
                        return ExtensionToolCallDecision.allow();
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = toolThenText(call("{\"x\":1}"));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolRegistry(doThingRegistry())
                .toolExecutor(exec)
                .toolInterceptor((c, ctx) -> ToolCallDecision.block("host policy wins"))
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();
        agent.run(AgentRequest.builder().input("go").build());

        Assert.assertEquals("host block must short-circuit before extension interceptors",
                0, extensionCalls[0]);
        Assert.assertEquals(0, exec.executed.size());
    }

    @Test
    public void shouldChainExtensionModifyThroughHostModify() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "second-rewrite"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        // sees the host-rewritten arguments and rewrites again
                        Assert.assertEquals("{\"x\":2}", request.getArguments());
                        return ExtensionToolCallDecision.modify(null, "{\"x\":3}");
                    }
                });
            }
        }).enable("intercept-pack");
        ScriptedModelClient model = toolThenText(call("{\"x\":1}"));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolRegistry(doThingRegistry())
                .toolExecutor(exec)
                .toolInterceptor((c, ctx) -> ToolCallDecision.modify(AgentToolCall.builder()
                        .name(c.getName()).arguments("{\"x\":2}").callId(c.getCallId()).build()))
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();
        agent.run(AgentRequest.builder().input("go").build());

        Assert.assertEquals(1, exec.executed.size());
        Assert.assertEquals("extension must see and re-modify the host-modified call",
                "{\"x\":3}", exec.executed.get(0).getArguments());
    }

    @Test
    public void shouldExposeInterceptorsThroughAgentToolsSnapshot() {
        ExtensionRegistry registry = ExtensionRegistry.of(new InterceptorExtension() {
            public void apply(ExtensionContext context) {
                context.interceptors().registerToolCall(new ExtensionToolCallInterceptor() {
                    public String name() { return "t1"; }
                    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
                        return ExtensionToolCallDecision.allow();
                    }
                });
                context.interceptors().registerPrompt(new ExtensionPromptInterceptor() {
                    public String name() { return "p1"; }
                    public ExtensionPromptDecision intercept(ExtensionPromptRequest request) {
                        return ExtensionPromptDecision.allow();
                    }
                });
                context.interceptors().registerModelRequest(new ExtensionModelRequestInterceptor() {
                    public String name() { return "m1"; }
                    public ExtensionModelRequestDecision intercept(ExtensionModelRequest request) {
                        return ExtensionModelRequestDecision.allow();
                    }
                });
            }
        }).enable("intercept-pack");

        ExtensionAgentTools tools = ExtensionAgentTools.from(registry);
        Assert.assertEquals(1, tools.getToolCallInterceptors().size());
        Assert.assertEquals(1, tools.getPromptInterceptors().size());
        Assert.assertEquals(1, tools.getModelRequestInterceptors().size());
    }
}
