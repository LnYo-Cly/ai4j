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
    @Builder.Default private Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
    @Builder.Default private Map<String, Object> requirements = new LinkedHashMap<String, Object>();
}
