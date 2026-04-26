package io.github.lnyocly.ai4j.agent.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TraceSpanEvent {

    private long timestamp;
    private String name;
    private Map<String, Object> attributes;
}
