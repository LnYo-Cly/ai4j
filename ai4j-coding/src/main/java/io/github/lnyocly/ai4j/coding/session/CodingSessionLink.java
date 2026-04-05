package io.github.lnyocly.ai4j.coding.session;

import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingSessionLink {

    private String linkId;

    private String taskId;

    private String definitionName;

    private String parentSessionId;

    private String childSessionId;

    private CodingSessionMode sessionMode;

    private boolean background;

    private long createdAtEpochMs;
}
