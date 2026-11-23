package io.github.lnyocly.ai4j.dx;

import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.preset.ProviderPreset;

/**
 * High-level unified DX entry point bridging single-turn {@link ModelClient}
 * calls, multi-round {@link AiTool} execution, Pi-style {@link io.github.lnyocly.ai4j.stream.AiStream},
 * structured outputs, and declarative {@link AiService} proxies.
 */
public interface AiClient {

    /** Start building a prompt using the client's default model/provider. */
    AiPromptSpec prompt();

    /** Start building a prompt targeting {@code modelSpec} (e.g. {@code "anthropic:claude-sonnet-4-5"}). */
    AiPromptSpec prompt(String modelSpec);

    /** Create a JDK 8 dynamic proxy for a declarative {@link AiService} interface. */
    <T> T service(Class<T> serviceInterface);

    /** Create a declarative {@link AiService} proxy bound to the given instance-scoped {@link AiTool} beans. */
    <T> T service(Class<T> serviceInterface, Object... toolBeans);

    static DefaultAiClient.Builder builder() {
        return DefaultAiClient.builder();
    }

    static AiClient fromEnv() {
        return DefaultAiClient.fromEnv();
    }

    static AiClient of(ModelClient client) {
        return DefaultAiClient.builder().defaultClient(client).build();
    }

    static AiClient of(ProviderPreset preset, String apiKey) {
        return DefaultAiClient.builder().preset(preset, apiKey).build();
    }
}
