package io.github.lnyocly.ai4j.agent.extension;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailDecision;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExtensionGuardrailToolExecutor implements ToolExecutor {

    public static final String ACTION_TOOL_EXECUTE = "tool.execute";

    private final ToolExecutor delegate;
    private final List<ExtensionGuardrail> guardrails;

    public ExtensionGuardrailToolExecutor(ToolExecutor delegate, List<ExtensionGuardrail> guardrails) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate tool executor must not be null");
        }
        this.delegate = delegate;
        this.guardrails = guardrails == null
                ? Collections.<ExtensionGuardrail>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionGuardrail>(guardrails));
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        evaluate(call);
        return delegate.execute(call);
    }

    private void evaluate(AgentToolCall call) {
        if (guardrails.isEmpty()) {
            return;
        }
        GuardrailRequest request = toRequest(call);
        for (ExtensionGuardrail guardrail : guardrails) {
            if (guardrail == null) {
                continue;
            }
            GuardrailDecision decision = guardrail.evaluate(request);
            if (decision != null && !decision.isAllowed()) {
                throw denied(guardrail, request, decision);
            }
        }
    }

    private GuardrailRequest toRequest(AgentToolCall call) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        String toolName = call == null ? null : call.getName();
        if (toolName != null) {
            attributes.put("toolName", toolName);
        }
        if (call != null && call.getArguments() != null) {
            attributes.put("arguments", call.getArguments());
        }
        if (call != null && call.getCallId() != null) {
            attributes.put("callId", call.getCallId());
        }
        if (call != null && call.getType() != null) {
            attributes.put("type", call.getType());
        }
        return new GuardrailRequest(ACTION_TOOL_EXECUTE, toolName, attributes);
    }

    private ExtensionException denied(ExtensionGuardrail guardrail,
                                      GuardrailRequest request,
                                      GuardrailDecision decision) {
        String guardrailName = guardrail.name() == null ? "unknown" : guardrail.name();
        String target = request.getTarget() == null ? "unknown" : request.getTarget();
        String reason = decision.getReason() == null || decision.getReason().trim().isEmpty()
                ? "no reason provided"
                : decision.getReason().trim();
        return new ExtensionException("Extension guardrail denied tool " + target
                + " by " + guardrailName + ": " + reason);
    }
}
