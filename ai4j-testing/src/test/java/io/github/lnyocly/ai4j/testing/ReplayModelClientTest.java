package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.Collections;

public class ReplayModelClientTest {

    @Test
    public void parsesFixtureAndReplaysResponsesInOrder() {
        ReplayModelClient client = loadWeatherFixture();

        Assert.assertEquals("deepseek-weather-toolcall", client.getFixture().getName());
        Assert.assertEquals("deepseek", client.getFixture().getRecordedFrom());
        Assert.assertEquals(2, client.getFixture().getExchanges().size());

        AgentPrompt firstPrompt = prompt("weather in shanghai?");
        firstPrompt.setTools(Collections.<Object>singletonList(
                Collections.singletonMap("name", "get_weather")));
        AgentModelResult first = client.create(firstPrompt);
        Assert.assertNotNull(first.getToolCalls());
        Assert.assertEquals("get_weather", first.getToolCalls().get(0).getName());
        Assert.assertEquals("call_weather_1", first.getToolCalls().get(0).getCallId());
        Assert.assertEquals(Long.valueOf(120L), first.getInputTokens());

        AgentModelResult second = client.create(prompt("tool result: sunny"));
        Assert.assertEquals("It is sunny in Shanghai, around 26C.", second.getOutputText());
        Assert.assertEquals(2, client.getInvocationCount());
    }

    @Test
    public void promptPinMismatchFailsAtTheInvocation() {
        ReplayModelClient client = loadWeatherFixture();
        try {
            client.create(prompt("nothing about the pinned city here"));
            Assert.fail("expected prompt pin mismatch to fail");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("deepseek-weather-toolcall"));
            Assert.assertTrue(e.getMessage().contains("shanghai"));
        }
    }

    @Test
    public void malformedFixtureFailsFastWithReason() {
        try {
            ReplayModelClient.parse("{\"name\":\"empty\"}");
            Assert.fail("expected missing exchanges to fail");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("exchanges"));
        }
    }

    private static ReplayModelClient loadWeatherFixture() {
        try {
            InputStream in = ReplayModelClientTest.class
                    .getResourceAsStream("/fixtures/deepseek-weather-toolcall.json");
            Assert.assertNotNull("fixture resource must exist", in);
            return ReplayModelClient.load(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgentPrompt prompt(String userText) {
        AgentPrompt prompt = new AgentPrompt();
        prompt.setItems(Collections.<Object>singletonList(AgentInputItem.userMessage(userText)));
        return prompt;
    }
}
