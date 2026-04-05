package io.github.lnyocly.ai4j.coding.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodingSessionDescriptor {

    private String sessionId;

    private String rootSessionId;

    private String parentSessionId;

    private String provider;

    private String protocol;

    private String model;

    private String workspace;

    private String summary;

    private int memoryItemCount;

    private int processCount;

    private int activeProcessCount;

    private int restoredProcessCount;

    private long createdAtEpochMs;

    private long updatedAtEpochMs;
}
