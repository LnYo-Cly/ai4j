package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime-created task input. It is not a developer-maintained task template. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessTaskSpec {

    private String taskId;
    private String scopeKey;
    private String title;
    private String goal;
    private String plan;
    private String parentTaskId;
    private String idempotencyKey;

    @Builder.Default
    private List<String> tags = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
