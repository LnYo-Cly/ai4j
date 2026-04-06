package io.github.lnyocly.ai4j.agentflow.workflow;

import io.github.lnyocly.ai4j.agentflow.AgentFlowConfig;
import io.github.lnyocly.ai4j.agentflow.support.AgentFlowSupport;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceContext;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.Request;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class N8nAgentFlowWorkflowService extends AgentFlowSupport implements AgentFlowWorkflowService {

    public N8nAgentFlowWorkflowService(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        super(configuration, agentFlowConfig);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentFlowWorkflowResponse run(AgentFlowWorkflowRequest request) throws Exception {
        AgentFlowTraceContext traceContext = startTrace("workflow", false, request);
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            if (request.getInputs() != null) {
                payload.putAll(request.getInputs());
            }
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                payload.put("_metadata", request.getMetadata());
            }
            if (request.getExtraBody() != null && !request.getExtraBody().isEmpty()) {
                payload.putAll(request.getExtraBody());
            }

            Request httpRequest = jsonRequestBuilder(requireWebhookUrl()).post(jsonBody(payload)).build();
            String responseBody = execute(httpRequest);
            Object raw = parseJsonOrText(responseBody);
            Map<String, Object> outputs = raw instanceof Map
                    ? new LinkedHashMap<String, Object>((Map<String, Object>) raw)
                    : Collections.<String, Object>emptyMap();

            AgentFlowWorkflowResponse workflowResponse = AgentFlowWorkflowResponse.builder()
                    .status("completed")
                    .outputText(extractText(raw))
                    .outputs(outputs)
                    .raw(raw)
                    .build();
            traceComplete(traceContext, workflowResponse);
            return workflowResponse;
        } catch (Exception ex) {
            traceError(traceContext, ex);
            throw ex;
        }
    }

    @Override
    public void runStream(AgentFlowWorkflowRequest request, AgentFlowWorkflowListener listener) {
        throw new UnsupportedOperationException("n8n workflow streaming is not supported yet");
    }
}
