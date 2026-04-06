package io.github.lnyocly.ai4j.agentflow.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agentflow.AgentFlowConfig;
import io.github.lnyocly.ai4j.agentflow.AgentFlowException;
import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import io.github.lnyocly.ai4j.agentflow.support.AgentFlowSupport;
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

public class CozeAgentFlowWorkflowService extends AgentFlowSupport implements AgentFlowWorkflowService {

    public CozeAgentFlowWorkflowService(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        super(configuration, agentFlowConfig);
    }

    @Override
    public AgentFlowWorkflowResponse run(AgentFlowWorkflowRequest request) throws Exception {
        JSONObject response = executeObject(buildRunRequest(request, false));
        assertCozeSuccess(response);

        Object dataValue = response.get("data");
        Object parsedData = parseWorkflowData(dataValue);
        Map<String, Object> outputs = parsedData instanceof JSONObject
                ? new LinkedHashMap<String, Object>((JSONObject) parsedData)
                : Collections.<String, Object>emptyMap();

        return AgentFlowWorkflowResponse.builder()
                .status("completed")
                .outputText(extractText(parsedData))
                .outputs(outputs)
                .workflowRunId(response.getString("execute_id"))
                .usage(usageFromCoze(response.getJSONObject("usage")))
                .raw(response)
                .build();
    }

    @Override
    public void runStream(AgentFlowWorkflowRequest request, final AgentFlowWorkflowListener listener) throws Exception {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }

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
                    JSONObject payload = parseObjectOrNull(data);
                    boolean done = false;
                    String outputText = null;

                    if ("Message".equals(eventType)) {
                        outputText = payload == null ? null : payload.getString("content");
                        if (!isBlank(outputText)) {
                            content.append(outputText);
                        }
                        AgentFlowUsage usage = payload == null ? null : usageFromCoze(payload.getJSONObject("usage"));
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

                    listener.onEvent(AgentFlowWorkflowEvent.builder()
                            .type(eventType)
                            .status(done ? "completed" : null)
                            .outputText(outputText)
                            .done(done)
                            .usage(usageRef.get())
                            .raw(payload == null ? data : payload)
                            .build());

                    if (done) {
                        AgentFlowWorkflowResponse responsePayload = AgentFlowWorkflowResponse.builder()
                                .status("completed")
                                .outputText(content.toString())
                                .usage(usageRef.get())
                                .raw(payload == null ? data : payload)
                                .build();
                        completion.set(responsePayload);
                        listener.onComplete(responsePayload);
                        closed.set(true);
                        eventSource.cancel();
                        latch.countDown();
                    }
                } catch (Throwable ex) {
                    failure.set(ex);
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

    private JSONObject buildRequestBody(AgentFlowWorkflowRequest request) {
        JSONObject body = new JSONObject();
        body.put("workflow_id", requireWorkflowId(request.getWorkflowId()));
        body.put("parameters", request.getInputs() == null ? Collections.emptyMap() : request.getInputs());
        if (!isBlank(agentFlowConfig.getBotId())) {
            body.put("bot_id", agentFlowConfig.getBotId());
        }
        if (!isBlank(agentFlowConfig.getAppId())) {
            body.put("app_id", agentFlowConfig.getAppId());
        }
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            body.put("ext", toStringMap(request.getMetadata()));
        }
        if (request.getExtraBody() != null && !request.getExtraBody().isEmpty()) {
            body.putAll(request.getExtraBody());
        }
        return body;
    }

    private Object parseWorkflowData(Object dataValue) {
        if (dataValue == null) {
            return null;
        }
        if (!(dataValue instanceof String)) {
            return dataValue;
        }
        String text = (String) dataValue;
        if (isBlank(text)) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (Exception ex) {
            return text;
        }
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
