package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.extension.ExtensionAgentTools;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class ExtensionAgentToolsTest {

    @Test
    public void shouldNotExposeToolsUntilExtensionAndToolAreExplicitlyAllowed() {
        ExtensionRegistry discoveredOnly = ExtensionRegistry.of(new WeatherExtension());
        ExtensionAgentTools discoveredTools = ExtensionAgentTools.from(discoveredOnly);

        Assert.assertTrue(discoveredTools.getToolRegistry().getTools().isEmpty());

        ExtensionRegistry enabledOnly = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack");
        ExtensionAgentTools enabledTools = ExtensionAgentTools.from(enabledOnly);

        Assert.assertTrue(enabledTools.getToolRegistry().getTools().isEmpty());
    }

    @Test
    public void shouldMapExtensionToolSchemaToAgentTool() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack")
                .exposeTool("weather.search");

        List<Object> tools = ExtensionAgentTools.from(registry).getToolRegistry().getTools();

        Assert.assertEquals(1, tools.size());
        Assert.assertTrue(tools.get(0) instanceof Tool);
        Tool tool = (Tool) tools.get(0);
        Assert.assertEquals("function", tool.getType());
        Assert.assertEquals("weather.search", tool.getFunction().getName());
        Assert.assertEquals("Search weather", tool.getFunction().getDescription());
        Assert.assertEquals("object", tool.getFunction().getParameters().getType());
        Assert.assertEquals(Arrays.asList("city"), tool.getFunction().getParameters().getRequired());
        Assert.assertEquals("string", tool.getFunction().getParameters().getProperties().get("city").getType());
        Assert.assertEquals("Target city", tool.getFunction().getParameters().getProperties().get("city").getDescription());
        Assert.assertEquals(Arrays.asList("celsius", "fahrenheit"),
                tool.getFunction().getParameters().getProperties().get("unit").getEnumValues());
    }

    @Test
    public void shouldExecuteExposedExtensionToolInsideAgentLoop() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack")
                .exposeTool("weather.search");
        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("weather.search")
                        .arguments("{\"city\":\"Shanghai\"}")
                        .callId("call-weather-1")
                        .type("function_call")
                        .build()))
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("weather done")
                .build());

        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("Check weather").build());

        Assert.assertEquals("weather done", result.getOutputText());
        Assert.assertEquals(1, result.getToolResults().size());
        Assert.assertEquals("weather.search", result.getToolResults().get(0).getName());
        Assert.assertEquals("weather:{\"city\":\"Shanghai\"}:call-weather-1",
                result.getToolResults().get(0).getOutput());
        Assert.assertEquals(2, modelClient.prompts.size());
        Assert.assertEquals(1, modelClient.prompts.get(0).getTools().size());
    }

    @Test
    public void shouldRejectUnexposedExtensionToolExecution() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack");

        try {
            ExtensionAgentTools.from(registry).getToolExecutor().execute(AgentToolCall.builder()
                    .name("weather.search")
                    .arguments("{}")
                    .build());
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("Extension tool not exposed"));
        }
    }

    private static class WeatherExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("weather-pack")
                    .name("Weather Pack")
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.tools().register(ExtensionToolSpec.builder()
                            .name("weather.search")
                            .description("Search weather")
                            .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"Target city\"},\"unit\":{\"type\":\"string\",\"enum\":[\"celsius\",\"fahrenheit\"]}},\"required\":[\"city\"]}")
                            .build(),
                    new io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor() {
                        public String execute(ExtensionToolCall call) {
                            return "weather:" + call.getArguments() + ":" + call.getAttributes().get("callId");
                        }
                    });
        }
    }

    private static class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();
        private final List<AgentPrompt> prompts = new java.util.ArrayList<AgentPrompt>();

        private void enqueue(AgentModelResult result) {
            results.addLast(result);
        }

        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            return results.removeFirst();
        }

        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            prompts.add(prompt);
            return results.removeFirst();
        }
    }
}
