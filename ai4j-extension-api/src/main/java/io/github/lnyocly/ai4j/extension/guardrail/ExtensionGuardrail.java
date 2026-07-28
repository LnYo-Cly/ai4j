package io.github.lnyocly.ai4j.extension.guardrail;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

@Experimental(since = "2.4.3")
public interface ExtensionGuardrail {

    String name();

    GuardrailDecision evaluate(GuardrailRequest request);
}
