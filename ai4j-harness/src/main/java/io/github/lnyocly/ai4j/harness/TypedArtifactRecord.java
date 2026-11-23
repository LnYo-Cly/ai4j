package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TypedArtifactRecord {

    private String artifactId;
    private String scopeKey;
    private String taskId;
    private String executionId;
    private String kindId;
    private int schemaVersion;
    private String payloadClass;
    private String payloadJson;
    private String digest;
    private HarnessActor createdBy;
    private long createdAtEpochMs;

    public <T> T decode(Class<T> type) {
        return payloadJson == null ? null : HarnessJson.fromJson(payloadJson, type);
    }

    public TypedArtifactRecord copy() {
        return HarnessJson.copy(this, TypedArtifactRecord.class);
    }
}
