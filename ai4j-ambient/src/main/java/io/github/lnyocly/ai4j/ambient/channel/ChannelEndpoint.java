package io.github.lnyocly.ai4j.ambient.channel;

import io.github.lnyocly.ai4j.ambient.memory.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Descriptor and transport binding for one channel endpoint registered with {@link ChannelGateway}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChannelEndpoint {

    private String endpointId;
    @Builder.Default
    private ChannelType channelType = ChannelType.CUSTOM;
    private String displayName;
    @Builder.Default
    private TrustLevel defaultTrustLevel = TrustLevel.EXTERNAL_UNTRUSTED;
    @Builder.Default
    private Set<String> trustedOwnerIds = new LinkedHashSet<String>();
    @Builder.Default
    private boolean enabled = true;
    private ChannelTransport transport;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public interface ChannelTransport {
        void sendMessage(OutboundChannelMessage message);

        default void sendEscalationCard(EscalationOptionCard card) {
            // Optional override for structured interactive cards.
        }
    }
}
