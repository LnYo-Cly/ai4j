package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured blocker self-explanation for a Task, Execution, Submission, or Escalation.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BlockerExplanation {

    private String entityRef;
    private String entityType;
    private String entityId;
    private String status;
    private boolean blocked;
    private String summary;
    private long explainedAtEpochMs;
    private BlockerNode root;

    @Builder.Default
    private List<BlockerNode> blockers = new ArrayList<BlockerNode>();
}
