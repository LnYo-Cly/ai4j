package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.CodingSessionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StoredCodingSession {

    private String sessionId;

    private String rootSessionId;

    private String parentSessionId;

    private String provider;

    private String protocol;

    private String model;

    private String workspace;

    private String workspaceDescription;

    private String systemPrompt;

    private String instructions;

    private String summary;

    private int memoryItemCount;

    private int processCount;

    private int activeProcessCount;

    private int restoredProcessCount;

    private long createdAtEpochMs;

    private long updatedAtEpochMs;

    private String storePath;

    private CodingSessionState state;
}
