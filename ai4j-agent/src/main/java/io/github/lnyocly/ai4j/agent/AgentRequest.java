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

    private Object input;

    private Map<String, Object> metadata;

    /**
     * Skills explicitly selected by the host for this run. Manual-only Skills are eligible here.
     */
    private List<String> selectedSkills;

    public String getMetadataString(String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
