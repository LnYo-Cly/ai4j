package io.github.lnyocly.ai4j.extension.guardrail;

public interface ExtensionGuardrail {

    String name();

    GuardrailDecision evaluate(GuardrailRequest request);
}
