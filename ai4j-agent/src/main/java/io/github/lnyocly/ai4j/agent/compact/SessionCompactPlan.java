package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.context.DefaultContextProjector;

/**
 * Session-level compact configuration for common long-running AgentSession use cases.
 *
 * <p>This class is intentionally a small facade over {@link CompactPolicy} and
 * {@link ContextBudget}. Advanced users can still provide a custom policy, while
 * most users can start with {@link #keepRecentItems(int)}.</p>
 */
public class SessionCompactPlan {

    private CompactPolicy policy;
    private ContextProjector projector;
    private Integer maxItems;
    private Integer maxApproxChars;
    private Integer pinnedPrefixItems;

    public static SessionCompactPlan keepRecentItems(int maxItems) {
        return builder().maxItems(maxItems).build();
    }

    public static SessionCompactPlan keepWithinApproxChars(int maxApproxChars) {
        return builder().maxApproxChars(maxApproxChars).build();
    }

    public static SessionCompactPlan withPolicy(CompactPolicy policy) {
        return builder().policy(policy).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public CompactPolicy toPolicy() {
        if (policy != null) {
            return policy;
        }
        return new StructuredSummaryCompactPolicy(
                projector == null ? new DefaultContextProjector() : projector,
                toBudget()
        );
    }

    public ContextBudget toBudget() {
        return ContextBudget.builder()
                .maxItems(maxItems)
                .maxApproxChars(maxApproxChars)
                .pinnedPrefixItems(pinnedPrefixItems == null ? 0 : pinnedPrefixItems)
                .build();
    }

    public SessionCompactPlan withPinnedPrefixItems(int pinnedPrefixItems) {
        this.pinnedPrefixItems = pinnedPrefixItems;
        return this;
    }

    public SessionCompactPlan withMaxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    public SessionCompactPlan withMaxApproxChars(int maxApproxChars) {
        this.maxApproxChars = maxApproxChars;
        return this;
    }

    public SessionCompactPlan withProjector(ContextProjector projector) {
        this.projector = projector;
        return this;
    }

    public CompactPolicy getPolicy() {
        return policy;
    }

    public ContextProjector getProjector() {
        return projector;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public Integer getMaxApproxChars() {
        return maxApproxChars;
    }

    public Integer getPinnedPrefixItems() {
        return pinnedPrefixItems;
    }

    public static class Builder {
        private final SessionCompactPlan plan = new SessionCompactPlan();

        public Builder policy(CompactPolicy policy) {
            plan.policy = policy;
            return this;
        }

        public Builder projector(ContextProjector projector) {
            plan.projector = projector;
            return this;
        }

        public Builder maxItems(int maxItems) {
            plan.maxItems = maxItems;
            return this;
        }

        public Builder maxApproxChars(int maxApproxChars) {
            plan.maxApproxChars = maxApproxChars;
            return this;
        }

        public Builder pinnedPrefixItems(int pinnedPrefixItems) {
            plan.pinnedPrefixItems = pinnedPrefixItems;
            return this;
        }

        public SessionCompactPlan build() {
            return plan;
        }
    }
}
