package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ModelClientRagQueryPlannerTest {

    @Test
    public void plansVariantsWithDeduplicationAndHistory() {
        RecordingModelClient client = new RecordingModelClient(
                Endpoint.builder(Protocol.CHAT_COMPLETIONS).baseUrl("https://api.example.com").build(),
                "```json\n"
                        + "{\"rewrite\":\"What is the AI4J Java 8 policy?\","
                        + "\"multiQueries\":[\"AI4J JDK 8 requirement\",\"What is the AI4J Java 8 policy?\",\"AI4J Maven build target\"],"
                        + "\"hyde\":\"AI4J compiles targeting Java 8 bytecode.\"}\n"
                        + "```");

        ModelClientRagQueryPlanner planner = ModelClientRagQueryPlanner.builder()
                .modelClient(client)
                .model("gpt-4o-mini")
                .rewriteEnabled(true)
                .multiQueryEnabled(true)
                .hydeEnabled(true)
                .maxVariants(4)
                .maxHistoryItems(2)
                .build();

        RagQuery query = RagQuery.builder()
                .query("  What about   Java version?  ")
                .history(Arrays.asList(
                        ChatMemoryItem.user("older turn"),
                        ChatMemoryItem.assistant("Tell me about AI4J"),
                        ChatMemoryItem.user("How is it built?")))
                .build();

        RagQueryPlan plan = planner.plan(query);
        Assert.assertFalse(plan.isFallback());
        Assert.assertEquals("What about Java version?", plan.getOriginalQuery());
        Assert.assertEquals(4, plan.getVariants().size());
        Assert.assertEquals(RagQueryVariantType.ORIGINAL, plan.getVariants().get(0).getType());
        Assert.assertEquals(RagQueryVariantType.REWRITE, plan.getVariants().get(1).getType());
        Assert.assertEquals("What is the AI4J Java 8 policy?", plan.getVariants().get(1).getQuery());
        Assert.assertEquals(RagQueryVariantType.MULTI_QUERY, plan.getVariants().get(2).getType());
        Assert.assertEquals("AI4J JDK 8 requirement", plan.getVariants().get(2).getQuery());
        Assert.assertEquals(RagQueryVariantType.MULTI_QUERY, plan.getVariants().get(3).getType());
        Assert.assertEquals("AI4J Maven build target", plan.getVariants().get(3).getQuery());

        Assert.assertNotNull(client.lastRequest);
        Assert.assertEquals("gpt-4o-mini", client.lastRequest.getModel());
        Assert.assertNotNull(client.lastRequest.getProviderOptions().get("response_format"));
        String userPrompt = ((Message) client.lastRequest.getItems().get(1)).textContent();
        Assert.assertFalse(userPrompt.contains("older turn"));
        Assert.assertTrue(userPrompt.contains("Tell me about AI4J"));
    }

    @Test
    public void fallsBackWhenModelThrowsOrReturnsInvalidJson() {
        ModelClient failingClient = new ModelClient() {
            @Override
            public Endpoint endpoint() {
                return Endpoint.builder(Protocol.MESSAGES).baseUrl("https://api.example.com").build();
            }

            @Override
            public Capabilities capabilities() {
                return Capabilities.none(Protocol.MESSAGES);
            }

            @Override
            public void require(Capability capability) {
            }

            @Override
            public ModelResult create(ModelRequest request) {
                throw new IllegalStateException("upstream timeout");
            }

            @Override
            public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };

        ModelClientRagQueryPlanner planner = new ModelClientRagQueryPlanner(failingClient, "claude-3-5-sonnet");
        RagQueryPlan failedPlan = planner.plan(RagQuery.builder().query("fallback query").build());
        Assert.assertTrue(failedPlan.isFallback());
        Assert.assertEquals(1, failedPlan.getVariants().size());
        Assert.assertEquals("fallback query", failedPlan.getVariants().get(0).getQuery());
        Assert.assertTrue(failedPlan.getFallbackReason().contains("upstream timeout"));

        RecordingModelClient badJsonClient = new RecordingModelClient(
                Endpoint.builder(Protocol.RESPONSES).baseUrl("https://api.example.com").build(),
                "not valid json at all");
        ModelClientRagQueryPlanner badJsonPlanner = new ModelClientRagQueryPlanner(badJsonClient, "gpt-4o-mini");
        RagQueryPlan badJsonPlan = badJsonPlanner.plan(RagQuery.builder().query("another query").build());
        Assert.assertTrue(badJsonPlan.isFallback());
        Assert.assertEquals("unparseable_planner_output", badJsonPlan.getFallbackReason());
        Assert.assertNull(badJsonClient.lastRequest.getProviderOptions().get("response_format"));
    }

    static final class RecordingModelClient implements ModelClient {
        private final Endpoint endpoint;
        private final String responseText;
        private ModelRequest lastRequest;

        RecordingModelClient(Endpoint endpoint, String responseText) {
            this.endpoint = endpoint;
            this.responseText = responseText;
        }

        @Override
        public Endpoint endpoint() {
            return endpoint;
        }

        @Override
        public Capabilities capabilities() {
            return Capabilities.none(endpoint.getProtocol());
        }

        @Override
        public void require(Capability capability) {
        }

        @Override
        public ModelResult create(ModelRequest request) {
            this.lastRequest = request;
            List<ModelItem> items = responseText == null
                    ? java.util.Collections.<ModelItem>emptyList()
                    : java.util.Collections.<ModelItem>singletonList(Message.assistant().text(responseText).build());
            return ModelResult.builder().items(items).build();
        }

        @Override
        public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
            throw new UnsupportedOperationException();
        }

        ModelRequest lastRequest() {
            return lastRequest;
        }
    }
}
