package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessFactSpec {

    private String factId;
    private String scopeKey;
    private String taskId;
    private String statement;
    private String source;
    private String confidence;

    @Builder.Default
    private List<String> evidenceIds = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
