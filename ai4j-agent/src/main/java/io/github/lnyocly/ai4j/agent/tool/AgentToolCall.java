package io.github.lnyocly.ai4j.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCall {

    public static final String METADATA_KEY_HARNESS_APPROVAL_GRANTED = "harnessApprovalGranted";

    /** Optional stable identity for a side-effecting tool invocation. */
    public static final String METADATA_KEY_HARNESS_INVOCATION_ID = "harnessInvocationId";

    /** Identifies the outer runtime call that owns a nested tool invocation. */
    public static final String METADATA_KEY_PARENT_CALL_ID = "parentCallId";

    /** Prefix for the portable system marker used by CodeAct pending calls. */
    public static final String CODEACT_PENDING_RESULT_PREFIX = "CODEACT_PENDING_TOOL_RESULT:";

    private String name;

    private String arguments;

    private String callId;

    private String type;

    /** Host/runtime metadata; providers do not need to serialize it. */
    private Map<String, Object> metadata;

    /** Source-compatible constructor retained for the original four-field shape. */
    public AgentToolCall(String name, String arguments, String callId, String type) {
        this(name, arguments, callId, type, null);
    }

    public AgentToolCall withMetadata(String key, Object value) {
        Map<String, Object> values = metadata == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(metadata);
        if (key != null) {
            values.put(key, value);
        }
        this.metadata = values;
        return this;
    }

    public boolean hasMetadataValue(String key, Object expected) {
        if (metadata == null || key == null) {
            return false;
        }
        Object actual = metadata.get(key);
        return expected == null ? actual == null : expected.equals(actual);
    }
}
