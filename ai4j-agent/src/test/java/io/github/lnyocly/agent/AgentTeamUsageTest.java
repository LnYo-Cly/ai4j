package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamHook;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentTeamUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_team_with_deterministic_plan_and_real_members() throws Exception {
        Agent analyst = createRoleAgent(
                "你是需求分析师",
                "输出需求拆解、风险点、验收口径，控制在5条以内。"
        );

        Agent backend = createRoleAgent(
                "你是后端工程师",
                "输出接口与数据模型建议，强调可上线性。"
        );

        Agent lead = createRoleAgent(
                "你是技术负责人",
                "把成员结果合并成最终执行计划，按模块分段输出。"
        );

        AtomicInteger beforeTaskCount = new AtomicInteger();
        AtomicInteger afterTaskCount = new AtomicInteger();

        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .rawPlanText("fixed-plan")
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder().id("t1").memberId("analyst").task("拆解目标与风险").build(),
                                AgentTeamTask.builder().id("t2").memberId("backend").task("给出后端实施方案").dependsOn(Arrays.asList("t1")).build()
                        ))
                        .build())
                .synthesizerAgent(lead)
                .member(AgentTeamMember.builder().id("analyst").name("Analyst").description("需求分析").agent(analyst).build())
                .member(AgentTeamMember.builder().id("backend").name("Backend").description("后端设计").agent(backend).build())
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(2)
                        .enableMessageBus(true)
                        .enableMemberTeamTools(false)
                        .includeMessageHistoryInDispatch(true)
                        .requirePlanApproval(true)
                        .maxRounds(8)
                        .build())
                .planApproval((objective, plan, members, options) -> plan != null && plan.getTasks() != null && !plan.getTasks().isEmpty())
                .hook(new AgentTeamHook() {
                    @Override
                    public void beforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
                        beforeTaskCount.incrementAndGet();
                    }

                    @Override
                    public void afterTask(String objective, AgentTeamMemberResult result) {
                        afterTaskCount.incrementAndGet();
                    }
                })
                .build();

        AgentTeamResult result = runTeamWithRetry(team, "给一个中型 Java 项目的一周迭代交付方案", 3);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutput());
        Assert.assertTrue(result.getOutput().trim().length() > 0);
        Assert.assertEquals(2, result.getTaskStates().size());
        Assert.assertTrue("任务未全部完成: " + describeTaskStates(result.getTaskStates()), allTasksCompleted(result.getTaskStates()));
        Assert.assertTrue(beforeTaskCount.get() >= 2);
        Assert.assertTrue(afterTaskCount.get() >= 2);
    }

    @Test
    public void test_team_dynamic_member_and_message_bus() throws Exception {
        Agent lead = createRoleAgent(
                "你是团队负责人",
                "先规划任务再总结输出，内容简洁。"
        );

        Agent writer = createRoleAgent(
                "你是文档工程师",
                "产出发布说明草稿。"
        );

        Agent reviewer = createRoleAgent(
                "你是评审工程师",
                "产出评审意见。"
        );

        AgentTeam team = Agents.team()
                .leadAgent(lead)
                .member(AgentTeamMember.builder().id("writer").name("Writer").agent(writer).build())
                .options(AgentTeamOptions.builder()
                        .allowDynamicMemberRegistration(true)
                        .enableMessageBus(true)
                        .enableMemberTeamTools(false)
                        .maxRounds(8)
                        .build())
                .build();

        team.registerMember(AgentTeamMember.builder().id("reviewer").name("Reviewer").agent(reviewer).build());

        AgentTeamResult result = callWithProviderGuard(() -> team.run("整理本周发布说明并补充评审意见"));
        team.sendMessage("writer", "reviewer", "peer.ask", "task-seed", "请重点关注回滚策略");

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutput());
        Assert.assertTrue(result.getOutput().trim().length() > 0);
        Assert.assertTrue(result.getMemberResults().size() > 0);
        Assert.assertTrue(team.listMembers().size() >= 2);
        Assert.assertTrue(result.getMessages().size() > 0);

        boolean hasPeerAsk = false;
        for (AgentTeamMessage message : team.listMessages()) {
            if ("peer.ask".equals(message.getType())) {
                hasPeerAsk = true;
                break;
            }
        }
        Assert.assertTrue(hasPeerAsk);

        Assert.assertTrue(team.unregisterMember("reviewer"));
    }

    private Agent createRoleAgent(String systemPrompt, String instructions) {
        return Agents.builder()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt(systemPrompt)
                .instructions(instructions)
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();
    }

    private AgentTeamResult runTeamWithRetry(AgentTeam team, String objective, int maxAttempts) throws Exception {
        AgentTeamResult lastResult = null;
        for (int i = 0; i < maxAttempts; i++) {
            lastResult = callWithProviderGuard(() -> team.run(objective));
            if (lastResult != null && allTasksCompleted(lastResult.getTaskStates())) {
                return lastResult;
            }
        }
        return lastResult;
    }

    private boolean allTasksCompleted(List<AgentTeamTaskState> states) {
        if (states == null || states.isEmpty()) {
            return false;
        }
        for (AgentTeamTaskState state : states) {
            if (state == null || state.getStatus() != AgentTeamTaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private String describeTaskStates(List<AgentTeamTaskState> states) {
        if (states == null || states.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < states.size(); i++) {
            AgentTeamTaskState state = states.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            if (state == null) {
                sb.append("null");
                continue;
            }
            sb.append(state.getTaskId()).append(":").append(state.getStatus());
            if (state.getError() != null && !state.getError().isEmpty()) {
                sb.append("(error=").append(state.getError()).append(")");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
