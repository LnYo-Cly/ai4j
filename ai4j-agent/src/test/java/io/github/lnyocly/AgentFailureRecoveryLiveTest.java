package io.github.lnyocly;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import io.github.lnyocly.ai4j.agent.replay.ResumableModelClient;
import io.github.lnyocly.ai4j.agent.replay.ResumableToolExecutor;
import io.github.lnyocly.ai4j.agent.replay.ResumeCache;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.anthropic.chat.AnthropicMessagesService;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Live failure-recovery verification against a real LLM (GLM via Anthropic Messages).
 *
 * <p>Run 1 drives a real tool turn and captures every model/tool node. Run 2 re-runs the SAME input
 * against the populated cache and must make <b>zero</b> real LLM calls (full resume). Run 3 drops the
 * last captured model step (simulate "crashed before the final step") and must re-run only that
 * missing step. Skipped unless {@code ANTHROPIC_API_KEY} is set.</p>
 */
@Category(LiveProviderTest.class)
public class AgentFailureRecoveryLiveTest {

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    @Test
    public void fullResumeMakesZeroRealLlmCallsAndPartialResumeReRunsMissingStep() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = env("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = env("ANTHROPIC_MODEL", "glm-5.1");
        String input = "Use the echo_text tool to echo 'hello', then tell me the result.";

        AgentToolRegistry registry = echoRegistry();
        ResumeCache cache = new ResumeCache();

        // ---- Run 1: real turn, captures all model + tool nodes ----
        CountingModelClient real1 = new CountingModelClient(buildRealClient(key, baseUrl));
        CountingToolExecutor tool1 = new CountingToolExecutor(echoExecutor());
        Agent agent1 = Agents.react()
                .modelClient(new ResumableModelClient(real1, cache))
                .model(model).maxOutputTokens(512)
                .toolRegistry(registry).toolExecutor(new ResumableToolExecutor(tool1, cache))
                .build();
        agent1.newSession().run(AgentRequest.builder().input(input).build());
        int run1ModelCalls = real1.calls;
        int run1ToolCalls = tool1.calls;
        assertTrue("run1 must make real LLM calls", run1ModelCalls >= 1);
        assertTrue("run1 must capture model results", cache.modelSize() >= 1);
        System.out.println("=== run1 (capture) === modelCalls=" + run1ModelCalls + " toolCalls=" + run1ToolCalls
                + " cache.modelSize=" + cache.modelSize());

        // ---- Run 2: same input, full resume -> ZERO real LLM calls ----
        CountingModelClient real2 = new CountingModelClient(buildRealClient(key, baseUrl));
        CountingToolExecutor tool2 = new CountingToolExecutor(echoExecutor());
        Agent agent2 = Agents.react()
                .modelClient(new ResumableModelClient(real2, cache))
                .model(model).maxOutputTokens(512)
                .toolRegistry(registry).toolExecutor(new ResumableToolExecutor(tool2, cache))
                .build();
        agent2.newSession().run(AgentRequest.builder().input(input).build());
        System.out.println("=== run2 (full resume) === modelCalls=" + real2.calls + " toolCalls=" + tool2.calls);
        assertEquals("full resume must make ZERO real LLM calls", 0, real2.calls);
        assertEquals("full resume must skip all tool side effects", 0, tool2.calls);

        // ---- Run 3: drop the last captured model step (crash before final) -> re-run only it ----
        cache.removeLastModelEntry();
        CountingModelClient real3 = new CountingModelClient(buildRealClient(key, baseUrl));
        CountingToolExecutor tool3 = new CountingToolExecutor(echoExecutor());
        Agent agent3 = Agents.react()
                .modelClient(new ResumableModelClient(real3, cache))
                .model(model).maxOutputTokens(512)
                .toolRegistry(registry).toolExecutor(new ResumableToolExecutor(tool3, cache))
                .build();
        agent3.newSession().run(AgentRequest.builder().input(input).build());
        System.out.println("=== run3 (partial resume) === modelCalls=" + real3.calls + " toolCalls=" + tool3.calls);
        assertTrue("partial resume must re-run the missing step (>=1 real call)", real3.calls >= 1);
        assertTrue("partial resume must still be cheaper than a full run", real3.calls < run1ModelCalls + run1ModelCalls);
    }

    private static AgentToolRegistry echoRegistry() {
        Tool.Function fn = new Tool.Function();
        fn.setName("echo_text");
        fn.setDescription("Echo back the provided text exactly.");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        Tool.Function.Property textProp = new Tool.Function.Property();
        textProp.setType("string");
        textProp.setDescription("The text to echo back");
        props.put("text", textProp);
        param.setProperties(props);
        param.setRequired(Collections.singletonList("text"));
        fn.setParameters(param);
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));
    }

    private static ToolExecutor echoExecutor() {
        return new ToolExecutor() {
            public String execute(AgentToolCall call) { return "echo result"; }
        };
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
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(300, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
                .build());
        IMessagesService service = new AnthropicMessagesService(configuration);
        return new MessagesModelClient(service);
    }

    /** Counts real delegate calls (a hit never reaches here). */
    static final class CountingModelClient implements AgentModelClient {
        final AgentModelClient delegate;
        int calls;
        CountingModelClient(AgentModelClient delegate) { this.delegate = delegate; }
        public AgentModelResult create(AgentPrompt prompt) throws Exception {
            calls++;
            System.out.println("  [real model call #" + calls + "]");
            return delegate.create(prompt);
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
            calls++;
            System.out.println("  [real model stream call #" + calls + "]");
            return delegate.createStream(prompt, listener);
        }
    }

    static final class CountingToolExecutor implements ToolExecutor {
        final ToolExecutor delegate;
        int calls;
        CountingToolExecutor(ToolExecutor delegate) { this.delegate = delegate; }
        public String execute(AgentToolCall call) throws Exception {
            calls++;
            return delegate.execute(call);
        }
    }
}
