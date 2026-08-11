package io.github.lnyocly.ai4j.agent.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextBudget {

    private Integer maxItems;

    private Integer maxApproxChars;

    @Builder.Default
    private Integer pinnedPrefixItems = 0;

    /**
     * Tier-1 microcompact: maximum recent {@code function_call_output} items whose full content
     * is kept in the projected prompt. Older tool results are replaced with a placeholder so the
     * model sees the structure without the bulk. {@code null} (default) disables microcompact.
     */
    private Integer maxRecentToolResults;

    /**
     * Tier-2: when true, reasoning/thinking content from earlier turns is trimmed in the
     * projected prompt. Default {@code null} treated as false (no trimming).
     */
    private Boolean trimOldReasoning;

    public static ContextBudget maxItems(int maxItems) {
        return ContextBudget.builder().maxItems(maxItems).build();
    }

    public static ContextBudget maxApproxChars(int maxApproxChars) {
        return ContextBudget.builder().maxApproxChars(maxApproxChars).build();
    }

    public int resolvedPinnedPrefixItems() {
        return pinnedPrefixItems == null || pinnedPrefixItems < 0 ? 0 : pinnedPrefixItems;
    }

    public boolean hasLimits() {
        return (maxItems != null && maxItems >= 0) || (maxApproxChars != null && maxApproxChars >= 0);
    }
}
