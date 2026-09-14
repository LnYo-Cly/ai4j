package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host-owned, detached input for one acceptance evaluation. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HarnessAcceptanceContext {
    private String taskId;
    private String executionId;
    private String submissionId;
    private String sessionId;
    private String workspace;
    /** Optional opaque host reference to the input/artifact snapshot used by the check. */
    private String contextSnapshotRef;
    @Builder.Default private Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
    @Builder.Default private Map<String, Object> requirements = new LinkedHashMap<String, Object>();

    /** Preserves the constructor signature from before context snapshot metadata. */
    public HarnessAcceptanceContext(String taskId,
                                    String executionId,
                                    String submissionId,
                                    String sessionId,
                                    String workspace,
                                    Map<String, Object> artifacts,
                                    Map<String, Object> requirements) {
        this(taskId, executionId, submissionId, sessionId, workspace, null, artifacts, requirements);
    }
}
