package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
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
import java.util.concurrent.TimeUnit;

public class CodeActRuntimeTest {

    private AiService aiService;

    @Before
    public void init() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DOUBAO_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getProperty("doubao.api.key");
        }
        Assume.assumeTrue(apiKey != null && !apiKey.isEmpty());
        DoubaoConfig doubaoConfig = new DoubaoConfig();
        doubaoConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setDoubaoConfig(doubaoConfig);

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
    public void test_codeact_with_tool() throws Exception {
        Agent agent = Agents.codeAct()
                .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You are a weather assistant. Use Python only. Always call queryWeather with args {location, type, days}. Use type \"daily\" and days 1. Return a final answer string. If you do not return, set __codeact_result.")
                .toolRegistry(Arrays.asList("queryWeather"), null)
                .options(AgentOptions.builder().maxSteps(4).build())
                .codeActOptions(CodeActOptions.builder().reAct(true).build())
                .eventPublisher(buildEventPublisher())
                .build();

        AgentResult result = agent.run(AgentRequest.builder()
                .input("Use Python to query weather for Beijing, Shanghai, and Shenzhen. Call queryWeather with type \"daily\" and days 1 for each city, then return a single summary string.")
                .build());

        System.out.println("CODEACT OUTPUT: " + result.getOutputText());
        if (result.getToolCalls() != null && !result.getToolCalls().isEmpty()) {
            System.out.println("CODEACT CODE: " + result.getToolCalls().get(0).getArguments());
        }
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().length() > 0);
        Assert.assertFalse(result.getOutputText().contains("CODE_ERROR"));
        Assert.assertTrue(result.getToolResults() != null && !result.getToolResults().isEmpty());
    }

    private AgentEventPublisher buildEventPublisher() {
        AgentEventPublisher publisher = new AgentEventPublisher();
        publisher.addListener(event -> logToolCall(event));
        return publisher;
    }

    private void logToolCall(AgentEvent event) {
        if (event == null || event.getType() != AgentEventType.TOOL_CALL) {
            return;
        }
        Object payload = event.getPayload();
        if (!(payload instanceof AgentToolCall)) {
            return;
        }
        AgentToolCall call = (AgentToolCall) payload;
        if (!"code".equals(call.getName())) {
            return;
        }
        System.out.println("CODEACT CODE (pre-exec): " + call.getArguments());
    }
}




