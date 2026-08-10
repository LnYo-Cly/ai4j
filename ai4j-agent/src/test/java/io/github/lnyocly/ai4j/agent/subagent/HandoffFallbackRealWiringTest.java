package io.github.lnyocly.ai4j.agent.subagent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.HandoffFailureAction;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;

/**
 * FALLBACK_TO_PRIMARY under real AgentBuilder wiring.
 *
 * <p>The delegate passed to SubAgentToolExecutor is a ToolUtilExecutor whose
 * allowedToolNames was captured before subagents were merged, so it cannot serve
 * the subagent tool name. Before the fix this surfaced a misleading
 * "Tool not allowed: delegate_xxx" error to the model. After the fix it surfaces
 * a clean, intentional "handoff denied / no primary handler" message.
 */
public class HandoffFallbackRealWiringTest {

    @Test
    public void fallbackToPrimaryYieldsCleanUnavailableNotRoutingError() throws Exception {
        ScriptedClient subClient = new ScriptedClient();
        Agent sub = Agents.react().modelClient(subClient).model("sub").build();
        SubAgentDefinition def = SubAgentDefinition.builder()
                .name("worker").description("d").toolName("delegate_worker").agent(sub).build();

        // A real base tool so the default ToolUtilExecutor is non-null (otherwise the
        // delegate is null and fallback degrades cleanly, masking the real path).
        io.github.lnyocly.ai4j.platform.openai.tool.Tool baseTool =
                new io.github.lnyocly.ai4j.platform.openai.tool.Tool(
                        "function",
                        new io.github.lnyocly.ai4j.platform.openai.tool.Tool.Function(
                                "lookup", "base tool", null));

        ScriptedClient parentClient = new ScriptedClient();
        parentClient.enqueue(toolCall("c1", "delegate_worker", "{}"));
        parentClient.enqueue(text("parent-handled"));

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("parent")
                .toolRegistry(new io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry(
                        Collections.singletonList((Object) baseTool)))
                .subAgent(def)
                .handoffPolicy(HandoffPolicy.builder()
                        .allowedTools(Collections.singleton("delegate_other"))
                        .onDenied(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                        .build())
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("go").build());
        String toolOutput = result.getToolResults().get(0).getOutput();

        // The fix: a denied handoff with no usable primary handler must report an intentional
        // "handoff unavailable" message (not surface as a TOOL_ERROR wrapping the internal
        // "Tool not allowed" routing failure).
        Assert.assertFalse("fallback must not surface as a TOOL_ERROR: " + toolOutput,
                toolOutput.startsWith("TOOL_ERROR:"));
        Assert.assertTrue("fallback should explain the handoff is unavailable: " + toolOutput,
                toolOutput.contains("was denied") && toolOutput.contains("no primary tool executor"));
    }

    private static AgentModelResult text(String t) {
        return AgentModelResult.builder().outputText(t)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>()).build();
    }

    private static AgentModelResult toolCall(String id, String name, String args) {
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .callId(id).name(name).arguments(args).type("function_call").build()))
                .memoryItems(new ArrayList<Object>()).build();
    }

    static class ScriptedClient implements AgentModelClient {
        final Deque<AgentModelResult> q = new ArrayDeque<>();
        void enqueue(AgentModelResult r) { q.addLast(r); }
        @Override public AgentModelResult create(AgentPrompt p) {
            AgentModelResult r = q.pollFirst();
            if (r == null) throw new IllegalStateException("exhausted");
            return r;
        }
        @Override public AgentModelResult createStream(AgentPrompt p, AgentModelStreamListener l) {
            throw new UnsupportedOperationException();
        }
    }
}
