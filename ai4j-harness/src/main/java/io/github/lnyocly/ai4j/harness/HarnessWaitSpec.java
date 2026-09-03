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
public class HarnessWaitSpec {

    private String waitId;
    private WaitType type;
    private String operationId;
    private String externalKey;
    private long dueAtEpochMs;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<String, Object>();
}
