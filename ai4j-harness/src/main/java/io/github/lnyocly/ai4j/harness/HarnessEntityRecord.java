package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Materialized entity projection row stored in {@code ai4j_harness_entities}
 * and {@code entities.json}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessEntityRecord {

    private String harnessId;
    private String entityType;
    private String entityId;
    private long entityVersion;
    private String status;
    private long updatedAtEpochMs;
    private String payloadJson;

    public <T> T decode(Class<T> type) {
        return payloadJson == null ? null : HarnessJson.fromJson(payloadJson, type);
    }

    public HarnessEntityRecord copy() {
        return new HarnessEntityRecord(harnessId, entityType, entityId, entityVersion,
                status, updatedAtEpochMs, payloadJson);
    }
}
