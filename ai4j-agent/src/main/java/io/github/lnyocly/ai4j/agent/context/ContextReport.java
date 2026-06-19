package io.github.lnyocly.ai4j.agent.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextReport {

    private int sourceItemCount;

    private int projectedItemCount;

    private int droppedItemCount;

    private int sourceApproxChars;

    private int projectedApproxChars;

    private boolean itemLimitApplied;

    private boolean characterLimitApplied;

    private List<String> notes;

    public List<String> getNotes() {
        return notes == null ? new ArrayList<String>() : new ArrayList<String>(notes);
    }

    public void setNotes(List<String> notes) {
        this.notes = notes == null ? new ArrayList<String>() : new ArrayList<String>(notes);
    }

    public ContextReport copy() {
        return ContextReport.builder()
                .sourceItemCount(sourceItemCount)
                .projectedItemCount(projectedItemCount)
                .droppedItemCount(droppedItemCount)
                .sourceApproxChars(sourceApproxChars)
                .projectedApproxChars(projectedApproxChars)
                .itemLimitApplied(itemLimitApplied)
                .characterLimitApplied(characterLimitApplied)
                .notes(getNotes())
                .build();
    }
}
