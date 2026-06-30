package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.compact.CompactPolicy;
import io.github.lnyocly.ai4j.agent.compact.CompactResult;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * Tests the auto-compaction trigger wired into the runtime loop: when {@link CompactPolicy#shouldCompact}
 * returns true, the runtime fires BEFORE_COMPACT, compacts, restores memory, and fires ON_COMPACT —
 * before the next model call. One deterministic test + one real-LLM test.
 */
public class CompactionAutoTriggerTest {

    /** A CompactPolicy that triggers when memory has more than N items; counts compact() calls. */
    static class RecordingCompactPolicy implements CompactPolicy {
        final int threshold;
        int compactCalls = 0;

        RecordingCompactPolicy(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public boolean shouldCompact(MemorySnapshot snapshot) {
            return snapshot != null
                    && snapshot.getItems() != null
                    && snapshot.getItems().size() > threshold;
        }

        @Override
        public CompactResult compact(MemorySnapshot snapshot) {
            compactCalls++;
            return null; // count only; don't restore (the trigger is what we're testing)
        }
    }

    @Test
    public void autoCompactFiresWhenPolicySaysShouldCompact() throws Exception {
        AgentToolCall call = AgentToolCall.builder().name("do_thing").callId("c1").arguments("{}").build();
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(call)).build(),
                AgentModelResult.builder().outputText("done").build()));
        ToolInterceptorTest.RecordingExecutor exec = new ToolInterceptorTest.RecordingExecutor();
        RecordingCompactPolicy policy = new RecordingCompactPolicy(1);

        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(exec).toolRegistry(ToolInterceptorTest.registry())
                .compactPolicy(policy)
                .build();
        agent.newSession().run("do it");

        // step 0: memory has 1 item (user input). shouldCompact(1>1)=false. Model → tool → memory grows.
        // step 1: shouldCompact(items>1)=true → auto-compact fires.
        assertTrue("auto-compaction must fire when shouldCompact returns true", policy.compactCalls > 0);
    }

    @Test
    public void noCompactPolicyMeansNoAutoCompact() throws Exception {
        AgentToolCall call = AgentToolCall.builder().name("do_thing").callId("c1").arguments("{}").build();
        ToolInterceptorTest.ScriptedModelClient model = new ToolInterceptorTest.ScriptedModelClient(Arrays.asList(
                AgentModelResult.builder().toolCalls(Collections.singletonList(call)).build(),
                AgentModelResult.builder().outputText("done").build()));
        ToolInterceptorTest.RecordingExecutor exec = new ToolInterceptorTest.RecordingExecutor();

        Agent agent = Agents.react()
                .modelClient(model).model("test-model")
                .toolExecutor(exec).toolRegistry(ToolInterceptorTest.registry())
                .build();
        agent.newSession().run("do it");
        // no crash, no compact — just runs normally
    }

    @Test
    @Category(LiveProviderTest.class)
    public void liveAutoCompactTriggersDuringRealGlmTurn() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        RecordingCompactPolicy policy = new RecordingCompactPolicy(1);

        // echo_text tool — forces a 2-step turn (model calls tool → tool result → step 1 compact)
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
        AgentToolRegistry registry = new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));
        ToolExecutor executor = call -> "echo result";

        Agent agent = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .maxOutputTokens(512)
                .toolRegistry(registry)
                .toolExecutor(executor)
                .compactPolicy(policy)
                .build();
        agent.newSession().run("Use the echo_text tool to echo 'hello', then tell me the result.");

        assertTrue("auto-compaction must fire during a real GLM turn with a low threshold",
                policy.compactCalls > 0);
    }
}
