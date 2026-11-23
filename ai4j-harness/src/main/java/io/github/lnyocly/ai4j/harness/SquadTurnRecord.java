package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SquadTurnRecord {

    private int turnNumber;
    private String action;
    private String targetMemberId;
    private String summary;
    private String memberResponse;
    private long recordedAtEpochMs;
}
