package io.github.lnyocly.ai4j.agent.systemone;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.workflow.AgentNode;
import io.github.lnyocly.ai4j.agent.workflow.StateCondition;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowContext;
import io.github.lnyocly.ai4j.service.ISystemOneService;
import io.github.lnyocly.ai4j.systemone.entity.NoulCriteria;
import io.github.lnyocly.ai4j.systemone.entity.NoulQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;

import java.util.Collections;

/**
 * Noul-based guardrail backed by a System One (Jev) evaluation.
 *
 * <p>The guardrail asks a yes/no question about the current state — e.g.
 * "Does this content contain PII?" — and treats the state as a violation when
 * the returned {@code noul} probability reaches {@link #violationThreshold}.
 * As a {@link StateCondition}, {@link #matches} returns {@code true} when the
 * state is allowed to proceed. {@link #asNode} adapts the check into a
 * workflow node that returns a fixed violation result.</p>
 *
 * <pre>{@code
 * SystemOneGuardrail pii = new SystemOneGuardrail(systemOneService)
 *         .instructions("Does the content contain personally identifiable information?")
 *         .threshold(0.8);
 *
 * workflow.addNode("piiGate", pii.asNode(blockedResult));
 * }</pre>
 */
public class SystemOneGuardrail implements StateCondition {

    private final ISystemOneService service;

    private String model;
    private String questionKey = "guardrail";
    private Object instructions;
    private NoulCriteria criteria;
    private double violationThreshold = 0.5;

    public SystemOneGuardrail(ISystemOneService service) {
        if (service == null) {
            throw new IllegalArgumentException("service is required");
        }
        this.service = service;
    }

    public SystemOneGuardrail model(String model) {
        this.model = model;
        return this;
    }

    /** Question key in the request/answer map; defaults to {@code "guardrail"}. */
    public SystemOneGuardrail questionKey(String questionKey) {
        this.questionKey = questionKey;
        return this;
    }

    /** The yes/no proposition evaluated against the state (EntryType value). */
    public SystemOneGuardrail instructions(Object instructions) {
        this.instructions = instructions;
        return this;
    }

    public SystemOneGuardrail criteria(NoulCriteria criteria) {
        this.criteria = criteria;
        return this;
    }

    /** Probability at or above which the state counts as a violation (default 0.5). */
    public SystemOneGuardrail threshold(double violationThreshold) {
        this.violationThreshold = violationThreshold;
        return this;
    }

    @Override
    public boolean matches(WorkflowContext context, AgentRequest request, AgentResult result) {
        try {
            return !isViolation(stateOf(context, request));
        } catch (Exception e) {
            throw new IllegalStateException("System One guardrail evaluation failed", e);
        }
    }

    /** Evaluates the noul question against an explicit state and returns the raw answer. */
    public SystemOneAnswer evaluate(Object state) throws Exception {
        NoulQuestion question = NoulQuestion.of(instructions, criteria);
        SystemOneRequest systemOneRequest = SystemOneRequest.of(state, model,
                Collections.singletonMap(questionKey, question));
        SystemOneResponse response = service.evaluate(systemOneRequest);
        if (response == null || response.getAnswers() == null) {
            return null;
        }
        return response.getAnswers().get(questionKey);
    }

    /** True when the noul probability is at or above {@link #violationThreshold}. */
    public boolean isViolation(Object state) throws Exception {
        SystemOneAnswer answer = evaluate(state);
        Double noul = answer == null ? null : answer.getNoul();
        return noul != null && noul >= violationThreshold;
    }

    /**
     * Adapts this guardrail to a workflow node: returns {@code violationResult}
     * when the state violates the policy, otherwise echoes the request input as
     * the node output so downstream nodes can continue.
     */
    public AgentNode asNode(final AgentResult violationResult) {
        return new AgentNode() {
            @Override
            public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
                if (isViolation(stateOf(context, request))) {
                    return violationResult;
                }
                Object input = request == null ? null : request.getInput();
                return AgentResult.builder()
                        .outputText(input == null ? null : String.valueOf(input))
                        .build();
            }
        };
    }

    private static Object stateOf(WorkflowContext context, AgentRequest request) {
        if (context != null && context.getState() != null && !context.getState().isEmpty()) {
            return context.getState();
        }
        return request == null ? null : request.getInput();
    }
}
