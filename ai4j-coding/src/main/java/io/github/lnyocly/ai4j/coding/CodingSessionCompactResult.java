package io.github.lnyocly.ai4j.coding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodingSessionCompactResult {

    private String sessionId;

    private int beforeItemCount;

    private int afterItemCount;

    private String summary;

    private boolean automatic;

    private boolean splitTurn;

    private int estimatedTokensBefore;

    private int estimatedTokensAfter;

    private String strategy;

    private int compactedToolResultCount;

    private int deltaItemCount;

    private boolean checkpointReused;

    private boolean fallbackSummary;

    private CodingSessionCheckpoint checkpoint;
}
