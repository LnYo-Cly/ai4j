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
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Best-practice Agent Teams demo using Doubao:
 * - deterministic dependency plan to make orchestration stable in tests
 * - Doubao-powered teammates + synthesizer
 * - message bus + hooks + plan approval enabled
 */
public class DoubaoAgentTeamBestPracticeTest {

    private static final String MODEL = (System.getenv("ZHIPU_MODEL") == null || System.getenv("ZHIPU_MODEL").isEmpty())
            ? "GLM-4.5-Flash"
            : System.getenv("ZHIPU_MODEL");

    private AiService aiService;

    @Before
    public void init() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ZHIPU_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "1cbd1960cdc7e9144ded698a9763569b.seHlVxdOq3eTnY9m";
        }
        Assume.assumeTrue(apiKey != null && !apiKey.isEmpty());

        ZhipuConfig zhipuConfig = new ZhipuConfig();
        zhipuConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setZhipuConfig(zhipuConfig);

        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
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
    public void test_doubao_agent_team_best_practice() throws Exception {
        Agent northWeatherAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(MODEL)
                .temperature(0.7)
                .systemPrompt("You are a weather data specialist. You must call queryWeather before answering.")
                .instructions("Use queryWeather(location, type=daily, days=1). Return concise JSON only.")
                .toolRegistry(Collections.singletonList("queryWeather"), null)
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        Agent southWeatherAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(MODEL)
                .temperature(0.7)
                .systemPrompt("You are a weather data specialist. You must call queryWeather before answering.")
                .instructions("Use queryWeather(location, type=daily, days=1). Return concise JSON only.")
                .toolRegistry(Collections.singletonList("queryWeather"), null)
                .options(AgentOptions.builder().maxSteps(3).build())
                .build();

        Agent analystAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(MODEL)
                .temperature(0.7)
                .systemPrompt("You are a weather risk analyst. Compare city weather and provide travel guidance.")
                .instructions("Use teammate outputs. Produce compact bullet points.")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        Agent synthesizerAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(MODEL)
                .temperature(0.7)
                .systemPrompt("You are the team lead. Merge teammate results into final structured JSON.")
                .instructions("Output JSON with fields: summary, cityComparisons, travelAdvice.")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        final AtomicInteger beforeTaskCount = new AtomicInteger();
        final AtomicInteger afterTaskCount = new AtomicInteger();
        final AtomicInteger messageCount = new AtomicInteger();

        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .rawPlanText("deterministic_best_practice_plan")
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("north_weather")
                                        .memberId("north_weather")
                                        .task("Get a 1-day daily weather forecast for Beijing and return compact JSON.")
                                        .context("city=Beijing")
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("south_weather")
                                        .memberId("south_weather")
                                        .task("Get a 1-day daily weather forecast for Shenzhen and return compact JSON.")
                                        .context("city=Shenzhen")
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("risk_analysis")
                                        .memberId("analyst")
                                        .task("Compare both weather reports and provide practical travel suggestions.")
                                        .dependsOn(Arrays.asList("north_weather", "south_weather"))
                                        .build()
                        ))
                        .build())
                .synthesizerAgent(synthesizerAgent)
                .member(AgentTeamMember.builder()
                        .id("north_weather")
                        .name("North Weather")
                        .description("Fetches weather for northern city")
                        .agent(northWeatherAgent)
                        .build())
                .member(AgentTeamMember.builder()
                        .id("south_weather")
                        .name("South Weather")
                        .description("Fetches weather for southern city")
                        .agent(southWeatherAgent)
                        .build())
                .member(AgentTeamMember.builder()
                        .id("analyst")
                        .name("Analyst")
                        .description("Compares reports and produces advice")
                        .agent(analystAgent)
                        .build())
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(3)
                        .enableMessageBus(true)
                        .includeMessageHistoryInDispatch(true)
                        .messageHistoryLimit(50)
                        .requirePlanApproval(true)
                        .maxRounds(12)
                        .build())
                .planApproval((objective, plan, members, options) -> {
                    if (plan == null || plan.getTasks() == null || plan.getTasks().isEmpty()) {
                        return false;
                    }
                    Set<String> validMembers = new HashSet<String>();
                    for (AgentTeamMember member : members) {
                        validMembers.add(member.resolveId());
                    }
                    for (AgentTeamTask task : plan.getTasks()) {
                        if (!validMembers.contains(task.getMemberId())) {
                            return false;
                        }
                    }
                    return true;
                })
                .hook(new AgentTeamHook() {
                    @Override
                    public void beforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
                        beforeTaskCount.incrementAndGet();
                        System.out.println("TEAM TASK START: " + task.getId() + " -> " + member.resolveId());
                    }

                    @Override
                    public void afterTask(String objective, AgentTeamMemberResult result) {
                        afterTaskCount.incrementAndGet();
                        System.out.println("TEAM TASK END: " + result.getTaskId() + " status=" + result.getTaskStatus());
                    }

                    @Override
                    public void onMessage(AgentTeamMessage message) {
                        messageCount.incrementAndGet();
                        System.out.println("TEAM MSG: [" + message.getType() + "] "
                                + message.getFromMemberId() + " -> " + message.getToMemberId()
                                + " | " + message.getContent());
                    }
                })
                .build();

        team.publishMessage(AgentTeamMessage.builder()
                .id("seed-message")
                .fromMemberId("lead")
                .toMemberId("*")
                .type("run.context")
                .content("Use factual weather data. Keep output concise and actionable.")
                .createdAt(System.currentTimeMillis())
                .build());

        AgentTeamResult result = team.run("Create a two-city weather briefing for Beijing and Shenzhen.");

        System.out.println("TEAM ROUNDS: " + result.getRounds());
        System.out.println("TEAM TASK STATES: " + result.getTaskStates());
        System.out.println("TEAM OUTPUT: " + result.getOutput());

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutput());
        Assert.assertTrue(result.getOutput().length() > 0);
        Assert.assertTrue(result.getRounds() >= 2);
        Assert.assertNotNull(result.getTaskStates());
        Assert.assertEquals(3, result.getTaskStates().size());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, result.getTaskStates().get(0).getStatus());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, result.getTaskStates().get(1).getStatus());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, result.getTaskStates().get(2).getStatus());
        Assert.assertTrue(beforeTaskCount.get() >= 3);
        Assert.assertTrue(afterTaskCount.get() >= 3);
        Assert.assertTrue(messageCount.get() > 0);
        Assert.assertNotNull(result.getMessages());
        Assert.assertTrue(result.getMessages().size() > 0);
    }
}

