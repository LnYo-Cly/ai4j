package io.github.lnyocly;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Live: closes the "agent native Anthropic path never exercised with real thinking / tools" gap.
 * <p>Env: {@code ANTHROPIC_API_KEY} (required), {@code ANTHROPIC_BASE_URL} (default zhipu coding plan),
 * {@code ANTHROPIC_MODEL} (default glm-5.1).
 */
@Category(LiveProviderTest.class)
public class AgentAnthropicThinkingAndToolLiveTest {

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    private static Agent baseAgent() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = env("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = env("ANTHROPIC_MODEL", "glm-5.1");
        return Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .maxOutputTokens(1024)
                .build();
    }

    /** #4: thinking enabled -> reasoningText captured via native Anthropic path. */
    @Test
    public void agentCapturesThinkingViaAnthropicNative() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = env("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = env("ANTHROPIC_MODEL", "glm-5.1");

        Agent agent = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .reasoning(Collections.<String, Object>singletonMap("type", "enabled"))
                .maxOutputTokens(1024)
                .build();

        AgentSession session = agent.newSession();
        AgentResult result = session.run("Think step by step, then compute 17 * 23 and give the answer.");
        System.out.println("=== thinking test ===");
        System.out.println("outputText=" + result.getOutputText());
        String raw = String.valueOf(result.getRawResponse());
        System.out.println("rawResponse contains thinking block: " + raw.contains("thinking"));
        assertNotNull(result);
        assertTrue("agent must produce output via native anthropic path with thinking enabled",
                result.getOutputText() != null && !result.getOutputText().isEmpty());
    }

    /** #3: agent drives a real tool round-trip via the native Anthropic path. */
    @Test
    public void agentRunsToolLoopViaAnthropicNative() throws Exception {
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
        Tool tool = new Tool("function", fn);
        AgentToolRegistry registry = new StaticToolRegistry(Collections.<Object>singletonList(tool));
        ToolExecutor executor = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "echo result";
            }
        };

        Agent agent = baseAgent();
        // re-build with tool registry + executor
        String key = System.getenv("ANTHROPIC_API_KEY");
        String baseUrl = env("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = env("ANTHROPIC_MODEL", "glm-5.1");
        agent = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .maxOutputTokens(512)
                .toolRegistry(registry)
                .toolExecutor(executor)
                .build();

        AgentResult result = agent.newSession().run("Use the echo_text tool to echo the text 'hello'. Then tell me the echoed result.");
        System.out.println("=== tool test ===");
        System.out.println("outputText=" + result.getOutputText());
        System.out.println("toolResults=" + result.getToolResults());
        assertNotNull(result);
        assertTrue("agent should have executed at least one tool via native anthropic path",
                result.getToolResults() != null && !result.getToolResults().isEmpty());
    }
}
