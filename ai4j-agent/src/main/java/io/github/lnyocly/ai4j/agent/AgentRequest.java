package io.github.lnyocly.ai4j.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    public static final String METADATA_KEY_RUN_ID = "runId";
    public static final String METADATA_KEY_TURN_ID = "turnId";
    public static final String METADATA_KEY_SESSION_ID = "sessionId";
    public static final String METADATA_KEY_EVENT_ID = "eventId";
    /** Stable Harness scope selected by the host for one execution. */
    public static final String METADATA_KEY_HARNESS_SCOPE = "harnessScope";
    /** Durable Harness execution identity injected into an Agent request. */
    public static final String METADATA_KEY_HARNESS_EXECUTION_ID = "harnessExecutionId";
    /** Durable Harness Task identity injected when the execution is Task-bound. */
    public static final String METADATA_KEY_HARNESS_TASK_ID = "harnessTaskId";

    private Object input;

    private Map<String, Object> metadata;

    /**
     * Skills explicitly selected by the host for this run. Manual-only Skills are eligible here.
     */
    private List<String> selectedSkills;

    /**
     * Source-compatible constructor retained for callers compiled before selectedSkills existed.
     */
    public AgentRequest(Object input, Map<String, Object> metadata) {
        this(input, metadata, null);
    }

    public String getMetadataString(String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
