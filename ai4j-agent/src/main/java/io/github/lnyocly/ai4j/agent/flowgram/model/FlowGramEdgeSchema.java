package io.github.lnyocly.ai4j.agent.flowgram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramEdgeSchema {

    private String sourceNodeID;
    private String sourcePort;
    private String sourcePortID;
    private String targetNodeID;
    private String targetPort;
    private String targetPortID;

    public String sourcePortKey() {
        return firstNonBlank(sourcePortID, sourcePort);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
