package io.github.lnyocly.ai4j.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.structured.StructuredOutputException;
import lombok.Data;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StructuredOutputSelfHealerTest {

    public enum Priority {
        LOW,
        HIGH
    }

    @Data
    public static class OrderSummary {
        @JsonProperty(value = "order_id", required = true)
        @JsonAlias({"orderId", "id"})
        private String orderId;

        @JsonProperty(required = true)
        private double total;

        private Priority priority;

        @JsonIgnore
        private String internalSecret;
    }

    @Data
    public static class LineItem {
        @JsonProperty(required = true)
        private String sku;

        private int quantity;
    }

    @Data
    public static class TreeNode {
        @JsonProperty(required = true)
        private String label;
        private TreeNode next;
    }

    private static final class ScriptedModelClient implements AgentModelClient {
        private final List<AgentModelResult> scriptedResults;
        private final List<AgentPrompt> capturedPrompts = new ArrayList<AgentPrompt>();
        private final AtomicInteger index = new AtomicInteger();

        private ScriptedModelClient(List<AgentModelResult> scriptedResults) {
            this.scriptedResults = scriptedResults;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            capturedPrompts.add(prompt);
            int i = index.getAndIncrement();
            if (i >= scriptedResults.size()) {
                return scriptedResults.get(scriptedResults.size() - 1);
            }
            return scriptedResults.get(i);
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }

        public List<AgentPrompt> getCapturedPrompts() {
            return capturedPrompts;
        }
    }

    private static AgentModelResult textResult(String text) {
        return AgentModelResult.builder().outputText(text).build();
    }

    private static Agent buildAgent(ScriptedModelClient client) {
        return Agents.react()
                .modelClient(client)
                .model("gpt-test")
                .instructions("Base instructions.")
                .build();
    }

    @Test
    public void testAgentCallDirectSuccessWithMarkdownFenceAndSchemaInstructions() throws Exception {
        ScriptedModelClient client = new ScriptedModelClient(Collections.singletonList(
                textResult("Here is your JSON:\n```json\n{\"order_id\":\"ORD-1\",\"total\":12.5,\"priority\":\"HIGH\"}\n```")
        ));
        Agent agent = buildAgent(client);

        OrderSummary summary = agent.call(
                AgentRequest.builder().input("Summarize order 1").build(),
                OrderSummary.class
        );

        assertNotNull(summary);
        assertEquals("ORD-1", summary.getOrderId());
        assertEquals(12.5, summary.getTotal(), 0.0001);
        assertEquals(Priority.HIGH, summary.getPriority());
        assertEquals(1, client.getCapturedPrompts().size());
        String sentInstructions = client.getCapturedPrompts().get(0).getInstructions();
        assertTrue(sentInstructions.contains("Base instructions."));
        assertTrue(sentInstructions.contains("\"order_id\""));
        assertTrue(sentInstructions.contains("\"required\""));
        assertFalse(sentInstructions.contains("internalSecret"));
        assertEquals("Base instructions.", agent.getContext().getInstructions());
    }

    @Test
    public void testSelfHealingRecoversFromMalformedJsonAndMissingRequiredField() throws Exception {
        ScriptedModelClient client = new ScriptedModelClient(Arrays.asList(
                textResult("{not valid json"),
                textResult("{\"total\":42.5}"),
                textResult("{\"orderId\":\"ORD-99\",\"total\":42.5,\"priority\":\"LOW\"}")
        ));
        Agent agent = buildAgent(client);

        OrderSummary summary = agent.call("Extract order 99", OrderSummary.class);

        assertNotNull(summary);
        assertEquals("ORD-99", summary.getOrderId());
        assertEquals(42.5, summary.getTotal(), 0.0001);
        assertEquals(Priority.LOW, summary.getPriority());
        assertEquals(3, client.getCapturedPrompts().size());

        String retry1Input = String.valueOf(client.getCapturedPrompts().get(1).getItems());
        assertTrue(retry1Input.contains("Self-healing retry #1"));
        assertTrue(retry1Input.contains("{not valid json"));

        String retry2Input = String.valueOf(client.getCapturedPrompts().get(2).getItems());
        assertTrue(retry2Input.contains("Self-healing retry #2"));
        assertTrue(retry2Input.contains("order_id"));
    }

    @Test
    public void testTypeReferenceGenericListAndCustomValidatorHealRetry() throws Exception {
        ScriptedModelClient client = new ScriptedModelClient(Arrays.asList(
                textResult("{\"sku\":\"A1\",\"quantity\":1}"),
                textResult("[{\"sku\":\"A1\",\"quantity\":0}]"),
                textResult("[{\"sku\":\"A1\",\"quantity\":3}]")
        ));
        Agent agent = buildAgent(client);

        StructuredOutputSelfHealer.Validator<List<LineItem>> validator = new StructuredOutputSelfHealer.Validator<List<LineItem>>() {
            @Override
            public void validate(List<LineItem> items) {
                if (items == null || items.isEmpty()) {
                    throw new IllegalArgumentException("items cannot be empty");
                }
                for (LineItem item : items) {
                    if (item.getQuantity() <= 0) {
                        throw new IllegalArgumentException("quantity must be positive for " + item.getSku());
                    }
                }
            }
        };

        List<LineItem> result = agent.call(
                AgentRequest.builder().input("Extract items").build(),
                new TypeReference<List<LineItem>>() {},
                2,
                validator
        );

        assertEquals(1, result.size());
        assertEquals("A1", result.get(0).getSku());
        assertEquals(3, result.get(0).getQuantity());
        assertEquals(3, client.getCapturedPrompts().size());

        String secondRetryInput = String.valueOf(client.getCapturedPrompts().get(2).getItems());
        assertTrue(secondRetryInput.contains("quantity must be positive for A1"));
    }

    @Test
    public void testExhaustedHealAttemptsThrowsStructuredOutputException() throws Exception {
        ScriptedModelClient client = new ScriptedModelClient(Arrays.asList(
                textResult("broken-0"),
                textResult("broken-1"),
                textResult("{\"order_id\":\"TOO-LATE\",\"total\":1.0}")
        ));
        Agent agent = buildAgent(client);

        try {
            agent.call(AgentRequest.builder().input("Extract").build(), OrderSummary.class, 1);
            fail("Expected StructuredOutputException when retries exhausted");
        } catch (StructuredOutputException ex) {
            assertEquals("broken-1", ex.getRawContent());
            assertTrue(ex.getMessage().contains("2 attempt(s)"));
        }
        assertEquals(2, client.getCapturedPrompts().size());
    }

    @Test
    public void testZeroMaxHealAttemptsFromBuilderDisablesRetry() throws Exception {
        ScriptedModelClient client = new ScriptedModelClient(Arrays.asList(
                textResult("invalid-initial"),
                textResult("{\"order_id\":\"UNUSED\",\"total\":1.0}")
        ));
        Agent agent = Agents.react()
                .modelClient(client)
                .model("gpt-test")
                .maxHealAttempts(0)
                .build();

        try {
            agent.call("Extract", OrderSummary.class);
            fail("Expected StructuredOutputException without retry when maxHealAttempts is 0");
        } catch (StructuredOutputException ex) {
            assertEquals("invalid-initial", ex.getRawContent());
            assertTrue(ex.getMessage().contains("1 attempt(s)"));
        }
        assertEquals(1, client.getCapturedPrompts().size());
    }

    @Test
    public void testAgentSessionCallWithToolExecutionAndFollowUpTurn() throws Exception {
        Tool.Function fn = new Tool.Function();
        fn.setName("lookup_price");
        fn.setDescription("Looks up item price");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        param.setProperties(new HashMap<String, Tool.Function.Property>());
        param.setRequired(Collections.<String>emptyList());
        fn.setParameters(param);

        ScriptedModelClient client = new ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(
                                AgentToolCall.builder().callId("call_1").name("lookup_price").arguments("{}").build()
                        ))
                        .build(),
                textResult("not json yet"),
                textResult("{\"order_id\":\"ORD-TOOL\",\"total\":19.99}"),
                textResult("{\"order_id\":\"ORD-FOLLOWUP\",\"total\":39.98}")
        ));

        Agent agent = Agents.react()
                .modelClient(client)
                .model("gpt-test")
                .toolRegistry(new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn))))
                .toolExecutor(call -> "{\"price\":19.99}")
                .build();

        AgentSession session = agent.newSession();
        OrderSummary first = session.call(AgentRequest.builder().input("Order with tool").build(), OrderSummary.class);
        assertEquals("ORD-TOOL", first.getOrderId());
        assertEquals(19.99, first.getTotal(), 0.0001);

        OrderSummary followUp = session.call("Double the order", OrderSummary.class);
        assertEquals("ORD-FOLLOWUP", followUp.getOrderId());
        assertEquals(39.98, followUp.getTotal(), 0.0001);
        assertEquals(4, client.getCapturedPrompts().size());
    }

    @Test
    public void testSchemaGenerationHandlesCyclicPojoWithoutStackOverflow() {
        String schema = StructuredOutputSelfHealer.buildSchemaJson(
                io.github.lnyocly.ai4j.codec.JsonCodec.protocolMapper()
                        .getTypeFactory()
                        .constructType(TreeNode.class)
        );
        assertTrue(schema.contains("\"label\""));
        assertTrue(schema.contains("\"next\""));
        assertTrue(schema.contains("\"required\""));
    }
}
