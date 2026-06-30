package io.github.lnyocly.ai4j.agent.interceptor;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the {@link AgentHooks} facade (the pi-like named per-event sugar): preToolUse / postToolUse
 * compose into one ToolInterceptor, userPromptSubmit into a PromptInterceptor, and observe events
 * (stop) into a lifecycle hook — all from a single {@code .hooks(...)} call.
 */
public class AgentHooksTest {

    private static AgentToolCall toolCall() {
        return AgentToolCall.builder().name("do_thing").callId("c1").arguments("{\"x\":1}").build();
    }

    private static ToolInterceptorTest.ScriptedModelClient toolThenFinal() {
        return new ToolInterceptorTest.ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(toolCall())).build(),
                AgentModelResult.builder().outputText("done").build()));
    }

    private static ToolInterceptorTest.ScriptedModelClient finalOnly() {
        return new ToolInterceptorTest.ScriptedModelClient(Collections.singletonList(
                AgentModelResult.builder().outputText("done").build()));
    }

    @Test
    public void preToolUseViaFacadeBlocksTheCall() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = toolThenFinal();
        ToolInterceptorTest.RecordingExecutor exec = new ToolInterceptorTest.RecordingExecutor();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(exec).toolRegistry(ToolInterceptorTest.registry())
                .hooks(h -> h.preToolUse((call, ctx) -> ToolCallDecision.block("vetoed")))
                .build();
        agent.newSession().run("do it");
        assertEquals("preToolUse block must skip the executor", 0, exec.executed.size());
        assertTrue(JSON.toJSONString(model.prompts.get(1)).contains("TOOL_BLOCKED"));
    }

    @Test
    public void postToolUseViaFacadeBlocksTheResult() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = toolThenFinal();
        ToolInterceptorTest.RecordingExecutor exec = new ToolInterceptorTest.RecordingExecutor();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(exec).toolRegistry(ToolInterceptorTest.registry())
                .hooks(h -> h.postToolUse((call, out, ctx) -> ToolCallDecision.block("bad output")))
                .build();
        agent.newSession().run("do it");
        assertEquals("tool still runs (post-block is after execution)", 1, exec.executed.size());
        assertTrue(JSON.toJSONString(model.prompts.get(1)).contains("TOOL_BLOCKED"));
    }

    @Test
    public void userPromptSubmitViaFacadeBlocksBeforeModel() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = finalOnly();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .hooks(h -> h.userPromptSubmit((input, ctx) -> PromptDecision.block("forbidden")))
                .build();
        AgentResult result = agent.newSession().run("hello");
        assertEquals(0, model.prompts.size());
        assertTrue(result.getOutputText().contains("PROMPT_BLOCKED"));
    }

    @Test
    public void stopObserveFiresAndComposesAlongsideInterception() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = toolThenFinal();
        ToolInterceptorTest.RecordingExecutor exec = new ToolInterceptorTest.RecordingExecutor();
        final List<AgentLifecycleEvent> stopEvents = new ArrayList<AgentLifecycleEvent>();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(exec).toolRegistry(ToolInterceptorTest.registry())
                .hooks(h -> h
                        .preToolUse((call, ctx) -> ToolCallDecision.allow())
                        .stop(ev -> stopEvents.add(ev)))
                .build();
        agent.newSession().run("do it");
        assertEquals("interception (allow) lets the tool run", 1, exec.executed.size());
        assertTrue("observe (stop) must fire on turn end", !stopEvents.isEmpty());
    }
}
