package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.agent.workflow.AgentNode;
import io.github.lnyocly.ai4j.agent.workflow.RuntimeAgentNode;
import io.github.lnyocly.ai4j.agent.workflow.SequentialWorkflow;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowAgent;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowContext;
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

public class WeatherAgentWorkflowTest {

    private AiService aiService;

    @Before
    public void init() throws NoSuchAlgorithmException, KeyManagementException {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DOUBAO_API_KEY");
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
    public void test_weather_workflow() throws Exception {
        Agent weatherAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You are a weather analyst. Always call queryWeather before answering.")
                .instructions("Use queryWeather with the user's location, type=now, days=1.")
                .toolRegistry(Arrays.asList("queryWeather"), null)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        Agent formatAgent = Agents.react()
                .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You format weather analysis into strict JSON.")
                .instructions("Return JSON with fields: city, summary, advice.")
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        SequentialWorkflow workflow = new SequentialWorkflow()
                .addNode(new NamedNode("WeatherAnalysis", new RuntimeAgentNode(weatherAgent.newSession())))
                .addNode(new NamedNode("FormatOutput", new RuntimeAgentNode(formatAgent.newSession())));

        WorkflowAgent runner = new WorkflowAgent(workflow, weatherAgent.newSession());
        AgentResult result = runner.run(AgentRequest.builder()
                .input("Get the current weather in Beijing and provide advice.")
                .build());

        System.out.println("FINAL OUTPUT: " + result.getOutputText());
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().length() > 0);
    }

    private static class NamedNode implements AgentNode {
        private final String name;
        private final AgentNode delegate;

        private NamedNode(String name, AgentNode delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
            System.out.println("NODE START: " + name);
            try {
                AgentResult result = delegate.execute(context, request);
                System.out.println("NODE END: " + name + " | status=OK");
                return result;
            } catch (Exception e) {
                System.out.println("NODE END: " + name + " | status=ERROR");
                throw e;
            }
        }
    }
}


