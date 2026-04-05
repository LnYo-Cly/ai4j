package io.github.lnyocly.ai4j.flowgram.springboot.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramCaller {

    private String callerId;
    private String tenantId;
    private boolean anonymous;
    private Map<String, Object> attributes;
}
