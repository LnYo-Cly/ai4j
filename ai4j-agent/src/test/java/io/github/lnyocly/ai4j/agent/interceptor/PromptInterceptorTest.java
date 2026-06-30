package io.github.lnyocly.ai4j.agent.interceptor;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic agent-loop tests for the {@link PromptInterceptor} (no real LLM). Verifies block
 * (agent returns without calling the model), modify (rewritten input reaches the model), and allow.
 */
public class PromptInterceptorTest {

    private static ToolInterceptorTest.ScriptedModelClient finalAnswerModel() {
        // emits a final answer on the first call (no tool calls)
        return new ToolInterceptorTest.ScriptedModelClient(Collections.singletonList(
                AgentModelResult.builder().outputText("done").build()));
    }

    @Test
    public void blockStopsBeforeTheModelRuns() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = finalAnswerModel();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .promptInterceptor((input, ctx) -> PromptDecision.block("forbidden topic"))
                .build();
        AgentResult result = agent.newSession().run("tell me a forbidden thing");

        assertEquals("blocked prompt must NOT reach the model", 0, model.prompts.size());
        assertTrue("result must carry the block reason", result.getOutputText().contains("PROMPT_BLOCKED"));
        assertTrue(result.getOutputText().contains("forbidden topic"));
    }

    @Test
    public void modifyRewritesTheInputBeforeTheModelSeesIt() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = finalAnswerModel();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .promptInterceptor((input, ctx) -> PromptDecision.modify(input.toUpperCase()))
                .build();
        agent.newSession().run("hello world");

        assertEquals("model must be called once", 1, model.prompts.size());
        String seenByModel = JSON.toJSONString(model.prompts.get(0));
        assertTrue("model must see the MODIFIED (uppercased) input: " + seenByModel,
                seenByModel.contains("HELLO WORLD"));
    }

    @Test
    public void allowPassesTheInputThrough() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = finalAnswerModel();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .promptInterceptor((input, ctx) -> PromptDecision.allow())
                .build();
        agent.newSession().run("hello world");

        assertEquals(1, model.prompts.size());
        assertTrue(JSON.toJSONString(model.prompts.get(0)).contains("hello world"));
    }

    @Test
    public void noInterceptorBehavesLikeAllow() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = finalAnswerModel();
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .build();
        AgentResult result = agent.newSession().run("hello world");
        assertEquals(1, model.prompts.size());
        assertTrue(result.getOutputText().contains("done"));
    }
}
