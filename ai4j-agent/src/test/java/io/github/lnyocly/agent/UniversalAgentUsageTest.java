package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class UniversalAgentUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_basic_agent_build_and_run() throws Exception {
        // 基础搭建：最小可用 Agent
        Agent agent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是一个简洁助手，只输出一行结论，不要废话。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        AgentResult result = callWithProviderGuard(() -> agent.run(AgentRequest.builder().input("请用一句话说明为什么代码评审重要").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }

    @Test
    public void test_advanced_parameters_and_session_memory() throws Exception {
        // 高级参数：采样参数、额外请求体、用户标识
        Map<String, Object> reasoning = new HashMap<>();
        reasoning.put("effort", "low");

        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("metadata", "agent-usage-test");

        Agent agent = Agents.builder()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.3)
                .topP(0.9)
                .maxOutputTokens(512)
                .reasoning(reasoning)
                .parallelToolCalls(false)
                .store(false)
                .user("agent-demo-user")
                .extraBody(extraBody)
                .instructions("回答时尽量精炼。")
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        AgentSession session = agent.newSession();

        callWithProviderGuard(() -> {
            session.run(AgentRequest.builder().input("记住口令：ALPHA-2026-XYZ。只回复：收到").build());
            return null;
        });
        AgentResult secondTurn = callWithProviderGuard(() -> session.run(AgentRequest.builder().input("请只回复上一个口令，不要解释").build()));

        Assert.assertNotNull(secondTurn);
        Assert.assertNotNull(secondTurn.getOutputText());
        Assert.assertTrue(secondTurn.getOutputText().contains("ALPHA-2026-XYZ"));
    }

    @Test
    public void test_stream_mode_with_real_chat_model() throws Exception {
        // stream=true 会走 createStream 分支，ChatModelClient 内部会安全降级到非流式请求
        Agent agent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .options(AgentOptions.builder().stream(true).maxSteps(2).build())
                .build();

        AgentResult result = callWithProviderGuard(() -> agent.run(AgentRequest.builder().input("给我一个 Java 代码重构建议").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }
}
