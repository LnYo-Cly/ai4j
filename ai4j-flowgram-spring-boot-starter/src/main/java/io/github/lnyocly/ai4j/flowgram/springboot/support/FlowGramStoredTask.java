package io.github.lnyocly.ai4j.flowgram.springboot.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramStoredTask {

    private String taskId;
    private String creatorId;
    private String tenantId;
    private Long createdAt;
    private Long expiresAt;
    private String status;
    private Boolean terminated;
    private String error;
    private Map<String, Object> resultSnapshot;
}
