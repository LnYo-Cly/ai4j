package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.web.spring.AiWebStreamOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for {@code ai4j-web} streaming options and SSE codecs.
 */
@Data
@ConfigurationProperties(prefix = "ai.web")
public class AiWebProperties {

    private boolean enabled = true;
    private Stream stream = new Stream();
    private Agui agui = new Agui();
    private OpenAi openai = new OpenAi();

    @Data
    public static class Stream {
        private Long timeoutMs;
        private long heartbeatIntervalMs = 0L;
        private String heartbeatComment = AiWebStreamOptions.DEFAULT_HEARTBEAT_COMMENT;
        private boolean includeErrorEvent = false;
    }

    @Data
    public static class Agui {
        private boolean includeEventName = true;
    }

    @Data
    public static class OpenAi {
        private String id;
        private String model;
        private String systemFingerprint;
    }
}
