package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjection;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.context.DefaultContextProjector;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;

import java.util.ArrayList;

public class StructuredSummaryCompactPolicy implements CompactPolicy {

    private final ContextProjector projector;
    private final ContextBudget budget;

    public StructuredSummaryCompactPolicy(ContextBudget budget) {
        this(new DefaultContextProjector(), budget);
    }

    public StructuredSummaryCompactPolicy(ContextProjector projector, ContextBudget budget) {
        this.projector = projector == null ? new DefaultContextProjector() : projector;
        this.budget = budget;
    }

    @Override
    public boolean shouldCompact(MemorySnapshot snapshot) {
        if (snapshot == null || snapshot.getItems() == null) {
            return false;
        }
        int maxItems = budget == null || budget.getMaxItems() == null ? Integer.MAX_VALUE : budget.getMaxItems();
        return snapshot.getItems().size() > maxItems;
    }

    @Override
    public CompactResult compact(MemorySnapshot snapshot) {
        MemorySnapshot source = snapshot == null ? MemorySnapshot.from(new ArrayList<Object>(), null) : MemorySnapshot.from(snapshot.getItems(), snapshot.getSummary());
        ContextProjection projection = projector.project(source.getItems(), budget);
        String summary = buildSummary(source, projection);
        MemorySnapshot compacted = MemorySnapshot.from(projection.getItems(), summary);
        return CompactResult.builder()
                .memory(compacted)
                .summary(summary)
                .contextReport(projection.getReport())
                .build();
    }

    private String buildSummary(MemorySnapshot source, ContextProjection projection) {
        String existing = source.getSummary();
        StringBuilder builder = new StringBuilder();
        builder.append("AI4J_COMPACT_SUMMARY\n");
        if (existing != null && !existing.trim().isEmpty()) {
            builder.append("Existing summary:\n").append(existing.trim()).append("\n");
        }
        if (projection != null && projection.getReport() != null) {
            builder.append("Context projection: ")
                    .append(projection.getReport().getProjectedItemCount())
                    .append("/")
                    .append(projection.getReport().getSourceItemCount())
                    .append(" items retained");
            if (projection.getReport().getDroppedItemCount() > 0) {
                builder.append(", ").append(projection.getReport().getDroppedItemCount()).append(" dropped");
            }
            builder.append(".\n");
        }
        builder.append("Preserved recent items should be treated as the authoritative working context.");
        return builder.toString();
    }
}
