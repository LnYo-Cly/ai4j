package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests {@link StructuredSummaryCompactPolicy#shouldCompact} — the auto-trigger gate. */
public class StructuredSummaryCompactPolicyTest {

    @Test
    public void shouldCompactWhenItemsExceedMaxItems() {
        StructuredSummaryCompactPolicy policy = new StructuredSummaryCompactPolicy(
                ContextBudget.maxItems(3));

        assertTrue("should compact when items > maxItems",
                policy.shouldCompact(MemorySnapshot.from(Arrays.asList("a", "b", "c", "d"), null)));
    }

    @Test
    public void shouldNotCompactWhenItemsAtOrBelowMaxItems() {
        StructuredSummaryCompactPolicy policy = new StructuredSummaryCompactPolicy(
                ContextBudget.maxItems(3));

        assertFalse("should not compact when items <= maxItems",
                policy.shouldCompact(MemorySnapshot.from(Arrays.asList("a", "b"), null)));
        assertFalse("should not compact at boundary",
                policy.shouldCompact(MemorySnapshot.from(Arrays.asList("a", "b", "c"), null)));
    }

    @Test
    public void shouldNotCompactOnNullSnapshot() {
        StructuredSummaryCompactPolicy policy = new StructuredSummaryCompactPolicy(
                ContextBudget.maxItems(1));
        assertFalse(policy.shouldCompact(null));
        assertFalse(policy.shouldCompact(MemorySnapshot.from(Collections.emptyList(), null)));
    }
}
