package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;
import io.github.lnyocly.ai4j.agent.tool.AgentToolVisibility;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AgentToolVisibilityTest {

    @Test
    public void namedAndExcludingPreserveOrderAndHandleSerializedTools() {
        Tool read = tool("read_file");
        Tool bash = tool("bash");
        Map<String, Object> serialized = new HashMap<String, Object>();
        Map<String, Object> function = new HashMap<String, Object>();
        function.put("name", "edit");
        serialized.put("type", "function");
        serialized.put("function", function);
        Object unnamed = new Object();
        List<Object> registered = Arrays.<Object>asList(read, bash, serialized, unnamed);

        List<Object> named = AgentToolVisibility.named(Arrays.asList("bash", "edit"))
                .filter(registered);
        assertEquals(Arrays.asList(bash, serialized), named);

        List<Object> excluding = AgentToolVisibility.excluding(Collections.singleton("bash"))
                .filter(registered);
        assertEquals(Arrays.asList(read, serialized, unnamed), excluding);
    }

    @Test
    public void composedVisibilityUsesLogicalPredicates() {
        Tool read = tool("read_file");
        Tool bash = tool("bash");
        AgentToolVisibility view = AgentToolVisibility.named(Arrays.asList("read_file", "bash"))
                .and(AgentToolVisibility.excluding(Collections.singleton("bash")));
        assertTrue(view.includes(read));
        assertFalse(view.includes(bash));
        assertTrue(AgentToolVisibility.named(Collections.singleton("read_file"))
                .or(AgentToolVisibility.named(Collections.singleton("bash"))).includes(bash));
    }

    @Test
    public void compositeRegistryUsesFirstNamedToolAndKeepsUnnamedItems() {
        Tool first = tool("duplicate");
        Tool second = tool("duplicate");
        Object unnamed = new Object();
        CompositeToolRegistry registry = new CompositeToolRegistry(
                new StaticToolRegistry(Arrays.<Object>asList(first, unnamed)),
                new StaticToolRegistry(Arrays.<Object>asList(second, tool("other"))));

        List<Object> tools = registry.getTools();
        assertEquals(3, tools.size());
        assertSame(first, tools.get(0));
        assertSame(unnamed, tools.get(1));
        assertEquals("other", AgentToolVisibility.toolName(tools.get(2)));
    }

    @Test
    public void builderVisibilityChangesPromptOnlyAndHiddenToolStillExecutes() throws Exception {
        final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();
        final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();
        results.add(AgentModelResult.builder()
                .toolCalls(Collections.singletonList(io.github.lnyocly.ai4j.agent.tool.AgentToolCall.builder()
                        .name("bash").callId("call-1")
                        .arguments("{\"action\":\"exec\",\"command\":\"echo ok\"}").build()))
                .build());
        results.add(AgentModelResult.builder().outputText("done").build());
        final int[] executions = new int[]{0};
        AgentModelClient client = new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                prompts.add(prompt);
                return results.removeFirst();
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                return create(prompt);
            }
        };

        Agent agent = new AgentBuilder()
                .modelClient(client)
                .model("test-model")
                .toolRegistry(new StaticToolRegistry(Arrays.<Object>asList(tool("read_file"), tool("bash"))))
                .toolExecutor(call -> {
                    executions[0]++;
                    return "executed";
                })
                .permissionPolicy(AgentPermissionPolicies.allowAll())
                .toolVisibility(AgentToolVisibility.named(Collections.singleton("read_file")))
                .options(AgentOptions.builder().maxSteps(0).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("run it").build());
        assertEquals("done", result.getOutputText());
        assertEquals(1, executions[0]);
        assertEquals(2, prompts.size());
        assertEquals(1, prompts.get(0).getTools().size());
        assertEquals("read_file", AgentToolVisibility.toolName(prompts.get(0).getTools().get(0)));
        assertEquals("read_file", AgentToolVisibility.toolName(agent.getContext().getToolRegistry().getTools().get(0)));
    }

    private static Tool tool(String name) {
        return new Tool("function", new Tool.Function(name, name, null));
    }
}
