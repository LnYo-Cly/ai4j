package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamHook;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.network.OkHttpUtil;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 使用豆包真实大模型的 Agent Teams 集成测试。
 * 场景：模拟完整研发小组（架构/后端/前端/测试/运维）协作完成项目交付规划。
 */
public class DoubaoProjectTeamAgentTeamsTest {

    private static final String DEFAULT_API_KEY = "1cbd1960cdc7e9144ded698a9763569b.seHlVxdOq3eTnY9m";
    private static final String MODEL = (System.getenv("ZHIPU_MODEL") == null || System.getenv("ZHIPU_MODEL").isEmpty())
            ? "GLM-4.5-Flash"
            : System.getenv("ZHIPU_MODEL");

    private AiService aiService;

    @Before
    public void init() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ZHIPU_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = DEFAULT_API_KEY;
        }

        ZhipuConfig zhipuConfig = new ZhipuConfig();
        zhipuConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setZhipuConfig(zhipuConfig);

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        // ????????????????????CI/???????????????
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .sslSocketFactory(OkHttpUtil.getIgnoreInitedSslContext().getSocketFactory(), OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509)
                .hostnameVerifier(OkHttpUtil.getIgnoreSslHostnameVerifier())
                .build();

        configuration.setOkHttpClient(okHttpClient);
        aiService = new AiService(configuration);
    }

    @Test
    public void test_project_delivery_team_with_doubao() throws Exception {
        // 1) 构建不同职责的 Agent（同一模型，不同 systemPrompt/instructions）
        Agent architect = createRoleAgent(
                "You are the software architect.",
                "Define architecture boundaries, API contract shape, and key risks in concise bullet points.");

        Agent backend = createRoleAgent(
                "You are the backend engineer.",
                "Create backend implementation plan with modules, API endpoints, and schema changes. "
                        + "Use team_send_message to notify frontend about API contract changes when needed.");

        Agent frontend = createRoleAgent(
                "You are the frontend engineer.",
                "Create frontend implementation plan with pages/components/state/API integration. "
                        + "Use team_send_message to ask backend for contract clarification when needed.");

        Agent qa = createRoleAgent(
                "You are the QA engineer.",
                "Build a concise quality plan: smoke/regression/e2e, acceptance criteria, and release gate. "
                        + "Use team_list_tasks if needed.");

        Agent ops = createRoleAgent(
                "You are the DevOps engineer.",
                "Provide deployment, monitoring, alerting, rollback strategy, and production readiness checklist.");

        Agent lead = createRoleAgent(
                "You are the team lead.",
                "You are responsible for both planning and final synthesis. "
                        + "First break objective into executable tasks and assign to member ids: architect, backend, frontend, qa, ops. "
                        + "Then merge member outputs into one sprint delivery plan with sections: architecture, backend, frontend, qa, ops, milestones, risks.");

        // 2) 构建 Team：固定规划任务 DAG，成员并发执行，最后由 lead 汇总
        AgentTeam team = Agents.team()
                .leadAgent(lead)
                .member(AgentTeamMember.builder().id("architect").name("Architect").description("architecture").agent(architect).build())
                .member(AgentTeamMember.builder().id("backend").name("Backend").description("backend").agent(backend).build())
                .member(AgentTeamMember.builder().id("frontend").name("Frontend").description("frontend").agent(frontend).build())
                .member(AgentTeamMember.builder().id("qa").name("QA").description("quality").agent(qa).build())
                .member(AgentTeamMember.builder().id("ops").name("Ops").description("operations").agent(ops).build())
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(3)
                        .enableMessageBus(true)
                        .includeMessageHistoryInDispatch(true)
                        .messageHistoryLimit(50)
                        .enableMemberTeamTools(false)
                        .maxRounds(16)
                        .build())
                .hook(new AgentTeamHook() {
                    @Override
                    public void beforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
                        System.out.println("TEAM TASK START: " + task.getId() + " -> " + member.resolveId());
                    }

                    @Override
                    public void afterTask(String objective, AgentTeamMemberResult result) {
                        System.out.println("TEAM TASK END: " + result.getTaskId() + " | " + result.getTaskStatus());
                    }

                    @Override
                    public void onMessage(AgentTeamMessage message) {
                        System.out.println("TEAM MSG: [" + message.getType() + "] "
                                + message.getFromMemberId() + " -> " + message.getToMemberId()
                                + " | " + message.getContent());
                    }
                })
                .build();

        // 3) 执行团队协作任务
        AgentTeamResult result = team.run("Build a production-ready task management web application in one sprint.");

        System.out.println("TEAM ROUNDS: " + result.getRounds());
        System.out.println("TEAM OUTPUT:\n" + result.getOutput());

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutput());
        Assert.assertTrue(result.getOutput().length() > 120);
        Assert.assertNotNull(result.getTaskStates());
        Assert.assertTrue(result.getTaskStates().size() > 0);
        Assert.assertNotNull(result.getMemberResults());
        Assert.assertTrue(result.getMemberResults().size() > 0);
        Assert.assertTrue(result.getRounds() >= 1);

        int completedCount = 0;
        for (AgentTeamTaskState state : result.getTaskStates()) {
            if (state != null && state.getStatus() == AgentTeamTaskStatus.COMPLETED) {
                completedCount++;
            }
        }
        Assert.assertTrue("expected at least one completed task", completedCount > 0);

        Assert.assertNotNull(result.getMessages());
        Assert.assertTrue(result.getMessages().size() > 0);
    }

    // 统一创建 ReAct Agent，便于按角色切换 systemPrompt 与 instructions
    private Agent createRoleAgent(String systemPrompt, String instructions) {
        return Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(MODEL)
                .temperature(0.7)
                .systemPrompt(systemPrompt)
                .instructions(instructions)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();
    }
}


