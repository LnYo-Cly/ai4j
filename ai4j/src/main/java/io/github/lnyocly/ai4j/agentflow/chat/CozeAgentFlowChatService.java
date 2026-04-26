package io.github.lnyocly.ai4j.agentflow.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CozeAgentFlowChatService extends AgentFlowSupport implements AgentFlowChatService {

    public CozeAgentFlowChatService(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        super(configuration, agentFlowConfig);
    }

    @Override
    public AgentFlowChatResponse chat(AgentFlowChatRequest request) throws Exception {
        AgentFlowTraceContext traceContext = startTrace("chat", false, request);
        try {
            JSONObject createResponse = executeObject(buildCreateRequest(request, false));
            assertCozeSuccess(createResponse);

            JSONObject createData = createResponse.getJSONObject("data");
            String chatId = createData == null ? null : createData.getString("id");
            String conversationId = firstNonBlank(
                    createData == null ? null : createData.getString("conversation_id"),
                    defaultConversationId(request.getConversationId())
            );
            if (isBlank(chatId)) {
                throw new AgentFlowException("Coze chat id is missing");
            }

            JSONObject chatData = pollChat(conversationId, chatId);
            String status = chatData.getString("status");
            if (!"completed".equals(status)) {
                throw new AgentFlowException("Coze chat finished with status: " + status);
            }

            JSONObject messageResponse = executeObject(buildMessageListRequest(conversationId, chatId));
            assertCozeSuccess(messageResponse);

            JSONArray messages = messageResponse.getJSONArray("data");
            StringBuilder content = new StringBuilder();
            String messageId = null;
            if (messages != null) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    JSONObject message = messages.getJSONObject(i);
                    if (message == null) {
                        continue;
                    }
                    if (!"assistant".equals(message.getString("role"))) {
                        continue;
                    }
                    String messageContent = message.getString("content");
                    if (!isBlank(messageContent)) {
                        if (content.length() > 0) {
                            content.insert(0, "\n");
                        }
                        content.insert(0, messageContent);
                        if (messageId == null) {
                            messageId = message.getString("id");
                        }
                    }
                }
            }

            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            raw.put("chat", chatData);
            raw.put("messages", messages);

            AgentFlowChatResponse chatResponse = AgentFlowChatResponse.builder()
                    .content(content.toString())
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .taskId(chatId)
                    .usage(usageFromCoze(chatData.getJSONObject("usage")))
                    .raw(raw)
                    .build();
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
        Request httpRequest = buildCreateRequest(request, true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicReference<String> chatIdRef = new AtomicReference<String>();
        final AtomicReference<String> conversationIdRef = new AtomicReference<String>();
        final AtomicReference<String> messageIdRef = new AtomicReference<String>();
        final AtomicReference<AgentFlowUsage> usageRef = new AtomicReference<AgentFlowUsage>();
        final AtomicReference<AgentFlowChatResponse> completionRef = new AtomicReference<AgentFlowChatResponse>();
        final StringBuilder content = new StringBuilder();
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicBoolean sawDelta = new AtomicBoolean(false);

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
                    String eventType = type;
                    JSONObject payload = parseObjectOrNull(data);

                    if ("done".equals(eventType)) {
                        AgentFlowChatEvent event = AgentFlowChatEvent.builder()
                                .type(eventType)
                                .conversationId(conversationIdRef.get())
                                .messageId(messageIdRef.get())
                                .taskId(chatIdRef.get())
                                .done(true)
                                .usage(usageRef.get())
                                .raw(data)
                                .build();
                        listener.onEvent(event);
                        traceEvent(traceContext, event);

                        AgentFlowChatResponse responsePayload = AgentFlowChatResponse.builder()
                                .content(content.toString())
                                .conversationId(conversationIdRef.get())
                                .messageId(messageIdRef.get())
                                .taskId(chatIdRef.get())
                                .usage(usageRef.get())
                                .raw(data)
                                .build();
                        completionRef.set(responsePayload);
                        listener.onComplete(responsePayload);
                        traceComplete(traceContext, responsePayload);
                        closed.set(true);
                        eventSource.cancel();
                        latch.countDown();
                        return;
                    }

                    if ("error".equals(eventType)) {
                        throw new AgentFlowException("Coze stream error: " + data);
                    }

                    String delta = null;
                    if (eventType != null && eventType.startsWith("conversation.chat.")) {
                        conversationIdRef.set(firstNonBlank(
                                payload == null ? null : payload.getString("conversation_id"),
                                conversationIdRef.get()
                        ));
                        chatIdRef.set(firstNonBlank(
                                payload == null ? null : payload.getString("id"),
                                chatIdRef.get()
                        ));
                        AgentFlowUsage usage = payload == null ? null : usageFromCoze(payload.getJSONObject("usage"));
                        if (usage != null) {
                            usageRef.set(usage);
                        }
                        if ("conversation.chat.failed".equals(eventType) || "conversation.chat.requires_action".equals(eventType)) {
                            throw new AgentFlowException("Coze chat status event: " + eventType);
                        }
                    } else if (eventType != null && eventType.startsWith("conversation.message.")) {
                        conversationIdRef.set(firstNonBlank(
                                payload == null ? null : payload.getString("conversation_id"),
                                conversationIdRef.get()
                        ));
                        chatIdRef.set(firstNonBlank(
                                payload == null ? null : payload.getString("chat_id"),
                                chatIdRef.get()
                        ));
                        messageIdRef.set(firstNonBlank(
                                payload == null ? null : payload.getString("id"),
                                messageIdRef.get()
                        ));
                        String contentValue = payload == null ? null : payload.getString("content");
                        if ("conversation.message.delta".equals(eventType)) {
                            delta = contentValue;
                            if (!isBlank(delta)) {
                                content.append(delta);
                                sawDelta.set(true);
                            }
                        } else if ("conversation.message.completed".equals(eventType)) {
                            if (!sawDelta.get() && !isBlank(contentValue)) {
                                content.append(contentValue);
                            }
                        }
                    }

                    AgentFlowChatEvent event = AgentFlowChatEvent.builder()
                            .type(eventType)
                            .contentDelta(delta)
                            .conversationId(conversationIdRef.get())
                            .messageId(messageIdRef.get())
                            .taskId(chatIdRef.get())
                            .usage(usageRef.get())
                            .raw(payload == null ? data : payload)
                            .build();
                    listener.onEvent(event);
                    traceEvent(traceContext, event);
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
                    AgentFlowChatResponse responsePayload = completionRef.get();
                    if (responsePayload == null) {
                        responsePayload = AgentFlowChatResponse.builder()
                                .content(content.toString())
                                .conversationId(conversationIdRef.get())
                                .messageId(messageIdRef.get())
                                .taskId(chatIdRef.get())
                                .usage(usageRef.get())
                                .build();
                        completionRef.set(responsePayload);
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
                    error = new AgentFlowException("Coze stream failed: HTTP " + response.code());
                }
                if (error == null) {
                    error = new AgentFlowException("Coze stream failed");
                }
                failure.set(error);
                traceError(traceContext, error);
                listener.onError(error);
                closed.set(true);
                latch.countDown();
            }
        });

        if (!latch.await(pollTimeoutMillis(), TimeUnit.MILLISECONDS)) {
            throw new AgentFlowException("Coze stream timed out");
        }
        if (failure.get() != null) {
            if (failure.get() instanceof Exception) {
                throw (Exception) failure.get();
            }
            throw new AgentFlowException("Coze stream failed", failure.get());
        }
    }

    private JSONObject pollChat(String conversationId, String chatId) throws Exception {
        long deadline = System.currentTimeMillis() + pollTimeoutMillis();
        while (true) {
            String url = appendQuery(
                    joinedUrl(requireBaseUrl(), "v3/chat/retrieve"),
                    query(conversationId, chatId)
            );
            JSONObject retrieveResponse = executeObject(jsonRequestBuilder(url).get().build());
            assertCozeSuccess(retrieveResponse);
            JSONObject chatData = retrieveResponse.getJSONObject("data");
            String status = chatData == null ? null : chatData.getString("status");
            if ("completed".equals(status) || "failed".equals(status) || "canceled".equals(status) || "requires_action".equals(status)) {
                return chatData == null ? new JSONObject() : chatData;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AgentFlowException("Coze chat poll timed out");
            }
            sleep(pollIntervalMillis());
        }
    }

    private Request buildCreateRequest(AgentFlowChatRequest request, boolean stream) {
        String conversationId = defaultConversationId(request.getConversationId());
        String url = appendQuery(
                joinedUrl(requireBaseUrl(), "v3/chat"),
                queryConversation(conversationId)
        );
        return jsonRequestBuilder(url).post(jsonBody(buildCreateBody(request, stream))).build();
    }

    private JSONObject buildCreateBody(AgentFlowChatRequest request, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("bot_id", requireBotId());
        body.put("user_id", defaultUserId(request.getUserId()));
        body.put("stream", stream);
        body.put("additional_messages", Collections.singletonList(userMessage(request.getPrompt())));
        if (request.getInputs() != null && !request.getInputs().isEmpty()) {
            body.put("parameters", request.getInputs());
        }
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            body.put("meta_data", toStringMap(request.getMetadata()));
        }
        if (!stream && !body.containsKey("auto_save_history")) {
            body.put("auto_save_history", true);
        }
        if (request.getExtraBody() != null && !request.getExtraBody().isEmpty()) {
            body.putAll(request.getExtraBody());
        }
        return body;
    }

    private Request buildMessageListRequest(String conversationId, String chatId) {
        String url = appendQuery(
                joinedUrl(requireBaseUrl(), "v1/conversation/message/list"),
                queryConversation(conversationId)
        );
        JSONObject body = new JSONObject();
        body.put("conversation_id", conversationId);
        body.put("chat_id", chatId);
        body.put("order", "asc");
        body.put("limit", 50);
        if (!isBlank(agentFlowConfig.getBotId())) {
            body.put("bot_id", agentFlowConfig.getBotId());
        }
        return jsonRequestBuilder(url).post(jsonBody(body)).build();
    }

    private JSONObject userMessage(String prompt) {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("type", "question");
        message.put("content", prompt);
        message.put("content_type", "text");
        return message;
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

    private Map<String, String> queryConversation(String conversationId) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (!isBlank(conversationId)) {
            values.put("conversation_id", conversationId);
        }
        return values;
    }

    private Map<String, String> query(String conversationId, String chatId) {
        Map<String, String> values = queryConversation(conversationId);
        if (!isBlank(chatId)) {
            values.put("chat_id", chatId);
        }
        return values;
    }
}
