package io.github.lnyocly.ai4j.agent.interceptor;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
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
 * Deterministic agent-loop tests for the tool interceptor (no real LLM — interception is runtime
 * logic). A scripted model client emits one tool call then a final answer; the interceptor decides
 * block / modify / allow and we assert the runtime honors it.
 */
public class ToolInterceptorTest {

    private static AgentToolCall call(String args) {
        return AgentToolCall.builder().name("do_thing").callId("c1").arguments(args).build();
    }

    static AgentToolRegistry registry() {
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

    /** model emits toolCall on first call, final text on the second. */
    static final class ScriptedModelClient implements AgentModelClient {
        final List<AgentModelResult> scripted;
        final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();
        int idx = 0;
        ScriptedModelClient(List<AgentModelResult> scripted) { this.scripted = scripted; }
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            return scripted.get(Math.min(idx++, scripted.size() - 1));
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener l) { return create(prompt); }
    }

    /** records every call it actually executed */
    static final class RecordingExecutor implements ToolExecutor {
        final List<AgentToolCall> executed = new ArrayList<AgentToolCall>();
        public String execute(AgentToolCall c) {
            executed.add(c);
            return "real-output";
        }
    }

    @Test
    public void blockVetoesTheCallAndFeedsReasonBackToModel() throws Exception {
        AgentToolCall requested = call("{\"x\":1}");
        ScriptedModelClient model = new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("ok").build()));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolExecutor(exec)
                .toolRegistry(registry())
                .toolInterceptor((c, ctx) -> ToolCallDecision.block("forbidden by policy"))
                .build();
        AgentResult result = agent.newSession().run("do the thing");

        assertEquals("blocked call must NOT reach the executor", 0, exec.executed.size());
        assertTrue("model must be invoked again after the block", model.prompts.size() >= 2);
        String fedBack = JSON.toJSONString(model.prompts.get(1));
        assertTrue("block reason must be fed back to the model: " + fedBack,
                fedBack.contains("TOOL_BLOCKED") && fedBack.contains("forbidden by policy"));
        assertTrue(result.getOutputText().contains("ok"));
    }

    @Test
    public void modifyRewritesTheCallBeforeExecution() throws Exception {
        AgentToolCall requested = call("{\"x\":1}");
        AgentToolCall rewritten = AgentToolCall.builder().name("do_thing").callId("c1").arguments("{\"x\":2}").build();
        ScriptedModelClient model = new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolExecutor(exec)
                .toolRegistry(registry())
                .toolInterceptor((c, ctx) -> ToolCallDecision.modify(rewritten))
                .build();
        agent.newSession().run("do the thing");

        assertEquals("modified call must execute exactly once", 1, exec.executed.size());
        assertEquals("executor must receive the MODIFIED call, not the original",
                "{\"x\":2}", exec.executed.get(0).getArguments());
    }

    @Test
    public void allowRunsTheOriginalCall() throws Exception {
        AgentToolCall requested = call("{\"x\":1}");
        ScriptedModelClient model = new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolExecutor(exec)
                .toolRegistry(registry())
                .toolInterceptor((c, ctx) -> ToolCallDecision.allow())
                .build();
        agent.newSession().run("do the thing");

        assertEquals(1, exec.executed.size());
        assertEquals("{\"x\":1}", exec.executed.get(0).getArguments());
    }

    @Test
    public void noInterceptorBehavesLikeAllow() throws Exception {
        AgentToolCall requested = call("{\"x\":1}");
        ScriptedModelClient model = new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
        RecordingExecutor exec = new RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolExecutor(exec)
                .toolRegistry(registry())
                .build();
        agent.newSession().run("do the thing");

        assertEquals("no interceptor => original call runs", 1, exec.executed.size());
    }

    @Test
    public void afterToolCallBlockReplacesTheResultFedBackToModel() throws Exception {
        AgentToolCall requested = call("{\"x\":1}");
        ScriptedModelClient model = new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(requested)).build(),
                AgentModelResult.builder().outputText("done").build()));
        RecordingExecutor exec = new RecordingExecutor();

        // allow before, block after (e.g. output leaked a secret). Anonymous class overrides the
        // default afterToolCall in addition to the abstract beforeToolCall.
        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolExecutor(exec)
                .toolRegistry(registry())
                .toolInterceptor(new ToolInterceptor() {
                    @Override
                    public ToolCallDecision beforeToolCall(AgentToolCall c, AgentContext ctx) {
                        return ToolCallDecision.allow();
                    }
                    @Override
                    public ToolCallDecision afterToolCall(AgentToolCall c, String output, AgentContext ctx) {
                        return ToolCallDecision.block("output leaked a secret");
                    }
                })
                .build();
        agent.newSession().run("do the thing");

        assertEquals("tool must still run (block is post-execution)", 1, exec.executed.size());
        assertTrue("model must be invoked again after the post-block", model.prompts.size() >= 2);
        String fedBack = JSON.toJSONString(model.prompts.get(1));
        assertTrue("blocked result must be fed back: " + fedBack,
                fedBack.contains("TOOL_BLOCKED") && fedBack.contains("leaked a secret"));
    }
}
