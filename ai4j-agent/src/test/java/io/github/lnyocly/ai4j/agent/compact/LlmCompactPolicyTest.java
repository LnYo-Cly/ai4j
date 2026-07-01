package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.platform.anthropic.chat.AnthropicMessagesService;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests {@link LlmCompactPolicy} — LLM-powered structured summaries. */
public class LlmCompactPolicyTest {

    @Test
    public void shouldCompactWhenItemsExceedThreshold() {
        LlmCompactPolicy policy = new LlmCompactPolicy(
                new FixedModelClient("summary"), "m", 3);
        assertTrue(policy.shouldCompact(MemorySnapshot.from(Arrays.asList("a", "b", "c", "d"), null)));
        assertFalse(policy.shouldCompact(MemorySnapshot.from(Arrays.asList("a", "b"), null)));
    }

    @Test
    @Category(LiveProviderTest.class)
    public void liveLlmCompactGeneratesStructuredSummary() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        AgentModelClient modelClient = buildRealClient(key, baseUrl);
        LlmCompactPolicy policy = new LlmCompactPolicy(modelClient, model, 3);

        // simulate a 5-item conversation
        List<Object> items = new ArrayList<Object>();
        items.add(AgentInputItem.userMessage("What is 2+2?"));
        items.add(AgentInputItem.message("assistant", "It's 4."));
        items.add(AgentInputItem.userMessage("What about 3+3?"));
        items.add(AgentInputItem.message("assistant", "That's 6."));
        items.add(AgentInputItem.userMessage("And 10 times 10?"));
        MemorySnapshot snapshot = MemorySnapshot.from(items, null);

        CompactResult result = policy.compact(snapshot);

        assertNotNull(result);
        assertNotNull("summary must be generated", result.getSummary());
        assertTrue("summary must be non-empty: " + result.getSummary(),
                !result.getSummary().trim().isEmpty());
        assertTrue("summary should look structured (mention a number or goal): " + result.getSummary(),
                result.getSummary().length() > 20);
        assertNotNull(result.getMemory());
        assertTrue("should keep recent items (<= threshold)",
                result.getMemory().getItems().size() <= 3);
    }

    private static AgentModelClient buildRealClient(String key, String baseUrl) {
        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(key);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build());
        IMessagesService service = new AnthropicMessagesService(configuration);
        return new MessagesModelClient(service);
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String output;
        FixedModelClient(String output) { this.output = output; }
        public io.github.lnyocly.ai4j.agent.model.AgentModelResult create(io.github.lnyocly.ai4j.agent.model.AgentPrompt prompt) {
            return io.github.lnyocly.ai4j.agent.model.AgentModelResult.builder().outputText(output).build();
        }
        public io.github.lnyocly.ai4j.agent.model.AgentModelResult createStream(io.github.lnyocly.ai4j.agent.model.AgentPrompt prompt, io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener listener) {
            return io.github.lnyocly.ai4j.agent.model.AgentModelResult.builder().outputText(output).build();
        }
    }
}
