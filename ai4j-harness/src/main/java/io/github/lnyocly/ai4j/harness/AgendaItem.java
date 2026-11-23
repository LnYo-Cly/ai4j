package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgendaItem {

    private String itemId;
    private AgendaItemKind kind;
    private String scopeKey;
    private String taskId;
    private String executionId;
    private String submissionId;
    private String waitId;
    private String escalationId;
    private String targetActorId;
    private String title;
    private String question;
    private String recommend;
    private String reason;
    private String answer;
    private String selectedOption;
    private String status;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;

    @Builder.Default
    private List<String> options = new ArrayList<String>();
}
