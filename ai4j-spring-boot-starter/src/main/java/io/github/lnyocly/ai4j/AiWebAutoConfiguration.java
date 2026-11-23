package io.github.lnyocly.ai4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.web.codec.AguiSseCodec;
import io.github.lnyocly.ai4j.web.codec.OpenAiSseCodec;
import io.github.lnyocly.ai4j.web.spring.AiWebStreamOptions;
import io.github.lnyocly.ai4j.web.spring.AiWebStreams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 2/3/4-compatible auto-configuration for optional {@code ai4j-web} SSE streaming utilities and codecs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AiWebStreams.class)
@ConditionalOnProperty(prefix = "ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiWebProperties.class)
public class AiWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiWebStreamOptions aiWebStreamOptions(AiWebProperties properties) {
        AiWebProperties.Stream stream = properties == null ? null : properties.getStream();
        if (stream == null) {
            return AiWebStreamOptions.defaults();
        }
        String comment = stream.getHeartbeatComment();
        return AiWebStreamOptions.builder()
                .timeoutMs(stream.getTimeoutMs())
                .heartbeatIntervalMs(stream.getHeartbeatIntervalMs())
                .heartbeatComment(comment == null || comment.trim().isEmpty()
                        ? AiWebStreamOptions.DEFAULT_HEARTBEAT_COMMENT
                        : comment)
                .includeErrorEvent(stream.isIncludeErrorEvent())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AguiSseCodec aguiSseCodec(AiWebProperties properties,
                                     ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider == null ? null : objectMapperProvider.getIfAvailable();
        AiWebProperties.Agui agui = properties == null ? null : properties.getAgui();
        boolean includeEventName = agui == null || agui.isIncludeEventName();
        return new AguiSseCodec(objectMapper, includeEventName);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAiSseCodec openAiSseCodec(AiWebProperties properties,
                                         ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider == null ? null : objectMapperProvider.getIfAvailable();
        AiWebProperties.OpenAi openai = properties == null ? null : properties.getOpenai();
        if (openai == null) {
            return new OpenAiSseCodec(objectMapper, null, null, 0L, null);
        }
        return new OpenAiSseCodec(
                objectMapper,
                trimToNull(openai.getId()),
                trimToNull(openai.getModel()),
                0L,
                trimToNull(openai.getSystemFingerprint())
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
