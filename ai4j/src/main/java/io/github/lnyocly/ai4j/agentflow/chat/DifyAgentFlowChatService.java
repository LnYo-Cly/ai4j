package io.github.lnyocly.ai4j.agentflow.chat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agentflow.AgentFlowConfig;
import io.github.lnyocly.ai4j.agentflow.AgentFlowException;
import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import io.github.lnyocly.ai4j.agentflow.support.AgentFlowSupport;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceContext;
import io.github.lnyocly.ai4j.codec.JsonCodec;
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
            ObjectNode body = buildRequestBody(request, "blocking");
            String url = joinedUrl(requireBaseUrl(), "v1/chat-messages");
            ObjectNode response = executeObject(jsonRequestBuilder(url).post(jsonBody(body)).build());
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
        ObjectNode body = buildRequestBody(request, "streaming");
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
                    ObjectNode payload = parseObjectOrNull(data);
                    String eventType = firstNonBlank(type, textValue(payload, "event"));
                    if (isBlank(eventType) || "ping".equals(eventType)) {
                        return;
                    }

                    String conversationId = textValue(payload, "conversation_id");
                    String messageId = firstNonBlank(textValue(payload, "message_id"), textValue(payload, "id"));
                    String taskId = textValue(payload, "task_id");
                    if (!isBlank(conversationId)) {
                        conversationIdRef.set(conversationId);
                    }
                    if (!isBlank(messageId)) {
                        messageIdRef.set(messageId);
                    }
                    if (!isBlank(taskId)) {
                        taskIdRef.set(taskId);
                    }

                    AgentFlowUsage usage = usageFromDify(metadataUsage(payload));
                    if (usage != null) {
                        usageRef.set(usage);
                    }

                    String delta = null;
                    if ("message".equals(eventType) || "agent_message".equals(eventType)) {
                        delta = textValue(payload, "answer");
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
                        throw new AgentFlowException("Dify stream error: " + (payload == null ? data : payload.toString()));
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

    private ObjectNode buildRequestBody(AgentFlowChatRequest request, String responseMode) {
        ObjectNode body = JsonCodec.createObjectNode();
        body.put("query", request.getPrompt());
        body.set("inputs", JsonCodec.valueToTree(request.getInputs() == null ? Collections.emptyMap() : request.getInputs()));
        body.put("user", defaultUserId(request.getUserId()));
        body.put("response_mode", responseMode);
        String conversationId = defaultConversationId(request.getConversationId());
        if (!isBlank(conversationId)) {
            body.put("conversation_id", conversationId);
        }
        putExtraBody(body, request.getExtraBody());
        return body;
    }

    private AgentFlowChatResponse mapBlockingResponse(ObjectNode response) {
        return AgentFlowChatResponse.builder()
                .content(firstNonBlank(textValue(response, "answer"), textValue(response, "message")))
                .conversationId(textValue(response, "conversation_id"))
                .messageId(firstNonBlank(textValue(response, "message_id"), textValue(response, "id")))
                .taskId(textValue(response, "task_id"))
                .usage(usageFromDify(metadataUsage(response)))
                .raw(response)
                .build();
    }

    private ObjectNode metadataUsage(ObjectNode payload) {
        ObjectNode metadata = objectField(payload, "metadata");
        return objectField(metadata, "usage");
    }
}
