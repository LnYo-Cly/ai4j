package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Golden demo: a committed fixture captured from a DeepSeek-style tool-call conversation
 * replays through the real ReAct runtime with zero provider access — the fixture drives the
 * model side, the deterministic executor answers the tool call, and prompt pins inside the
 * fixture assert what the runtime actually sent.
 */
public class ReplayGoldenTest {

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
    public void recordedToolCallConversationReplaysDeterministically() throws Exception {
        InputStream in = getClass().getResourceAsStream("/fixtures/deepseek-weather-toolcall.json");
        assertNotNull(in);
        ReplayModelClient model = ReplayModelClient.load(in);
        model.failWhenExhausted(true);
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("get_weather", "{\"temp\":26,\"sky\":\"sunny\"}");

        Agent agent = Agents.react()
                .modelClient(model)
                .model("deepseek-chat")
                .toolRegistry(weatherRegistry())
                .toolExecutor(tools)
                .build();

        AgentResult result = agent.newSession().run("weather in shanghai?");

        assertEquals("It is sunny in Shanghai, around 26C.", result.getOutputText());
        assertEquals(1, tools.countFor("get_weather"));
        assertEquals("{\"city\":\"shanghai\"}", tools.getCallsFor("get_weather").get(0).getArguments());
        assertEquals("fixture has exactly 2 exchanges and exhaustion must fail",
                2, model.getInvocationCount());
        assertEquals(0, model.remaining());
    }
}
