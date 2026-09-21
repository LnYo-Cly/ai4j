package io.github.lnyocly.ai4j.agent.systemone;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.workflow.StateRouter;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowContext;
import io.github.lnyocly.ai4j.service.ISystemOneService;
import io.github.lnyocly.ai4j.systemone.entity.ChoiceQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Confidence-gated state router backed by a System One (Jev) Choice question.
 *
 * <p>The router asks the model to pick one label from the registered route set
 * against the current workflow state. When the answer is missing, has no
 * choice, or its confidence falls below {@link #minConfidence}, the
 * {@link #fallbackRoute} label is returned instead. Returned values are route
 * labels — map them to node ids via
 * {@code StateGraphWorkflow.addConditionalEdges(from, router, routeMap)}.</p>
 *
 * <pre>{@code
 * SystemOneRouter router = new SystemOneRouter(systemOneService)
 *         .instructions("Which branch should handle this request?")
 *         .route("billing", "Invoices, payments, subscription issues")
 *         .route("support", "Product usage questions and bugs")
 *         .minConfidence(0.7)
 *         .fallbackRoute("generic");
 *
 * workflow.addConditionalEdges("decide", router,
 *         Map.of("billing", "billingNode", "support", "supportNode", "generic", "genericNode"));
 * }</pre>
 */
public class SystemOneRouter implements StateRouter {

    private final ISystemOneService service;
    private final LinkedHashMap<String, Object> criteria = new LinkedHashMap<String, Object>();

    private String model;
    private String questionKey = "route";
    private Object instructions;
    private double minConfidence;
    private String fallbackRoute;

    public SystemOneRouter(ISystemOneService service) {
        if (service == null) {
            throw new IllegalArgumentException("service is required");
        }
        this.service = service;
    }

    /** Registers a route label with an optional description shown to the model. */
    public SystemOneRouter route(String label, Object description) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("route label is required");
        }
        criteria.put(label, description == null ? label : description);
        return this;
    }

    public SystemOneRouter route(String label) {
        return route(label, null);
    }

    public SystemOneRouter model(String model) {
        this.model = model;
        return this;
    }

    /** Question key in the request/answer map; defaults to {@code "route"}. */
    public SystemOneRouter questionKey(String questionKey) {
        this.questionKey = questionKey;
        return this;
    }

    public SystemOneRouter instructions(Object instructions) {
        this.instructions = instructions;
        return this;
    }

    /** Minimum answer confidence required to take the winning route (0 disables gating). */
    public SystemOneRouter minConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
        return this;
    }

    /** Route label returned when the answer is missing or below {@link #minConfidence}. */
    public SystemOneRouter fallbackRoute(String fallbackRoute) {
        this.fallbackRoute = fallbackRoute;
        return this;
    }

    @Override
    public String route(WorkflowContext context, AgentRequest request, AgentResult result) {
        try {
            SystemOneAnswer answer = decide(stateOf(context, request));
            if (answer == null || answer.getChoice() == null) {
                return fallbackRoute;
            }
            Double confidence = answer.getConfidence();
            if (minConfidence > 0 && confidence != null && confidence < minConfidence) {
                return fallbackRoute;
            }
            return answer.getChoice();
        } catch (Exception e) {
            throw new IllegalStateException("System One route evaluation failed", e);
        }
    }

    /** Evaluates the choice question against an explicit state and returns the raw answer. */
    public SystemOneAnswer decide(Object state) throws Exception {
        if (criteria.isEmpty()) {
            throw new IllegalStateException("SystemOneRouter requires at least one route");
        }
        ChoiceQuestion question = ChoiceQuestion.of(instructions, criteria);
        SystemOneRequest systemOneRequest = SystemOneRequest.of(state, model,
                Collections.singletonMap(questionKey, question));
        SystemOneResponse response = service.evaluate(systemOneRequest);
        if (response == null || response.getAnswers() == null) {
            return null;
        }
        return response.getAnswers().get(questionKey);
    }

    private static Object stateOf(WorkflowContext context, AgentRequest request) {
        if (context != null && context.getState() != null && !context.getState().isEmpty()) {
            return context.getState();
        }
        return request == null ? null : request.getInput();
    }
}
