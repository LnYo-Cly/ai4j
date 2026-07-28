package io.github.lnyocly.ai4j.extension.guardrail;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

@Experimental(since = "2.4.3")
public interface GuardrailRegistry {

    void register(ExtensionGuardrail guardrail);
}
