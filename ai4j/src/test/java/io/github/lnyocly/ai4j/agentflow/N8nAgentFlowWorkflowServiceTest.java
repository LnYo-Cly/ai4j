package io.github.lnyocly.ai4j.agentflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowRequest;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowResponse;
import io.github.lnyocly.ai4j.agentflow.workflow.N8nAgentFlowWorkflowService;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.factory.AiService;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class N8nAgentFlowWorkflowServiceTest {

    @Test
    public void test_n8n_workflow_blocking() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true,\"result\":\"Booked\",\"city\":\"Paris\"}"));
        server.start();
        try {
            N8nAgentFlowWorkflowService service = new N8nAgentFlowWorkflowService(configuration(), AgentFlowConfig.builder()
                    .type(AgentFlowType.N8N)
                    .webhookUrl(server.url("/travel-hook").toString())
                    .build());
            AgentFlowWorkflowResponse response = service.run(AgentFlowWorkflowRequest.builder()
                    .inputs(java.util.Collections.<String, Object>singletonMap("city", "Paris"))
                    .build());

            Assert.assertEquals("Booked", response.getOutputText());
            Assert.assertEquals(Boolean.TRUE, response.getOutputs().get("ok"));
            Assert.assertEquals("Paris", response.getOutputs().get("city"));

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/travel-hook", request.getPath());
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("Paris", body.getString("city"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_ai_service_exposes_agent_flow() {
        Configuration configuration = configuration();
        AiService aiService = new AiService(configuration);
        AgentFlow agentFlow = aiService.getAgentFlow(AgentFlowConfig.builder()
                .type(AgentFlowType.N8N)
                .webhookUrl("https://example.com/hook")
                .build());

        Assert.assertNotNull(agentFlow);
        Assert.assertEquals(AgentFlowType.N8N, agentFlow.getConfig().getType());
        Assert.assertNotNull(agentFlow.workflow());
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build());
        return configuration;
    }
}
