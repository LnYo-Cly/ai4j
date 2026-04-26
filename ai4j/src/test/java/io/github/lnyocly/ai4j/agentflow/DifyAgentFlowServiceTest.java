package io.github.lnyocly.ai4j.agentflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatEvent;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatListener;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatRequest;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatResponse;
import io.github.lnyocly.ai4j.agentflow.chat.DifyAgentFlowChatService;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowEvent;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowListener;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowRequest;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowResponse;
import io.github.lnyocly.ai4j.agentflow.workflow.DifyAgentFlowWorkflowService;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DifyAgentFlowServiceTest {

    @Test
    public void test_dify_chat_blocking() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"answer\":\"Hello from Dify\",\"conversation_id\":\"conv-1\",\"message_id\":\"msg-1\",\"task_id\":\"task-1\",\"metadata\":{\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5,\"total_tokens\":8}}}"));
        server.start();
        try {
            DifyAgentFlowChatService service = new DifyAgentFlowChatService(configuration(), difyConfig(server));
            AgentFlowChatResponse response = service.chat(AgentFlowChatRequest.builder()
                    .prompt("hello")
                    .conversationId("conv-1")
                    .build());

            Assert.assertEquals("Hello from Dify", response.getContent());
            Assert.assertEquals("conv-1", response.getConversationId());
            Assert.assertEquals(Integer.valueOf(8), response.getUsage().getTotalTokens());

            RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(recordedRequest);
            Assert.assertEquals("/v1/chat-messages", recordedRequest.getPath());
            JSONObject body = JSON.parseObject(recordedRequest.getBody().readUtf8());
            Assert.assertEquals("hello", body.getString("query"));
            Assert.assertEquals("blocking", body.getString("response_mode"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_dify_chat_streaming() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(sseResponse(
                "event: message\n" +
                        "data: {\"event\":\"message\",\"answer\":\"Hel\",\"conversation_id\":\"conv-1\",\"message_id\":\"msg-1\",\"task_id\":\"task-1\"}\n\n" +
                        "event: message\n" +
                        "data: {\"event\":\"message\",\"answer\":\"lo\"}\n\n" +
                        "event: message_end\n" +
                        "data: {\"event\":\"message_end\",\"conversation_id\":\"conv-1\",\"message_id\":\"msg-1\",\"task_id\":\"task-1\",\"metadata\":{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}}\n\n"
        ));
        server.start();
        try {
            DifyAgentFlowChatService service = new DifyAgentFlowChatService(configuration(), difyConfig(server));
            final List<AgentFlowChatEvent> events = new ArrayList<AgentFlowChatEvent>();
            final AtomicReference<AgentFlowChatResponse> completion = new AtomicReference<AgentFlowChatResponse>();

            service.chatStream(AgentFlowChatRequest.builder().prompt("hello").build(), new AgentFlowChatListener() {
                @Override
                public void onEvent(AgentFlowChatEvent event) {
                    events.add(event);
                }

                @Override
                public void onComplete(AgentFlowChatResponse response) {
                    completion.set(response);
                }
            });

            Assert.assertEquals(3, events.size());
            Assert.assertEquals("Hel", events.get(0).getContentDelta());
            Assert.assertEquals("Hello", completion.get().getContent());
            Assert.assertEquals(Integer.valueOf(3), completion.get().getUsage().getTotalTokens());

            RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
            JSONObject body = JSON.parseObject(recordedRequest.getBody().readUtf8());
            Assert.assertEquals("streaming", body.getString("response_mode"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_dify_workflow_blocking() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"task_id\":\"task-1\",\"workflow_run_id\":\"run-1\",\"data\":{\"status\":\"succeeded\",\"outputs\":{\"answer\":\"Plan ready\",\"city\":\"Paris\"},\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}}}"));
        server.start();
        try {
            DifyAgentFlowWorkflowService service = new DifyAgentFlowWorkflowService(configuration(), difyConfig(server));
            AgentFlowWorkflowResponse response = service.run(AgentFlowWorkflowRequest.builder().build());

            Assert.assertEquals("succeeded", response.getStatus());
            Assert.assertEquals("Plan ready", response.getOutputText());
            Assert.assertEquals("run-1", response.getWorkflowRunId());
            Assert.assertEquals("Paris", response.getOutputs().get("city"));

            RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/workflows/run", recordedRequest.getPath());
            JSONObject body = JSON.parseObject(recordedRequest.getBody().readUtf8());
            Assert.assertEquals("blocking", body.getString("response_mode"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_dify_workflow_streaming() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(sseResponse(
                "event: workflow_started\n" +
                        "data: {\"event\":\"workflow_started\",\"task_id\":\"task-1\",\"workflow_run_id\":\"run-1\"}\n\n" +
                        "event: workflow_finished\n" +
                        "data: {\"event\":\"workflow_finished\",\"task_id\":\"task-1\",\"workflow_run_id\":\"run-1\",\"data\":{\"status\":\"succeeded\",\"outputs\":{\"answer\":\"Plan ready\",\"city\":\"Paris\"},\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}}}\n\n"
        ));
        server.start();
        try {
            DifyAgentFlowWorkflowService service = new DifyAgentFlowWorkflowService(configuration(), difyConfig(server));
            final List<AgentFlowWorkflowEvent> events = new ArrayList<AgentFlowWorkflowEvent>();
            final AtomicReference<AgentFlowWorkflowResponse> completion = new AtomicReference<AgentFlowWorkflowResponse>();

            service.runStream(AgentFlowWorkflowRequest.builder().build(), new AgentFlowWorkflowListener() {
                @Override
                public void onEvent(AgentFlowWorkflowEvent event) {
                    events.add(event);
                }

                @Override
                public void onComplete(AgentFlowWorkflowResponse response) {
                    completion.set(response);
                }
            });

            Assert.assertEquals(2, events.size());
            Assert.assertTrue(events.get(1).isDone());
            Assert.assertEquals("Plan ready", completion.get().getOutputText());
            Assert.assertEquals("Paris", completion.get().getOutputs().get("city"));

            RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
            JSONObject body = JSON.parseObject(recordedRequest.getBody().readUtf8());
            Assert.assertEquals("streaming", body.getString("response_mode"));
        } finally {
            server.shutdown();
        }
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build());
        return configuration;
    }

    private AgentFlowConfig difyConfig(MockWebServer server) {
        return AgentFlowConfig.builder()
                .type(AgentFlowType.DIFY)
                .baseUrl(server.url("/").toString())
                .apiKey("test-key")
                .pollTimeoutMillis(3_000L)
                .build();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private MockResponse sseResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}
