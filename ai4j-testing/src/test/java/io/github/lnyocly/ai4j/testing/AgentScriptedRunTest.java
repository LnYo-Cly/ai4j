package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end proof that the public fixtures drive a real ReAct loop: scripted model emits one
 * tool call, the deterministic executor answers it, the model sees the result and finishes.
 */
public class AgentScriptedRunTest {

    private static AgentToolRegistry weatherRegistry() {
        Tool.Function fn = new Tool.Function();
        fn.setName("get_weather");
        fn.setDescription("get weather for a city");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        param.setProperties(new HashMap<String, Tool.Function.Property>());
        param.setRequired(Collections.<String>emptyList());
        fn.setParameters(param);
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));
    }

    @Test
    public void scriptedToolCallRoundTripDrivesRealReActLoop() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueueToolCall("get_weather", "{\"city\":\"shanghai\"}")
                .enqueueText("It is sunny in Shanghai");
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("get_weather", "{\"temp\":26,\"sky\":\"sunny\"}");

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .toolRegistry(weatherRegistry())
                .toolExecutor(tools)
                .build();

        AgentResult result = agent.newSession().run("weather in shanghai?");

        assertEquals("It is sunny in Shanghai", result.getOutputText());
        assertEquals(1, tools.countFor("get_weather"));
        assertEquals("{\"city\":\"shanghai\"}", tools.getCallsFor("get_weather").get(0).getArguments());
        assertEquals("tool answer must feed a second model invocation", 2, model.getInvocationCount());
        String secondPrompt = String.valueOf(model.getPrompts().get(1));
        assertTrue("tool output must appear in the follow-up prompt",
                secondPrompt.contains("sunny") || secondPrompt.contains("temp"));
    }

    @Test
    public void stubModelClientAnswersDirectlyWithoutTools() throws Exception {
        StubModelClient model = new StubModelClient("always the same");

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .build();

        AgentResult result = agent.newSession().run("anything");
        assertEquals("always the same", result.getOutputText());
        assertEquals(1, model.getInvocationCount());
    }
}
