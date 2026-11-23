package io.github.lnyocly.ai4j.ambient.channel;

import io.github.lnyocly.ai4j.ambient.memory.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inbound message arriving at {@link ChannelGateway} from a connected channel endpoint.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class InboundChannelMessage {

    private String messageId;
    private String endpointId;
    private String senderId;
    private String sessionId;
    private String scopeKey;
    private String text;
    private TrustLevel resolvedTrustLevel;
    private long receivedAtEpochMs;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
