package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DeterministicToolExecutorTest {

    @Test
    public void routesByToolNameToFixedOutput() throws Exception {
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("get_weather", "{\"temp\":26}");

        String out = tools.execute(ToolCalls.function("get_weather", "{\"city\":\"sh\"}"));
        assertEquals("{\"temp\":26}", out);
    }

    @Test
    public void routesByToolNameToDynamicHandler() throws Exception {
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("echo", call -> call.getArguments());

        String out = tools.execute(ToolCalls.function("echo", "{\"x\":1}"));
        assertEquals("{\"x\":1}", out);
    }

    @Test
    public void unknownToolThrowsByDefault() {
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("known", "ok");
        try {
            tools.execute(ToolCalls.function("unknown", "{}"));
            fail("expected IllegalArgumentException for unregistered tool");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown"));
        } catch (Exception e) {
            fail("expected IllegalArgumentException, got " + e);
        }
    }

    @Test
    public void otherwiseHandlesUnregisteredTools() throws Exception {
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("known", "ok")
                .otherwise(call -> "fallback:" + call.getName());

        assertEquals("fallback:anything", tools.execute(ToolCalls.function("anything", "{}")));
    }

    @Test
    public void recordsEveryExecutedCall() throws Exception {
        DeterministicToolExecutor tools = new DeterministicToolExecutor()
                .on("a", "1")
                .on("b", "2");
        tools.execute(ToolCalls.function("a", "{}"));
        tools.execute(ToolCalls.function("b", "{}"));
        tools.execute(ToolCalls.function("a", "{}"));

        assertEquals(3, tools.getCalls().size());
        assertEquals(2, tools.countFor("a"));
        assertEquals(1, tools.getCallsFor("b").size());
        AgentToolCall second = tools.getCallsFor("a").get(1);
        assertEquals("a", second.getName());
    }
}
