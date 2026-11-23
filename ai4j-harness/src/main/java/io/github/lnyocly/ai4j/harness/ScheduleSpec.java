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
public class ScheduleSpec {

    private String scheduleId;
    private String scopeKey;
    private String name;
    private ScheduleMode mode;
    private String cronExpression;
    private String description;
    private String targetNodeId;
    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
