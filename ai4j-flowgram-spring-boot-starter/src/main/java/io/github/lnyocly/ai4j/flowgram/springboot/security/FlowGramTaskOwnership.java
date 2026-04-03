package io.github.lnyocly.ai4j.flowgram.springboot.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTaskOwnership {

    private String creatorId;
    private String tenantId;
    private Long createdAt;
    private Long expiresAt;
}
