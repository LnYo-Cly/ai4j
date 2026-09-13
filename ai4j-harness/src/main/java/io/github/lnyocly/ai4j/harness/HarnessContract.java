package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Application-owned governance rules. It describes what the runtime must
 * enforce; it does not describe a fixed list of business Tasks.
 */
public interface HarnessContract {

    default boolean requiresTaskForTool(String toolName) {
        return false;
    }

    default boolean requiresApprovalForTool(String toolName) {
        return false;
    }

    default boolean acceptsAgentFact(HarnessFactSpec fact) {
        return true;
    }

    /**
     * Controls whether a submitted Task needs an approved external review.
     * Runtime execution limits are separate from this completion policy; a
     * coding Harness may deliberately leave review optional while a regulated
     * business workflow can require it.
     */
    default boolean requiresApprovedReview(TaskRecord task,
                                           SubmissionRecord submission) {
        return true;
    }

    default List<HarnessGate> completionGates() {
        return Collections.emptyList();
    }

    default List<GateResult> evaluateCompletion(TaskRecord task,
                                                 SubmissionRecord submission,
                                                 HarnessState state) {
        List<HarnessGate> gates = completionGates();
        if (gates == null || gates.isEmpty()) {
            return Collections.singletonList(GateResult.pass("default"));
        }
        List<GateResult> results = new ArrayList<GateResult>();
        for (HarnessGate gate : gates) {
            if (gate == null) {
                continue;
            }
            GateResult result;
            try {
                result = gate.evaluate(task, submission, state);
            } catch (RuntimeException error) {
                result = GateResult.fail(gate.getName(), "gate threw: " + error.getMessage());
            }
            results.add(result == null
                    ? GateResult.fail(gate.getName(), "gate returned no result")
                    : result);
        }
        return results;
    }

    default boolean mayApprove(HarnessActor actor, SubmissionRecord submission) {
        return actor != null && !actor.isAgent();
    }

    default boolean mayComplete(HarnessActor actor, SubmissionRecord submission) {
        return actor != null && !actor.isAgent();
    }

    /**
     * Allows a non-Agent actor to resolve an execution that became UNKNOWN
     * after its lease expired. Reconciliation is intentionally separate from
     * normal Agent execution because the external side effect may already
     * have happened.
     */
    default boolean mayReconcile(HarnessActor actor,
                                 ExecutionRecord execution,
                                 ExecutionStatus resolution) {
        return actor != null && !actor.isAgent();
    }

    /** External side-effect lookup may reconcile a durable tool invocation. */
    default boolean mayReconcileToolInvocation(HarnessActor actor,
                                                ToolInvocationRecord invocation,
                                                ToolInvocationStatus resolution) {
        return actor != null && !actor.isAgent();
    }

    static Builder builder() {
        return new Builder();
    }

    /** Convenient immutable contract for the common set-based policy. */
    final class Builder {
        private final Set<String> taskRequiredTools = new LinkedHashSet<String>();
        private final Set<String> approvalRequiredTools = new LinkedHashSet<String>();
        private final List<HarnessGate> completionGates = new ArrayList<HarnessGate>();
        private boolean approvedReviewRequired = true;
        private boolean allowSystemApproval = true;
        private boolean allowSystemCompletion = true;
        private boolean allowSystemReconciliation = true;

        public Builder taskRequiredTool(String toolName) {
            if (toolName != null && !toolName.trim().isEmpty()) {
                taskRequiredTools.add(toolName.trim());
            }
            return this;
        }

        public Builder approvalRequiredTool(String toolName) {
            if (toolName != null && !toolName.trim().isEmpty()) {
                approvalRequiredTools.add(toolName.trim());
            }
            return this;
        }

        public Builder completionGate(HarnessGate gate) {
            if (gate != null) {
                completionGates.add(gate);
            }
            return this;
        }

        public Builder requiresApprovedReview(boolean value) {
            approvedReviewRequired = value;
            return this;
        }

        public Builder allowSystemApproval(boolean value) {
            allowSystemApproval = value;
            return this;
        }

        public Builder allowSystemCompletion(boolean value) {
            allowSystemCompletion = value;
            return this;
        }

        public Builder allowSystemReconciliation(boolean value) {
            allowSystemReconciliation = value;
            return this;
        }

        public HarnessContract build() {
            final Set<String> taskTools = new LinkedHashSet<String>(taskRequiredTools);
            final Set<String> approvalTools = new LinkedHashSet<String>(approvalRequiredTools);
            final List<HarnessGate> gates = new ArrayList<HarnessGate>(completionGates);
            final boolean reviewRequired = approvedReviewRequired;
            final boolean systemApproval = allowSystemApproval;
            final boolean systemCompletion = allowSystemCompletion;
            final boolean systemReconciliation = allowSystemReconciliation;
            return new HarnessContract() {
                @Override
                public boolean requiresTaskForTool(String toolName) {
                    return toolName != null && taskTools.contains(toolName);
                }

                @Override
                public boolean requiresApprovalForTool(String toolName) {
                    return toolName != null && approvalTools.contains(toolName);
                }

                @Override
                public List<HarnessGate> completionGates() {
                    return new ArrayList<HarnessGate>(gates);
                }

                @Override
                public boolean requiresApprovedReview(TaskRecord task,
                                                      SubmissionRecord submission) {
                    return reviewRequired;
                }

                @Override
                public boolean mayApprove(HarnessActor actor, SubmissionRecord submission) {
                    return actor != null && (!actor.isAgent())
                            && (systemApproval || !actor.isSystem());
                }

                @Override
                public boolean mayComplete(HarnessActor actor, SubmissionRecord submission) {
                    return actor != null && (!actor.isAgent())
                            && (systemCompletion || !actor.isSystem());
                }

                @Override
                public boolean mayReconcile(HarnessActor actor,
                                            ExecutionRecord execution,
                                            ExecutionStatus resolution) {
                    return actor != null && (!actor.isAgent())
                            && (systemReconciliation || !actor.isSystem());
                }

                @Override
                public boolean mayReconcileToolInvocation(HarnessActor actor,
                                                          ToolInvocationRecord invocation,
                                                          ToolInvocationStatus resolution) {
                    return actor != null && (!actor.isAgent())
                            && (systemReconciliation || !actor.isSystem());
                }
            };
        }
    }
}
