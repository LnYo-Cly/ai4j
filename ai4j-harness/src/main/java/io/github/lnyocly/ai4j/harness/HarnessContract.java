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

    /** Default metadata key a task uses to declare its contract preset. */
    String PRESET_METADATA_KEY = "preset";

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

    /** Governed Task completion requires evidence bound to the current submission by default. */
    default boolean requiresCompletionEvidence(TaskRecord task, SubmissionRecord submission) {
        return true;
    }

    /**
     * Declares the acceptance checks that must pass for a governed
     * submission. An empty set preserves the legacy single-PASS acceptance
     * contract; once checks are declared, missing checks fail closed.
     */
    default Set<String> requiredAcceptanceChecks(TaskRecord task,
                                                  SubmissionRecord submission) {
        return Collections.emptySet();
    }

    default List<HarnessGate> completionGates() {
        return Collections.emptyList();
    }

    /**
     * Task-aware variant of {@link #completionGates()}; presets route through
     * this hook. The default delegates to the instance-level list so existing
     * contracts keep working unchanged.
     */
    default List<HarnessGate> completionGates(TaskRecord task) {
        return completionGates();
    }

    default List<GateResult> evaluateCompletion(TaskRecord task,
                                                 SubmissionRecord submission,
                                                 HarnessState state) {
        List<HarnessGate> gates = completionGates(task);
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

    /**
     * Controls who may promote an ACCEPTED decision into a durable
     * {@link HarnessRule}. Promotion changes what every future execution must
     * satisfy, so the default is intentionally stricter than decision
     * resolution: only a human actor may promote. {@link Builder#allowSystemPromotion}
     * widens this to system actors for explicitly automated governance.
     */
    default boolean mayPromoteLesson(HarnessActor actor) {
        return actor != null && actor.isHuman();
    }

    static Builder builder() {
        return new Builder();
    }

    /** Convenient immutable contract for the common set-based policy. */
    final class Builder {
        private final Set<String> taskRequiredTools = new LinkedHashSet<String>();
        private final Set<String> approvalRequiredTools = new LinkedHashSet<String>();
        private final List<HarnessGate> completionGates = new ArrayList<HarnessGate>();
        private final Set<String> requiredAcceptanceChecks = new LinkedHashSet<String>();
        private boolean approvedReviewRequired = true;
        private boolean completionEvidenceRequired = true;
        private boolean allowSystemApproval = true;
        private boolean allowSystemCompletion = true;
        private boolean allowSystemReconciliation = true;
        private boolean allowSystemPromotion = false;

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

        public Builder requiresCompletionEvidence(boolean value) {
            completionEvidenceRequired = value;
            return this;
        }

        public Builder requiredAcceptanceCheck(String checkId) {
            if (checkId != null && !checkId.trim().isEmpty()) {
                requiredAcceptanceChecks.add(checkId.trim());
            }
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

        /**
         * Widens {@link #mayPromoteLesson} to system actors. Off by default:
         * an unattended pipeline silently turning agent proposals into binding
         * rules is exactly the autonomous-churn failure mode this gate exists
         * to prevent.
         */
        public Builder allowSystemPromotion(boolean value) {
            allowSystemPromotion = value;
            return this;
        }

        public HarnessContract build() {
            final Set<String> taskTools = new LinkedHashSet<String>(taskRequiredTools);
            final Set<String> approvalTools = new LinkedHashSet<String>(approvalRequiredTools);
            final List<HarnessGate> gates = new ArrayList<HarnessGate>(completionGates);
            final Set<String> requiredChecks = new LinkedHashSet<String>(requiredAcceptanceChecks);
            final boolean reviewRequired = approvedReviewRequired;
            final boolean evidenceRequired = completionEvidenceRequired;
            final boolean systemApproval = allowSystemApproval;
            final boolean systemCompletion = allowSystemCompletion;
            final boolean systemReconciliation = allowSystemReconciliation;
            final boolean systemPromotion = allowSystemPromotion;
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
                public Set<String> requiredAcceptanceChecks(TaskRecord task,
                                                             SubmissionRecord submission) {
                    return new LinkedHashSet<String>(requiredChecks);
                }

                @Override
                public boolean requiresApprovedReview(TaskRecord task,
                                                      SubmissionRecord submission) {
                    return reviewRequired;
                }

                @Override
                public boolean requiresCompletionEvidence(TaskRecord task,
                                                           SubmissionRecord submission) {
                    return evidenceRequired;
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

                @Override
                public boolean mayPromoteLesson(HarnessActor actor) {
                    return actor != null && (actor.isHuman()
                            || (systemPromotion && actor.isSystem()));
                }
            };
        }
    }
}
