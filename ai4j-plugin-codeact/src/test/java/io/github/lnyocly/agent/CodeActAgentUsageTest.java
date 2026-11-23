package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.network.OkHttpUtil;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Category(LiveProviderTest.class)
public class CodeActAgentUsageTest {

    private static final String DEFAULT_MODEL = "GLM-4.5-Flash";
    private AiService aiService;
    private String model;

    @Before
    public void setupZhipuAiService() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ZHIPU_API_KEY");
        Assume.assumeTrue("Skip because Zhipu API key is not configured. Expected env var: ZHIPU_API_KEY",
                apiKey != null && !apiKey.trim().isEmpty());

        model = System.getenv("ZHIPU_MODEL");
        if (model == null || model.isEmpty()) {
            model = System.getProperty("zhipu.model");
        }
        if (model == null || model.isEmpty()) {
            model = DEFAULT_MODEL;
        }

        ZhipuConfig zhipuConfig = new ZhipuConfig();
        zhipuConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setZhipuConfig(zhipuConfig);

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

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
    public void test_codeact_basic_js_execution() throws Exception {
        Assume.assumeTrue(isNashornAvailable());

        Agent agent = Agents.codeAct()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(model)
                .temperature(0.7)
                .maxOutputTokens(512)
                .codeExecutor(new NashornCodeExecutor())
                .systemPrompt("你是代码执行助手。只允许输出 JSON。优先使用 JavaScript 计算并返回最终结果。")
                .codeActOptions(CodeActOptions.builder().reAct(false).build())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = runWithRetry(agent, AgentRequest.builder().input("请用JavaScript计算 17*3+5 ，只返回最终数字").build(), 2);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
        Assert.assertFalse(result.getOutputText().contains("CODE_ERROR"));
    }

    @Test
    public void test_codeact_react_finalize_flow_without_external_tool() throws Exception {
        Assume.assumeTrue(isNashornAvailable());

        Agent agent = Agents.codeAct()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.ZHIPU)))
                .model(model)
                .temperature(0.7)
                .maxOutputTokens(512)
                .codeExecutor(new NashornCodeExecutor())
                .systemPrompt("你是代码执行助手。先输出可执行 JavaScript，再输出最终结果。保持简短。")
                .instructions("用 JavaScript 计算 88 除以 11。")
                .codeActOptions(CodeActOptions.builder().reAct(true).build())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = runWithRetry(agent, AgentRequest.builder().input("请先写代码再返回最终结果，不要解释").build(), 2);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
        Assert.assertFalse(result.getOutputText().contains("CODE_ERROR"));
    }

    private boolean isNashornAvailable() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        return engine != null;
    }

    private AgentResult runWithRetry(Agent agent, AgentRequest request, int maxAttempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return agent.run(request);
            } catch (Exception ex) {
                last = ex;
                if (i + 1 >= maxAttempts || !isRetryable(ex)) {
                    throw ex;
                }
            }
        }
        throw last == null ? new RuntimeException("CodeAct run failed without exception detail") : last;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
