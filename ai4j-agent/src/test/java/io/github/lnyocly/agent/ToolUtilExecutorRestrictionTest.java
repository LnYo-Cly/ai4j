package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolUtilExecutor;
import org.junit.Test;

import java.util.Collections;

public class ToolUtilExecutorRestrictionTest {

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsUnknownTool() throws Exception {
        ToolUtilExecutor executor = new ToolUtilExecutor(Collections.singleton("allowed_tool"));
        AgentToolCall call = AgentToolCall.builder()
                .name("not_allowed")
                .arguments("{}")
                .build();
        executor.execute(call);
    }
}
