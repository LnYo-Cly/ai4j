package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScriptedModelClientTest {

    @Test
    public void replaysResultsInEnqueueOrder() {
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueueText("first")
                .enqueue(ModelResults.toolCall("get_weather", "{}"))
                .enqueueText("last");

        assertEquals("first", model.create(prompt()).getOutputText());
        assertEquals("get_weather", model.create(prompt()).getToolCalls().get(0).getName());
        assertEquals("last", model.create(prompt()).getOutputText());
        assertEquals(0, model.remaining());
    }

    @Test
    public void recordsEveryPrompt() {
        ScriptedModelClient model = new ScriptedModelClient().enqueueText("a").enqueueText("b");
        model.create(prompt("one"));
        model.create(prompt("two"));

        assertEquals(2, model.getInvocationCount());
        assertEquals(2, model.getPrompts().size());
        assertSame(model.getPrompts().get(1), model.getLastPrompt());
    }

    @Test
    public void exhaustedScriptReturnsEmptyResultByDefault() {
        ScriptedModelClient model = new ScriptedModelClient();
        AgentModelResult result = model.create(prompt());
        assertNotNull(result);
        assertNull(result.getOutputText());
        assertNull(result.getToolCalls());
    }

    @Test
    public void exhaustedScriptThrowsWhenFailWhenExhausted() {
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueueText("only")
                .failWhenExhausted(true);
        model.create(prompt());
        try {
            model.create(prompt());
            fail("expected IllegalStateException when script is exhausted");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("exhausted"));
        }
    }

    @Test
    public void streamReplaysSameScriptAsEvents() {
        AgentToolCall call = ToolCalls.function("c1", "get_weather", "{}");
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueue(ModelResults.toolCall(call))
                .enqueue(ModelResults.reasoned("thinking", "sunny"));
        RecordingStreamListener listener = new RecordingStreamListener();

        AgentModelResult first = model.createStream(prompt(), listener);
        assertSame(call, first.getToolCalls().get(0));
        assertEquals(1, listener.getToolCalls().size());
        assertNotNull(listener.getCompleted());

        RecordingStreamListener second = new RecordingStreamListener();
        model.createStream(prompt(), second);
        assertEquals("thinking", second.getReasoningDeltas().get(0));
        assertEquals("sunny", second.getText());
        assertEquals("sunny", second.getCompleted().getOutputText());
    }

    @Test
    public void streamPropagatesExhaustionAsError() {
        ScriptedModelClient model = new ScriptedModelClient().failWhenExhausted(true);
        RecordingStreamListener listener = new RecordingStreamListener();
        try {
            model.createStream(prompt(), listener);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals(1, listener.getErrors().size());
        }
    }

    private static AgentPrompt prompt() {
        return prompt(null);
    }

    private static AgentPrompt prompt(String model) {
        return AgentPrompt.builder().model(model).build();
    }
}
