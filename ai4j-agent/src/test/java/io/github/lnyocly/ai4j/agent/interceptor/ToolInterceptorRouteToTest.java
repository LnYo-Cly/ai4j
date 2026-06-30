package io.github.lnyocly.ai4j.agent.interceptor;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic routeTo tests: a tool call is redirected to a fake sandbox (no real E2B/Daytona —
 * the routing logic is what's under test). Verifies the command runs in the sandbox, the local
 * executor is bypassed, and the sandbox output is fed back to the model. Also: routeTo with no
 * provider configured surfaces as a blocked result.
 */
public class ToolInterceptorRouteToTest {

    private static AgentToolRegistry bashRegistry() {
        Tool.Function fn = new Tool.Function();
        fn.setName("bash");
        fn.setDescription("run a shell command");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        Tool.Function.Property cmdProp = new Tool.Function.Property();
        cmdProp.setType("string");
        props.put("command", cmdProp);
        param.setProperties(props);
        param.setRequired(Collections.singletonList("command"));
        fn.setParameters(param);
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));
    }

    static final class FakeSandboxSession implements SandboxSession {
        final List<SandboxCommand> executed = new ArrayList<SandboxCommand>();
        final SandboxResult result;
        boolean closed = false;
        FakeSandboxSession(SandboxResult result) { this.result = result; }
        public String getSessionId() { return "fake-sid"; }
        public String getProviderId() { return "fake"; }
        public SandboxSpec getSpec() { return null; }
        public SandboxStatus getStatus() { return SandboxStatus.RUNNING; }
        public SandboxResult execute(SandboxCommand command) { executed.add(command); return result; }
        public boolean cancel(String commandId) { return false; }
        public List<SandboxArtifact> listArtifacts() { return Collections.<SandboxArtifact>emptyList(); }
        public void close() { closed = true; }
    }

    static final class FakeSandboxProvider implements SandboxProvider {
        final List<SandboxSpec> created = new ArrayList<SandboxSpec>();
        final SandboxResult result;
        FakeSandboxProvider(SandboxResult result) { this.result = result; }
        public String getProviderId() { return "fake"; }
        public boolean supports(SandboxSpec spec) { return true; }
        public SandboxSession createSession(SandboxSpec spec) { created.add(spec); return new FakeSandboxSession(result); }
    }

    @Test
    public void routeToRunsCommandInSandboxBypassingLocalExecutor() throws Exception {
        AgentToolCall requested = AgentToolCall.builder().name("bash").callId("c1").arguments("{\"command\":\"rm -rf x\"}").build();
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
        ToolInterceptorTest.RecordingExecutor localExec = new ToolInterceptorTest.RecordingExecutor();

        SandboxResult sandboxResult = SandboxResult.builder().exitCode(Integer.valueOf(0)).stdout("sandboxed-output").build();
        FakeSandboxProvider provider = new FakeSandboxProvider(sandboxResult);
        SandboxSpec spec = SandboxSpec.builder().providerId("fake").build();
        SandboxCommand cmd = SandboxCommand.builder().command("rm -rf x").build();

        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(localExec)
                .toolRegistry(bashRegistry())
                .sandboxProvider(provider)
                .toolInterceptor((c, ctx) -> ToolCallDecision.routeTo(spec, cmd))
                .build();
        agent.newSession().run("delete something");

        assertEquals("local executor must be bypassed when routed to sandbox", 0, localExec.executed.size());
        assertEquals("a sandbox session must be created", 1, provider.created.size());
        assertTrue("model must be invoked again after the sandbox result", model.prompts.size() >= 2);
        String fedBack = JSON.toJSONString(model.prompts.get(1));
        assertTrue("sandbox output must be fed back to the model: " + fedBack,
                fedBack.contains("SANDBOX_RESULT") && fedBack.contains("sandboxed-output"));
    }

    @Test
    public void routeToWithoutProviderSurfacesAsBlocked() throws Exception {
        AgentToolCall requested = AgentToolCall.builder().name("bash").callId("c1").arguments("{\"command\":\"rm -rf x\"}").build();
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
        ToolInterceptorTest.RecordingExecutor localExec = new ToolInterceptorTest.RecordingExecutor();

        // no sandboxProvider configured
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(localExec)
                .toolRegistry(bashRegistry())
                .toolInterceptor((c, ctx) -> ToolCallDecision.routeTo(
                        SandboxSpec.builder().providerId("fake").build(),
                        SandboxCommand.builder().command("rm -rf x").build()))
                .build();
        agent.newSession().run("delete something");

        assertEquals(0, localExec.executed.size());
        String fedBack = JSON.toJSONString(model.prompts.get(1));
        assertTrue("routeTo without provider must feed back a blocked reason: " + fedBack,
                fedBack.contains("TOOL_BLOCKED") && fedBack.contains("no sandbox provider"));
    }
}
