package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.agent.workflow.AgentNode;
import io.github.lnyocly.ai4j.agent.workflow.StateGraphWorkflow;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

public class StateGraphWorkflowTest {

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
    public void test_branching_route() throws Exception {
        StateGraphWorkflow workflow = new StateGraphWorkflow()
                .addNode("start", new StaticNode("cold"))
                .addNode("cold", new StaticNode("take_coat"))
                .addNode("warm", new StaticNode("t_shirt"))
                .start("start")
                .addConditionalEdges("start", (context, request, result) -> {
                    return "cold".equals(result.getOutputText()) ? "cold" : "warm";
                });

        AgentResult result = workflow.run(new AgentSession(null, null), AgentRequest.builder().input("go").build());
        Assert.assertEquals("take_coat", result.getOutputText());
    }

    @Test
    public void test_loop_route() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);

        StateGraphWorkflow workflow = new StateGraphWorkflow()
                .addNode("loop", new CounterNode(counter))
                .addNode("done", new StaticNode("done"))
                .start("loop")
                .maxSteps(10)
                .addConditionalEdges("loop", (context, request, result) -> {
                    Object value = context.get("count");
                    if (value instanceof Integer && ((Integer) value) < 3) {
                        return "loop";
                    }
                    return "done";
                });

        AgentResult result = workflow.run(new AgentSession(null, null), AgentRequest.builder().input("start").build());
        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(3, counter.get());
    }

    @Test
    public void test_state_graph_with_agents() throws Exception {
        Agent routerAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You are a router. Output only ROUTE_WEATHER or ROUTE_GENERIC.")
                .instructions("If the user asks about weather or temperature, output ROUTE_WEATHER. Otherwise output ROUTE_GENERIC.")
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        Agent weatherAgent = Agents.react()
                .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You are a weather assistant. Always call queryWeather before answering.")
                .instructions("Use queryWeather with the user's location, type=now, days=1.")
                .toolRegistry(Arrays.asList("queryWeather"), null)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        Agent genericAgent = Agents.react()
                .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You answer general questions in one short sentence.")
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        Agent formatAgent = Agents.react()
                .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
                .model("doubao-seed-1-8-251228")
                .systemPrompt("You format responses into JSON.")
                .instructions("Return JSON with fields: route, answer.")
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        StateGraphWorkflow workflow = new StateGraphWorkflow()
                .addNode("decide", new NamedNode("Decide", new RoutingAgentNode(routerAgent.newSession())))
                .addNode("weather", new NamedNode("Weather", new RuntimeAgentNode(weatherAgent.newSession())))
                .addNode("generic", new NamedNode("Generic", new RuntimeAgentNode(genericAgent.newSession())))
                .addNode("format", new NamedNode("Format", new FormatAgentNode(formatAgent.newSession())))
                .start("decide")
                .addConditionalEdges("decide", (context, request, result) -> String.valueOf(context.get("route")))
                .addEdge("weather", "format")
                .addEdge("generic", "format");

        AgentResult result = workflow.run(new AgentSession(null, null), AgentRequest.builder()
                .input("What is the weather in Beijing today?")
                .build());

        System.out.println("FINAL OUTPUT: " + result.getOutputText());
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().contains("\"route\""));
    }

    private static class StaticNode implements AgentNode {
        private final String output;

        private StaticNode(String output) {
            this.output = output;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) {
            return AgentResult.builder().outputText(output).build();
        }
    }

    private static class CounterNode implements AgentNode {
        private final AtomicInteger counter;

        private CounterNode(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) {
            int current = counter.incrementAndGet();
            context.put("count", current);
            return AgentResult.builder().outputText("count=" + current).build();
        }
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

    private static class RoutingAgentNode implements AgentNode {
        private final AgentSession session;

        private RoutingAgentNode(AgentSession session) {
            this.session = session;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
            AgentResult result = session.run(request);
            String output = result == null ? null : result.getOutputText();
            if (output != null && output.contains("ROUTE_WEATHER")) {
                context.put("route", "weather");
            } else {
                context.put("route", "generic");
            }
            return AgentResult.builder()
                    .outputText(request == null || request.getInput() == null ? null : String.valueOf(request.getInput()))
                    .rawResponse(result == null ? null : result.getRawResponse())
                    .build();
        }
    }

    private static class RuntimeAgentNode implements AgentNode {
        private final AgentSession session;

        private RuntimeAgentNode(AgentSession session) {
            this.session = session;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
            return session.run(request);
        }
    }

    private static class FormatAgentNode implements AgentNode {
        private final AgentSession session;

        private FormatAgentNode(AgentSession session) {
            this.session = session;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
            String route = context.get("route") == null ? "unknown" : String.valueOf(context.get("route"));
            String input = request == null || request.getInput() == null ? "" : String.valueOf(request.getInput());
            AgentRequest formatted = AgentRequest.builder()
                    .input("route=" + route + "\nanswer=" + input)
                    .build();
            return session.run(formatted);
        }
    }
}


