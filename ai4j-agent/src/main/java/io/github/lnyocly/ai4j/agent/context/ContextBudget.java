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
