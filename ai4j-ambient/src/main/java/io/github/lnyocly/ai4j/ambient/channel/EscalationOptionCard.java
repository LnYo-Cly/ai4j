package io.github.lnyocly.ai4j.ambient.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured escalation card carrying clickable options for owner ruling across channels.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EscalationOptionCard {

    private String cardId;
    private String waitId;
    private String taskId;
    private String executionId;
    private String scopeKey;
    private String agendaItemId;
    private String title;
    private String question;
    private String reason;
    @Builder.Default
    private List<EscalationOption> options = new ArrayList<EscalationOption>();
    private String recommendedOptionId;
    @Builder.Default
    private List<String> targetEndpointIds = new ArrayList<String>();
    private long createdAtEpochMs;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationOption {
        private String optionId;
        private String label;
        private String description;
        private boolean recommended;
    }
}
