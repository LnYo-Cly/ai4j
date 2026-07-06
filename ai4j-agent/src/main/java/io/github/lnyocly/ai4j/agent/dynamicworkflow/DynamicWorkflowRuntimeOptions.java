package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DynamicWorkflowRuntimeOptions {

    @Builder.Default
    private Long timeoutMs = Long.valueOf(30000L);

    @Builder.Default
    private Integer maxAgents = Integer.valueOf(32);

    @Builder.Default
    private Boolean normalizeModernSyntax = Boolean.TRUE;

    /**
     * Keep disabled for untrusted model-generated workflows. The default
     * Nashorn runtime creates the engine with Java package access disabled and
     * only exposes the narrow workflow primitives.
     */
    @Builder.Default
    private Boolean allowJavaInterop = Boolean.FALSE;
}
