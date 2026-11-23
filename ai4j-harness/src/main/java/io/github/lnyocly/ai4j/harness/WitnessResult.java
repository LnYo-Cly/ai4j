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
public class WitnessResult {

    private int exitCode;
    private boolean passed;
    private String output;
    private String digest;
    @Builder.Default
    private long durationMillis = -1L;
    private String location;
    private String summary;

    @Builder.Default
    private Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
}
