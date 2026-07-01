package io.github.lnyocly.ai4j.agent.interceptor;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Tests {@link ModelRequestHook} — modify the full AgentPrompt before the model call. */
public class ModelRequestHookTest {

    @Test
    public void beforeModelRequestCanModifyTemperature() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(
                Collections.singletonList(AgentModelResult.builder().outputText("done").build()));
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .modelRequestHook((prompt, ctx) -> prompt.toBuilder().temperature(0.0).build())
                .build();
        agent.newSession().run("hello");

        assertEquals(1, model.prompts.size());
        assertEquals(Double.valueOf(0.0), model.prompts.get(0).getTemperature());
    }

    @Test
    public void beforeModelRequestViaFacade() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(
                Collections.singletonList(AgentModelResult.builder().outputText("done").build()));
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .hooks(h -> h.beforeModelRequest((prompt, ctx) ->
                        prompt.toBuilder().systemPrompt("injected").build()))
                .build();
        agent.newSession().run("hello");

        assertEquals(1, model.prompts.size());
        assertEquals("injected", model.prompts.get(0).getSystemPrompt());
    }

    @Test
    public void nullReturnKeepsOriginalPrompt() throws Exception {
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(
                Collections.singletonList(AgentModelResult.builder().outputText("done").build()));
        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .modelRequestHook((prompt, ctx) -> null) // pass-through
                .build();
        AgentResult result = agent.newSession().run("hello");
        assertTrue(result.getOutputText().contains("done"));
    }
}
