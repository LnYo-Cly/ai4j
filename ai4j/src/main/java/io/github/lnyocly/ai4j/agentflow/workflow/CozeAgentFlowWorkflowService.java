package io.github.lnyocly.ai4j.agentflow.workflow;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CozeAgentFlowWorkflowService extends AgentFlowSupport implements AgentFlowWorkflowService {

    public CozeAgentFlowWorkflowService(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        super(configuration, agentFlowConfig);
    }

    @Override
    public AgentFlowWorkflowResponse run(AgentFlowWorkflowRequest request) throws Exception {
        AgentFlowTraceContext traceContext = startTrace("workflow", false, request);
        try {
            ObjectNode response = executeObject(buildRunRequest(request, false));
            assertCozeSuccess(response);

            JsonNode dataNode = response.get("data");
            Object parsedData = parseWorkflowData(dataNode);
            Map<String, Object> outputs = parsedData instanceof JsonNode
                    ? toObjectMap((JsonNode) parsedData)
                    : Collections.<String, Object>emptyMap();

            AgentFlowWorkflowResponse workflowResponse = AgentFlowWorkflowResponse.builder()
                    .status("completed")
                    .outputText(extractText(parsedData))
                    .outputs(outputs)
                    .workflowRunId(textValue(response, "execute_id"))
                    .usage(usageFromCoze(objectField(response, "usage")))
                    .raw(response)
                    .build();
            traceComplete(traceContext, workflowResponse);
            return workflowResponse;
        } catch (Exception ex) {
            traceError(traceContext, ex);
            throw ex;
        }
    }

    @Override
    public void runStream(AgentFlowWorkflowRequest request, final AgentFlowWorkflowListener listener) throws Exception {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        final AgentFlowTraceContext traceContext = startTrace("workflow", true, request);

        Request httpRequest = buildRunRequest(request, true);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicReference<AgentFlowWorkflowResponse> completion = new AtomicReference<AgentFlowWorkflowResponse>();
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
                    String eventType = type;
                    ObjectNode payload = parseObjectOrNull(data);
                    boolean done = false;
                    String outputText = null;

                    if ("Message".equals(eventType)) {
                        outputText = textValue(payload, "content");
                        if (!isBlank(outputText)) {
                            content.append(outputText);
                        }
                        AgentFlowUsage usage = usageFromCoze(objectField(payload, "usage"));
                        if (usage != null) {
                            usageRef.set(usage);
                        }
                    } else if ("Interrupt".equals(eventType)) {
                        throw new AgentFlowException("Coze workflow interrupted: " + data);
                    } else if ("Error".equals(eventType)) {
                        throw new AgentFlowException("Coze workflow stream error: " + data);
                    } else if ("Done".equals(eventType)) {
                        done = true;
                    }

                    AgentFlowWorkflowEvent event = AgentFlowWorkflowEvent.builder()
                            .type(eventType)
                            .status(done ? "completed" : null)
                            .outputText(outputText)
                            .done(done)
                            .usage(usageRef.get())
                            .raw(payload == null ? data : payload)
                            .build();
                    listener.onEvent(event);
                    traceEvent(traceContext, event);

                    if (done) {
                        AgentFlowWorkflowResponse responsePayload = AgentFlowWorkflowResponse.builder()
                                .status("completed")
                                .outputText(content.toString())
                                .usage(usageRef.get())
                                .raw(payload == null ? data : payload)
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
                    AgentFlowWorkflowResponse responsePayload = completion.get();
                    if (responsePayload == null) {
                        responsePayload = AgentFlowWorkflowResponse.builder()
                                .status("completed")
                                .outputText(content.toString())
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
                    error = new AgentFlowException("Coze workflow stream failed: HTTP " + response.code());
                }
                if (error == null) {
                    error = new AgentFlowException("Coze workflow stream failed");
                }
                failure.set(error);
                traceError(traceContext, error);
                listener.onError(error);
                closed.set(true);
                latch.countDown();
            }
        });

        if (!latch.await(pollTimeoutMillis(), TimeUnit.MILLISECONDS)) {
            throw new AgentFlowException("Coze workflow stream timed out");
        }
        if (failure.get() != null) {
            if (failure.get() instanceof Exception) {
                throw (Exception) failure.get();
            }
            throw new AgentFlowException("Coze workflow stream failed", failure.get());
        }
    }

    private Request buildRunRequest(AgentFlowWorkflowRequest request, boolean stream) {
        String path = stream ? "v1/workflow/stream_run" : "v1/workflow/run";
        String url = joinedUrl(requireBaseUrl(), path);
        return jsonRequestBuilder(url).post(jsonBody(buildRequestBody(request))).build();
    }

    private ObjectNode buildRequestBody(AgentFlowWorkflowRequest request) {
        ObjectNode body = JsonCodec.createObjectNode();
        body.put("workflow_id", requireWorkflowId(request.getWorkflowId()));
        body.set("parameters", JsonCodec.valueToTree(request.getInputs() == null ? Collections.emptyMap() : request.getInputs()));
        if (!isBlank(agentFlowConfig.getBotId())) {
            body.put("bot_id", agentFlowConfig.getBotId());
        }
        if (!isBlank(agentFlowConfig.getAppId())) {
            body.put("app_id", agentFlowConfig.getAppId());
        }
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            body.set("ext", JsonCodec.valueToTree(toStringMap(request.getMetadata())));
        }
        putExtraBody(body, request.getExtraBody());
        return body;
    }

    private Object parseWorkflowData(JsonNode dataNode) {
        if (dataNode == null || dataNode.isNull()) {
            return null;
        }
        if (!dataNode.isTextual()) {
            return dataNode;
        }
        String text = dataNode.asText();
        if (isBlank(text)) {
            return null;
        }
        try {
            return JsonCodec.readTree(text);
        } catch (Exception ex) {
            return text;
        }
    }
}
