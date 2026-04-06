package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.agentflow.AgentFlowType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "ai.agentflow")
public class AgentFlowProperties {

    private boolean enabled = false;

    private String defaultName;

    private Map<String, EndpointProperties> profiles = new LinkedHashMap<String, EndpointProperties>();

    @Data
    public static class EndpointProperties {
        private AgentFlowType type;
        private String baseUrl;
        private String webhookUrl;
        private String apiKey;
        private String botId;
        private String workflowId;
        private String appId;
        private String userId;
        private String conversationId;
        private Long pollIntervalMillis = 1000L;
        private Long pollTimeoutMillis = 60000L;
        private Map<String, String> headers = new LinkedHashMap<String, String>();
    }
}
