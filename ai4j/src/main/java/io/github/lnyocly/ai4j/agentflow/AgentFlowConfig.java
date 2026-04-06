package io.github.lnyocly.ai4j.agentflow;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Collections;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentFlowConfig {

    @NonNull
    private AgentFlowType type;

    private String baseUrl;

    private String webhookUrl;

    private String apiKey;

    private String botId;

    private String workflowId;

    private String appId;

    private String userId;

    private String conversationId;

    @Builder.Default
    private Long pollIntervalMillis = 1_000L;

    @Builder.Default
    private Long pollTimeoutMillis = 60_000L;

    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
