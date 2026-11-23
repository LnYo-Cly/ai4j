package io.github.lnyocly.ai4j.ambient.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbound message routed by {@link ChannelGateway} to one or more endpoints.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OutboundChannelMessage {

    private String messageId;
    private String targetEndpointId;
    private ChannelType targetChannelType;
    private String recipientId;
    private String sessionId;
    private String scopeKey;
    private String text;
    private long sentAtEpochMs;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
