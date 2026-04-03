package io.github.lnyocly.ai4j.coding.delegate;

import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingDelegateRequest {

    private String definitionName;

    private String input;

    private String context;

    private String childSessionId;

    private Boolean background;

    private CodingSessionMode sessionMode;
}
