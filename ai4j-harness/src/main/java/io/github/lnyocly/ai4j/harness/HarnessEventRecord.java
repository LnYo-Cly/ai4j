package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessEventRecord {

    private long sequence;
    private String type;
    private String entityId;
    private String actorId;
    private long recordedAtEpochMs;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<String, Object>();

    public HarnessEventRecord copy() {
        return HarnessJson.copy(this, HarnessEventRecord.class);
    }
}
