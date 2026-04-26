package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ReActAgentUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_react_agent_basic_run_with_real_model() throws Exception {
        // ReAct 基础能力：真实模型完成多步推理并返回文本
        Agent agent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是架构顾问。先给结论，再给3条执行建议。")
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        AgentResult result = callWithProviderGuard(() -> agent.run(AgentRequest.builder().input("我们要把单体应用拆到微服务，先从哪里开始？").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }

    @Test
    public void test_react_agent_parallel_tool_calls_and_event_lifecycle() throws Exception {
        // 监听运行事件，验证 ReAct 生命周期事件可观测
        List<AgentEventType> events = new ArrayList<AgentEventType>();
        AgentEventPublisher publisher = new AgentEventPublisher();
        publisher.addListener(event -> events.add(event.getType()));

        Agent agent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .parallelToolCalls(true)
                .systemPrompt("你是技术评审助手，输出精炼结论。")
                .instructions("围绕可测试性和可维护性给建议。")
                .eventPublisher(publisher)
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        AgentResult result = callWithProviderGuard(() -> agent.run(AgentRequest.builder().input("请点评当前项目的测试策略并给改进建议").build()));

        Assert.assertNotNull(result);
        Assert.assertTrue(events.contains(AgentEventType.STEP_START));
        Assert.assertTrue(events.contains(AgentEventType.MODEL_REQUEST));
        Assert.assertTrue(events.contains(AgentEventType.MODEL_RESPONSE));
        Assert.assertTrue(events.contains(AgentEventType.FINAL_OUTPUT));
    }
}
