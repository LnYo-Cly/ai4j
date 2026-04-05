package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.subagent.HandoffFailureAction;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class SubAgentUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_subagent_delegation_with_real_llm() throws Exception {
        // 子代理配置示例：在 Chat 模式下先验证“可挂载 + 可运行”的主流程
        Agent reviewer = Agents.builder()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是代码审查专家。输出3条高风险问题。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        SubAgentDefinition reviewerSubAgent = SubAgentDefinition.builder()
                .name("reviewer")
                .description("负责代码风险审查")
                .toolName("delegate_code_review")
                .agent(reviewer)
                .build();

        Agent lead = Agents.builder()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .toolChoice("none")
                .systemPrompt("你是团队负责人。直接输出审查结论，不要调用任何工具。")
                .subAgent(reviewerSubAgent)
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        AgentResult result = callWithProviderGuard(() -> lead.run(AgentRequest.builder().input("审查一个登录模块，重点关注安全风险").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }

    @Test
    public void test_handoff_policy_denied_with_fallback_executor() throws Exception {
        // HandoffPolicy 配置示例：展示 allow/deny 与 fallback 的配置方式
        Agent subAgent = Agents.builder()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是子代理")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        SubAgentDefinition definition = SubAgentDefinition.builder()
                .name("planner")
                .description("负责规划")
                .toolName("delegate_planning")
                .agent(subAgent)
                .build();

        Agent parent = Agents.builder()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .toolChoice("none")
                .toolExecutor(call -> "{\"fallback\":true,\"tool\":\"" + call.getName() + "\"}")
                .subAgent(definition)
                .handoffPolicy(HandoffPolicy.builder()
                        .allowedTools(Collections.singleton("delegate_other"))
                        .onDenied(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                        .build())
                .systemPrompt("你是主代理。直接输出简短发布建议，不要调用任何工具。")
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        AgentResult result = callWithProviderGuard(() -> parent.run(AgentRequest.builder().input("给一个两天发布窗口的变更建议").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }
}
