package io.github.lnyocly.ai4j.agentflow.workflow;

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

public class DifyAgentFlowWorkflowService extends AgentFlowSupport implements AgentFlowWorkflowService {

    public DifyAgentFlowWorkflowService(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        super(configuration, agentFlowConfig);
    }

    @Override
    public AgentFlowWorkflowResponse run(AgentFlowWorkflowRequest request) throws Exception {
        AgentFlowTraceContext traceContext = startTrace("workflow", false, request);
        try {
            ObjectNode body = buildRequestBody(request, "blocking");
            String url = joinedUrl(requireBaseUrl(), "v1/workflows/run");
            ObjectNode response = executeObject(jsonRequestBuilder(url).post(jsonBody(body)).build());
            AgentFlowWorkflowResponse workflowResponse = mapWorkflowResponse(response);
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

        ObjectNode body = buildRequestBody(request, "streaming");
        String url = joinedUrl(requireBaseUrl(), "v1/workflows/run");
        Request httpRequest = jsonRequestBuilder(url).post(jsonBody(body)).build();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicReference<AgentFlowWorkflowResponse> completion = new AtomicReference<AgentFlowWorkflowResponse>();
        final AtomicReference<String> taskIdRef = new AtomicReference<String>();
        final AtomicReference<String> workflowRunIdRef = new AtomicReference<String>();
        final AtomicReference<AgentFlowUsage> usageRef = new AtomicReference<AgentFlowUsage>();
        final AtomicReference<Map<String, Object>> outputsRef = new AtomicReference<Map<String, Object>>(Collections.<String, Object>emptyMap());
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

                    if (payload != null) {
                        taskIdRef.set(firstNonBlank(textValue(payload, "task_id"), taskIdRef.get()));
                        workflowRunIdRef.set(firstNonBlank(textValue(payload, "workflow_run_id"), workflowRunIdRef.get()));
                    }

                    String outputText = null;
                    String status = null;
                    Map<String, Object> outputs = outputsRef.get();
                    boolean done = false;

                    if ("workflow_finished".equals(eventType)) {
                        done = true;
                        ObjectNode dataObject = objectField(payload, "data");
                        status = textValue(dataObject, "status");
                        ObjectNode outputObject = objectField(dataObject, "outputs");
                        outputs = toObjectMap(outputObject);
                        outputsRef.set(outputs);
                        outputText = extractText(outputObject);
                        if (!isBlank(outputText)) {
                            content.setLength(0);
                            content.append(outputText);
                        }
                        AgentFlowUsage usage = usageFromDify(objectField(dataObject, "usage"));
                        if (usage != null) {
                            usageRef.set(usage);
                        }
                    } else if ("message".equals(eventType) || "text_chunk".equals(eventType)) {
                        outputText = firstNonBlank(textValue(payload, "answer"), textValue(payload, "text"));
                        if (!isBlank(outputText)) {
                            content.append(outputText);
                        }
                    } else if ("error".equals(eventType)) {
                        throw new AgentFlowException("Dify workflow stream error: " + (payload == null ? data : payload.toString()));
                    }

                    AgentFlowWorkflowEvent event = AgentFlowWorkflowEvent.builder()
                            .type(eventType)
                            .status(status)
                            .outputText(outputText)
                            .outputs(outputs)
                            .taskId(taskIdRef.get())
                            .workflowRunId(workflowRunIdRef.get())
                            .done(done)
                            .usage(usageRef.get())
                            .raw(payload == null ? data : payload)
                            .build();
                    listener.onEvent(event);
                    traceEvent(traceContext, event);

                    if (done) {
                        AgentFlowWorkflowResponse responsePayload = AgentFlowWorkflowResponse.builder()
                                .status(status)
                                .outputText(content.toString())
                                .outputs(outputsRef.get())
                                .taskId(taskIdRef.get())
                                .workflowRunId(workflowRunIdRef.get())
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
                    AgentFlowWorkflowResponse responsePayload = completion.get();
                    if (responsePayload == null) {
                        responsePayload = AgentFlowWorkflowResponse.builder()
                                .outputText(content.toString())
                                .outputs(outputsRef.get())
                                .taskId(taskIdRef.get())
                                .workflowRunId(workflowRunIdRef.get())
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
                    error = new AgentFlowException("Dify workflow stream failed: HTTP " + response.code());
                }
                if (error == null) {
                    error = new AgentFlowException("Dify workflow stream failed");
                }
                failure.set(error);
                traceError(traceContext, error);
                listener.onError(error);
                closed.set(true);
                latch.countDown();
            }
        });

        if (!latch.await(pollTimeoutMillis(), TimeUnit.MILLISECONDS)) {
            throw new AgentFlowException("Dify workflow stream timed out");
        }
        if (failure.get() != null) {
            if (failure.get() instanceof Exception) {
                throw (Exception) failure.get();
            }
            throw new AgentFlowException("Dify workflow stream failed", failure.get());
        }
    }

    private ObjectNode buildRequestBody(AgentFlowWorkflowRequest request, String responseMode) {
        ObjectNode body = JsonCodec.createObjectNode();
        body.set("inputs", JsonCodec.valueToTree(request.getInputs() == null ? Collections.emptyMap() : request.getInputs()));
        body.put("user", defaultUserId(request.getUserId()));
        body.put("response_mode", responseMode);
        putExtraBody(body, request.getExtraBody());
        return body;
    }

    private AgentFlowWorkflowResponse mapWorkflowResponse(ObjectNode response) {
        ObjectNode data = objectField(response, "data");
        ObjectNode outputs = objectField(data, "outputs");
        Map<String, Object> outputMap = toObjectMap(outputs);
        return AgentFlowWorkflowResponse.builder()
                .status(textValue(data, "status"))
                .outputText(extractText(outputs))
                .outputs(outputMap)
                .taskId(textValue(response, "task_id"))
                .workflowRunId(firstNonBlank(textValue(response, "workflow_run_id"), textValue(data, "id")))
                .usage(usageFromDify(objectField(data, "usage")))
                .raw(response)
                .build();
    }
}
