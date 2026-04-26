package io.github.lnyocly.ai4j.agentflow.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agentflow.AgentFlowConfig;
import io.github.lnyocly.ai4j.agentflow.AgentFlowException;
import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import io.github.lnyocly.ai4j.agentflow.support.AgentFlowSupport;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceContext;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DifyAgentFlowChatService extends AgentFlowSupport implements AgentFlowChatService {

    public DifyAgentFlowChatService(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        super(configuration, agentFlowConfig);
    }

    @Override
    public AgentFlowChatResponse chat(AgentFlowChatRequest request) throws Exception {
        AgentFlowTraceContext traceContext = startTrace("chat", false, request);
        try {
            JSONObject body = buildRequestBody(request, "blocking");
            String url = joinedUrl(requireBaseUrl(), "v1/chat-messages");
            JSONObject response = executeObject(jsonRequestBuilder(url).post(jsonBody(body)).build());
            AgentFlowChatResponse chatResponse = mapBlockingResponse(response);
            traceComplete(traceContext, chatResponse);
            return chatResponse;
        } catch (Exception ex) {
            traceError(traceContext, ex);
            throw ex;
        }
    }

    @Override
    public void chatStream(AgentFlowChatRequest request, final AgentFlowChatListener listener) throws Exception {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        final AgentFlowTraceContext traceContext = startTrace("chat", true, request);
        JSONObject body = buildRequestBody(request, "streaming");
        String url = joinedUrl(requireBaseUrl(), "v1/chat-messages");
        Request httpRequest = jsonRequestBuilder(url).post(jsonBody(body)).build();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicReference<AgentFlowChatResponse> completion = new AtomicReference<AgentFlowChatResponse>();
        final AtomicReference<String> conversationIdRef = new AtomicReference<String>();
        final AtomicReference<String> messageIdRef = new AtomicReference<String>();
        final AtomicReference<String> taskIdRef = new AtomicReference<String>();
        final AtomicReference<AgentFlowUsage> usageRef = new AtomicReference<AgentFlowUsage>();
        final StringBuilder content = new StringBuilder();
        final AtomicBoolean closed = new AtomicBoolean(false);

        eventSourceFactory.newEventSource(httpRequest, new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                listener.onOpen();
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource,
                                @Nullable String id,
                                @Nullable String type,
                                @NotNull String data) {
                try {
                    JSONObject payload = parseObjectOrNull(data);
                    String eventType = firstNonBlank(type, payload == null ? null : payload.getString("event"));
                    if (isBlank(eventType) || "ping".equals(eventType)) {
                        return;
                    }

                    String conversationId = payload == null ? null : payload.getString("conversation_id");
                    String messageId = payload == null ? null : firstNonBlank(payload.getString("message_id"), payload.getString("id"));
                    String taskId = payload == null ? null : payload.getString("task_id");
                    if (!isBlank(conversationId)) {
                        conversationIdRef.set(conversationId);
                    }
                    if (!isBlank(messageId)) {
                        messageIdRef.set(messageId);
                    }
                    if (!isBlank(taskId)) {
                        taskIdRef.set(taskId);
                    }

                    AgentFlowUsage usage = payload == null ? null : usageFromDify(metadataUsage(payload));
                    if (usage != null) {
                        usageRef.set(usage);
                    }

                    String delta = null;
                    if ("message".equals(eventType) || "agent_message".equals(eventType)) {
                        delta = payload == null ? null : payload.getString("answer");
                        if (!isBlank(delta)) {
                            content.append(delta);
                        }
                    }

                    boolean done = "message_end".equals(eventType);
                    AgentFlowChatEvent event = AgentFlowChatEvent.builder()
                            .type(eventType)
                            .contentDelta(delta)
                            .conversationId(conversationIdRef.get())
                            .messageId(messageIdRef.get())
                            .taskId(taskIdRef.get())
                            .done(done)
                            .usage(usageRef.get())
                            .raw(payload == null ? data : payload)
                            .build();
                    listener.onEvent(event);
                    traceEvent(traceContext, event);

                    if ("error".equals(eventType)) {
                        throw new AgentFlowException("Dify stream error: " + (payload == null ? data : payload.toJSONString()));
                    }
                    if (done) {
                        AgentFlowChatResponse responsePayload = AgentFlowChatResponse.builder()
                                .content(content.toString())
                                .conversationId(conversationIdRef.get())
                                .messageId(messageIdRef.get())
                                .taskId(taskIdRef.get())
                                .usage(usageRef.get())
                                .raw(payload)
                                .build();
                        completion.set(responsePayload);
                        listener.onComplete(responsePayload);
                        traceComplete(traceContext, responsePayload);
                        closed.set(true);
                        eventSource.cancel();
                        latch.countDown();
                    }
                } catch (Throwable ex) {
                    failure.set(ex);
                    traceError(traceContext, ex);
                    listener.onError(ex);
                    closed.set(true);
                    eventSource.cancel();
                    latch.countDown();
                }
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                if (closed.compareAndSet(false, true)) {
                    AgentFlowChatResponse responsePayload = completion.get();
                    if (responsePayload == null) {
                        responsePayload = AgentFlowChatResponse.builder()
                                .content(content.toString())
                                .conversationId(conversationIdRef.get())
                                .messageId(messageIdRef.get())
                                .taskId(taskIdRef.get())
                                .usage(usageRef.get())
                                .build();
                        completion.set(responsePayload);
                        listener.onComplete(responsePayload);
                        traceComplete(traceContext, responsePayload);
                    }
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                Throwable error = t;
                if (error == null && response != null) {
                    error = new AgentFlowException("Dify stream failed: HTTP " + response.code());
                }
                if (error == null) {
                    error = new AgentFlowException("Dify stream failed");
                }
                failure.set(error);
                traceError(traceContext, error);
                listener.onError(error);
                closed.set(true);
                latch.countDown();
            }
        });

        if (!latch.await(pollTimeoutMillis(), TimeUnit.MILLISECONDS)) {
            throw new AgentFlowException("Dify stream timed out");
        }
        if (failure.get() != null) {
            if (failure.get() instanceof Exception) {
                throw (Exception) failure.get();
            }
            throw new AgentFlowException("Dify stream failed", failure.get());
        }
    }

    private JSONObject buildRequestBody(AgentFlowChatRequest request, String responseMode) {
        JSONObject body = new JSONObject();
        body.put("query", request.getPrompt());
        body.put("inputs", request.getInputs() == null ? Collections.emptyMap() : request.getInputs());
        body.put("user", defaultUserId(request.getUserId()));
        body.put("response_mode", responseMode);
        String conversationId = defaultConversationId(request.getConversationId());
        if (!isBlank(conversationId)) {
            body.put("conversation_id", conversationId);
        }
        if (request.getExtraBody() != null && !request.getExtraBody().isEmpty()) {
            body.putAll(request.getExtraBody());
        }
        return body;
    }

    private AgentFlowChatResponse mapBlockingResponse(JSONObject response) {
        return AgentFlowChatResponse.builder()
                .content(firstNonBlank(response.getString("answer"), response.getString("message")))
                .conversationId(response.getString("conversation_id"))
                .messageId(firstNonBlank(response.getString("message_id"), response.getString("id")))
                .taskId(response.getString("task_id"))
                .usage(usageFromDify(metadataUsage(response)))
                .raw(response)
                .build();
    }

    private JSONObject metadataUsage(JSONObject payload) {
        JSONObject metadata = payload == null ? null : payload.getJSONObject("metadata");
        return metadata == null ? null : metadata.getJSONObject("usage");
    }

    private JSONObject parseObjectOrNull(String data) {
        if (isBlank(data)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(data);
            return parsed instanceof JSONObject ? (JSONObject) parsed : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
