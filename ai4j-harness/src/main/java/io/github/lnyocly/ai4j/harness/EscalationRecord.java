package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EscalationRecord {

    private String escalationId;
    private String scopeKey;
    private String taskId;
    private String executionId;
    private String waitId;
    private String targetActorId;
    private HarnessActor requester;
    private String question;
    private String recommend;
    private String reason;
    private EscalationStatus status;
    private String answer;
    private String selectedOption;
    private String rulingRationale;
    private HarnessActor answeredBy;
    private String followUpNotes;
    private HarnessActor followedUpBy;
    private long createdAtEpochMs;
    private long answeredAtEpochMs;
    private long followedUpAtEpochMs;

    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private List<String> options = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public EscalationRecord copy() {
        return HarnessJson.copy(this, EscalationRecord.class);
    }
}
