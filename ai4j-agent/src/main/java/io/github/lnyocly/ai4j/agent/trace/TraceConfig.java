package io.github.lnyocly.ai4j.agent.trace;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TraceConfig {

    @Builder.Default
    private boolean recordModelInput = true;

    @Builder.Default
    private boolean recordModelOutput = true;

    @Builder.Default
    private boolean recordToolArgs = true;

    @Builder.Default
    private boolean recordToolOutput = true;

    @Builder.Default
    private int maxFieldLength = 0;

    private TraceMasker masker;
}
