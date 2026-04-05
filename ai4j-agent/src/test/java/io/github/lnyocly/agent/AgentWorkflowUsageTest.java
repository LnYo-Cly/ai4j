package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.workflow.RuntimeAgentNode;
import io.github.lnyocly.ai4j.agent.workflow.SequentialWorkflow;
import io.github.lnyocly.ai4j.agent.workflow.StateGraphWorkflow;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowAgent;
import org.junit.Assert;
import org.junit.Test;

public class AgentWorkflowUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_sequential_workflow_with_real_agents() throws Exception {
        // 顺序编排：第一个节点产出草稿，第二个节点做结构化整理
        Agent draftAgent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是方案起草助手。给出3条关键执行步骤。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        Agent formatAgent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是格式化助手。把输入整理成编号清单。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        SequentialWorkflow workflow = new SequentialWorkflow()
                .addNode(new RuntimeAgentNode(draftAgent.newSession()))
                .addNode(new RuntimeAgentNode(formatAgent.newSession()));

        AgentResult result = callWithProviderGuard(() -> workflow.run(new AgentSession(null, null), AgentRequest.builder().input("生成一次数据库迁移发布流程").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }

    @Test
    public void test_state_graph_workflow_with_router() throws Exception {
        // 状态图编排：先路由，再进入不同节点，最后统一收敛
        Agent routerAgent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是路由器。只输出 ROUTE_INCIDENT 或 ROUTE_GENERAL。")
                .instructions("输入含有故障、报错、事故时输出 ROUTE_INCIDENT，否则输出 ROUTE_GENERAL。")
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        Agent incidentAgent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是应急响应助手。给出故障处理建议。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        Agent generalAgent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是通用助手。给出普通建议。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        Agent finalAgent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是总结助手。输出最终建议，保持简洁。")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        StateGraphWorkflow workflow = new StateGraphWorkflow()
                .addNode("route", new RuntimeAgentNode(routerAgent.newSession()))
                .addNode("incident", new RuntimeAgentNode(incidentAgent.newSession()))
                .addNode("general", new RuntimeAgentNode(generalAgent.newSession()))
                .addNode("final", new RuntimeAgentNode(finalAgent.newSession()))
                .start("route")
                .addConditionalEdges("route", (context, request, result) -> {
                    String output = result == null ? "" : result.getOutputText();
                    if (output != null && output.contains("ROUTE_INCIDENT")) {
                        return "incident";
                    }
                    return "general";
                })
                .addEdge("incident", "final")
                .addEdge("general", "final");

        WorkflowAgent workflowAgent = new WorkflowAgent(workflow, new AgentSession(null, null));
        AgentResult result = callWithProviderGuard(() -> workflowAgent.run(AgentRequest.builder().input("线上发生接口报错，用户大量失败").build()));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
    }
}
