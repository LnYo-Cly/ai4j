package io.github.lnyocly.ai4j.agent.codeact;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodeActOptions {

    @Builder.Default
    private boolean reAct = false;
}
