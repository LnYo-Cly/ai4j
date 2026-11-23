package io.github.lnyocly.ai4j.ambient.heartbeat;

import io.github.lnyocly.ai4j.ambient.memory.TrustLevel;
import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One polling signal observed by {@link AgenticHeartbeat}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatTick {

    private String tickId;
    private String scopeKey;
    private String source;
    private String cursorBefore;
    private String cursorAfter;
    private int deltaCount;
    private boolean urgentHint;
    private String summary;
    @Builder.Default
    private TrustLevel trustLevel = TrustLevel.VERIFIED_TOOL;
    private long occurredAtEpochMs;
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<String, Object>();

    /**
     * Returns true when the cursor did not advance, deltaCount is non-positive,
     * no urgent hint is present, and no summary text was produced.
     */
    public boolean isIdleNoOp() {
        return deltaCount <= 0
                && !urgentHint
                && Objects.equals(AmbientSupport.trimToNull(cursorBefore), AmbientSupport.trimToNull(cursorAfter))
                && AmbientSupport.trimToNull(summary) == null
                && (payload == null || payload.isEmpty());
    }
}
