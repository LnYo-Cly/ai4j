package io.github.lnyocly.ai4j.agentflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatEvent;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatListener;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatRequest;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatResponse;
import io.github.lnyocly.ai4j.agentflow.chat.CozeAgentFlowChatService;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowEvent;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowListener;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowRequest;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowResponse;
import io.github.lnyocly.ai4j.agentflow.workflow.CozeAgentFlowWorkflowService;
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

public class CozeAgentFlowServiceTest {

    @Test
    public void test_coze_chat_blocking() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"code\":0,\"msg\":\"success\",\"data\":{\"id\":\"chat-1\",\"conversation_id\":\"conv-1\",\"status\":\"created\"}}"));
        server.enqueue(jsonResponse("{\"code\":0,\"msg\":\"success\",\"data\":{\"id\":\"chat-1\",\"conversation_id\":\"conv-1\",\"status\":\"completed\",\"usage\":{\"token_count\":9,\"input_tokens\":4,\"output_tokens\":5}}}"));
        server.enqueue(jsonResponse("{\"code\":0,\"msg\":\"success\",\"data\":[{\"id\":\"msg-user\",\"role\":\"user\",\"content\":\"hello\"},{\"id\":\"msg-1\",\"role\":\"assistant\",\"content\":\"Travel plan ready\"}]}"));
        server.start();
        try {
            CozeAgentFlowChatService service = new CozeAgentFlowChatService(configuration(), cozeConfig(server));
            AgentFlowChatResponse response = service.chat(AgentFlowChatRequest.builder().prompt("hello").conversationId("conv-1").build());

            Assert.assertEquals("Travel plan ready", response.getContent());
            Assert.assertEquals("conv-1", response.getConversationId());
            Assert.assertEquals("chat-1", response.getTaskId());
            Assert.assertEquals(Integer.valueOf(9), response.getUsage().getTotalTokens());

            RecordedRequest createRequest = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest retrieveRequest = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest listRequest = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertTrue(createRequest.getPath().startsWith("/v3/chat"));
            Assert.assertTrue(retrieveRequest.getPath().startsWith("/v3/chat/retrieve"));
            Assert.assertTrue(listRequest.getPath().startsWith("/v1/conversation/message/list"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_coze_chat_streaming() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(sseResponse(
                "event: conversation.chat.created\n" +
                        "data: {\"id\":\"chat-1\",\"conversation_id\":\"conv-1\",\"status\":\"created\"}\n\n" +
                        "event: conversation.message.delta\n" +
                        "data: {\"id\":\"msg-1\",\"conversation_id\":\"conv-1\",\"chat_id\":\"chat-1\",\"role\":\"assistant\",\"type\":\"answer\",\"content\":\"Travel \",\"content_type\":\"text\"}\n\n" +
                        "event: conversation.message.delta\n" +
                        "data: {\"id\":\"msg-1\",\"conversation_id\":\"conv-1\",\"chat_id\":\"chat-1\",\"role\":\"assistant\",\"type\":\"answer\",\"content\":\"ready\",\"content_type\":\"text\"}\n\n" +
                        "event: conversation.chat.completed\n" +
                        "data: {\"id\":\"chat-1\",\"conversation_id\":\"conv-1\",\"status\":\"completed\",\"usage\":{\"token_count\":7,\"input_tokens\":3,\"output_tokens\":4}}\n\n" +
                        "event: done\n" +
                        "data: [DONE]\n\n"
        ));
        server.start();
        try {
            CozeAgentFlowChatService service = new CozeAgentFlowChatService(configuration(), cozeConfig(server));
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

            Assert.assertEquals("Travel ready", completion.get().getContent());
            Assert.assertEquals("chat-1", completion.get().getTaskId());
            Assert.assertEquals(Integer.valueOf(7), completion.get().getUsage().getTotalTokens());
            Assert.assertTrue(events.size() >= 4);
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_coze_workflow_blocking() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"code\":0,\"msg\":\"success\",\"execute_id\":\"exec-1\",\"data\":\"{\\\"answer\\\":\\\"Workflow ready\\\",\\\"city\\\":\\\"Paris\\\"}\",\"usage\":{\"token_count\":6,\"input_tokens\":2,\"output_tokens\":4}}"));
        server.start();
        try {
            CozeAgentFlowWorkflowService service = new CozeAgentFlowWorkflowService(configuration(), cozeConfig(server));
            AgentFlowWorkflowResponse response = service.run(AgentFlowWorkflowRequest.builder().workflowId("wf-1").build());

            Assert.assertEquals("Workflow ready", response.getOutputText());
            Assert.assertEquals("exec-1", response.getWorkflowRunId());
            Assert.assertEquals("Paris", response.getOutputs().get("city"));

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/workflow/run", request.getPath());
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("wf-1", body.getString("workflow_id"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_coze_workflow_streaming() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(sseResponse(
                "event: Message\n" +
                        "data: {\"content\":\"Workflow \",\"usage\":{\"token_count\":3,\"input_tokens\":1,\"output_tokens\":2}}\n\n" +
                        "event: Message\n" +
                        "data: {\"content\":\"ready\"}\n\n" +
                        "event: Done\n" +
                        "data: {}\n\n"
        ));
        server.start();
        try {
            CozeAgentFlowWorkflowService service = new CozeAgentFlowWorkflowService(configuration(), cozeConfig(server));
            final List<AgentFlowWorkflowEvent> events = new ArrayList<AgentFlowWorkflowEvent>();
            final AtomicReference<AgentFlowWorkflowResponse> completion = new AtomicReference<AgentFlowWorkflowResponse>();

            service.runStream(AgentFlowWorkflowRequest.builder().workflowId("wf-1").build(), new AgentFlowWorkflowListener() {
                @Override
                public void onEvent(AgentFlowWorkflowEvent event) {
                    events.add(event);
                }

                @Override
                public void onComplete(AgentFlowWorkflowResponse response) {
                    completion.set(response);
                }
            });

            Assert.assertEquals("Workflow ready", completion.get().getOutputText());
            Assert.assertEquals(Integer.valueOf(3), completion.get().getUsage().getTotalTokens());
            Assert.assertTrue(events.get(events.size() - 1).isDone());
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

    private AgentFlowConfig cozeConfig(MockWebServer server) {
        return AgentFlowConfig.builder()
                .type(AgentFlowType.COZE)
                .baseUrl(server.url("/").toString())
                .apiKey("test-key")
                .botId("bot-1")
                .pollIntervalMillis(1L)
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
