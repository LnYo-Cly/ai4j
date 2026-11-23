package io.github.lnyocly.ai4j.preset;

import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.service.Platform;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Built-in provider preset registry and {@code "provider:model"} addressing parser.
 */
public final class ProviderPresets {

    public static final ProviderPreset OPENAI = ProviderPreset.builder("openai", Protocol.RESPONSES)
            .baseUrl("https://api.openai.com/")
            .endpointPath("/v1/responses")
            .bearerAuth()
            .apiKeyEnv("OPENAI_API_KEY")
            .baseUrlEnv("OPENAI_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(true)
                    .supportsStreamIncludeUsage(true)
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(true)
                    .maxTokensParameter("max_completion_tokens")
                    .build())
            .build();

    public static final ProviderPreset ANTHROPIC = ProviderPreset.builder("anthropic", Protocol.MESSAGES)
            .baseUrl("https://api.anthropic.com/")
            .endpointPath("/v1/messages")
            .headerAuth("x-api-key")
            .apiKeyEnv("ANTHROPIC_API_KEY")
            .baseUrlEnv("ANTHROPIC_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(false)
                    .supportsPromptCaching(true)
                    .requiresMaxTokens(true)
                    .supportsParallelToolCalls(false)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    public static final ProviderPreset GEMINI = ProviderPreset.builder("gemini", Protocol.GEMINI)
            .baseUrl("https://generativelanguage.googleapis.com/")
            .endpointPath("/v1beta/models/{model}:generateContent")
            .headerAuth("x-goog-api-key")
            .apiKeyEnv("GEMINI_API_KEY", "GOOGLE_API_KEY")
            .baseUrlEnv("GEMINI_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(true)
                    .supportsPromptCaching(true)
                    .requiresThoughtSignatureRoundTrip(true)
                    .supportsParallelToolCalls(false)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("maxOutputTokens")
                    .build())
            .build();

    public static final ProviderPreset DEEPSEEK = ProviderPreset.builder("deepseek", Protocol.CHAT_COMPLETIONS)
            .baseUrl("https://api.deepseek.com/")
            .endpointPath("/chat/completions")
            .bearerAuth()
            .apiKeyEnv("DEEPSEEK_API_KEY")
            .baseUrlEnv("DEEPSEEK_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(true)
                    .reasoningFieldName("reasoning_content")
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    public static final ProviderPreset DASHSCOPE = ProviderPreset.builder("dashscope", Protocol.CHAT_COMPLETIONS)
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")
            .endpointPath("/chat/completions")
            .bearerAuth()
            .apiKeyEnv("DASHSCOPE_API_KEY")
            .baseUrlEnv("DASHSCOPE_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(true)
                    .supportsPromptCaching(true)
                    .reasoningFieldName("reasoning_content")
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    public static final ProviderPreset DOUBAO = ProviderPreset.builder("doubao", Protocol.CHAT_COMPLETIONS)
            .baseUrl("https://ark.cn-beijing.volces.com/api/v3/")
            .endpointPath("/chat/completions")
            .bearerAuth()
            .apiKeyEnv("ARK_API_KEY", "DOUBAO_API_KEY")
            .baseUrlEnv("ARK_BASE_URL", "DOUBAO_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(true)
                    .supportsPromptCaching(true)
                    .reasoningFieldName("reasoning_content")
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    public static final ProviderPreset ZHIPU = ProviderPreset.builder("zhipu", Protocol.CHAT_COMPLETIONS)
            .baseUrl("https://open.bigmodel.cn/api/paas/")
            .endpointPath("/v4/chat/completions")
            .bearerAuth()
            .apiKeyEnv("ZHIPU_API_KEY", "BIGMODEL_API_KEY")
            .baseUrlEnv("ZHIPU_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(false)
                    .reasoningFieldName("reasoning_content")
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    public static final ProviderPreset MINIMAX = ProviderPreset.builder("minimax", Protocol.CHAT_COMPLETIONS)
            .baseUrl("https://api.minimaxi.com/")
            .endpointPath("/v1/chat/completions")
            .bearerAuth()
            .apiKeyEnv("MINIMAX_API_KEY")
            .baseUrlEnv("MINIMAX_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(true)
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    public static final ProviderPreset MOONSHOT = ProviderPreset.builder("moonshot", Protocol.CHAT_COMPLETIONS)
            .baseUrl("https://api.moonshot.cn/")
            .endpointPath("/v1/chat/completions")
            .bearerAuth()
            .apiKeyEnv("MOONSHOT_API_KEY")
            .baseUrlEnv("MOONSHOT_BASE_URL")
            .quirks(ProtocolQuirks.builder()
                    .supportsDeveloperRole(false)
                    .supportsStreamIncludeUsage(true)
                    .reasoningFieldName("reasoning_content")
                    .supportsParallelToolCalls(true)
                    .supportsStrictSchema(false)
                    .maxTokensParameter("max_tokens")
                    .build())
            .build();

    private static final Map<String, ProviderPreset> PRESETS;
    private static final List<ProviderPreset> ENV_DISCOVERY_ORDER;

    static {
        Map<String, ProviderPreset> map = new LinkedHashMap<String, ProviderPreset>();
        for (ProviderPreset preset : Arrays.asList(
                OPENAI, ANTHROPIC, GEMINI, DEEPSEEK, DASHSCOPE, DOUBAO, ZHIPU, MINIMAX, MOONSHOT)) {
            map.put(preset.id(), preset);
        }
        PRESETS = Collections.unmodifiableMap(map);
        ENV_DISCOVERY_ORDER = Collections.unmodifiableList(Arrays.asList(
                OPENAI, ANTHROPIC, GEMINI, DEEPSEEK, DASHSCOPE, DOUBAO, ZHIPU, MINIMAX, MOONSHOT));
    }

    private ProviderPresets() {
    }

    /** Find a built-in preset by id (case-insensitive), or {@code null} if unknown. */
    public static ProviderPreset find(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return PRESETS.get(normalized);
    }

    /** Find a built-in preset by {@link Platform}, or {@code null} if custom/unregistered. */
    public static ProviderPreset find(Platform platform) {
        return platform == null ? null : find(platform.id());
    }

    /** Require a built-in preset by id, throwing {@link IllegalArgumentException} if unknown. */
    public static ProviderPreset require(String id) {
        ProviderPreset preset = find(id);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown provider preset: " + id
                    + " (supported: " + PRESETS.keySet() + ")");
        }
        return preset;
    }

    /** All built-in presets in registration order. */
    public static Collection<ProviderPreset> all() {
        return PRESETS.values();
    }

    /** Return all presets whose environment API key variable is currently configured. */
    public static List<ProviderPreset> availableFromEnv() {
        java.util.List<ProviderPreset> found = new java.util.ArrayList<ProviderPreset>();
        for (ProviderPreset preset : ENV_DISCOVERY_ORDER) {
            if (preset.hasEnvApiKey()) {
                found.add(preset);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * Parse a {@code "provider:model"} or bare {@code "model"} spec.
     *
     * <p>Splits on the first {@code ':'} so model names containing colons (e.g.
     * {@code "ollama:qwen2.5:7b"}) stay intact.</p>
     */
    public static ModelSpec parseModelSpec(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            throw new IllegalArgumentException("Model spec must not be null or blank");
        }
        String trimmed = spec.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            String providerPart = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String modelPart = trimmed.substring(colon + 1).trim();
            if (providerPart.isEmpty() || modelPart.isEmpty()) {
                throw new IllegalArgumentException("Invalid provider:model spec: '" + spec + "'");
            }
            return new ModelSpec(providerPart, Platform.of(providerPart), modelPart, find(providerPart));
        }
        ProviderPreset inferred = inferPresetFromModelName(trimmed);
        if (inferred != null) {
            return new ModelSpec(inferred.id(), inferred.platform(), trimmed, inferred);
        }
        return new ModelSpec(null, null, trimmed, null);
    }

    private static ProviderPreset inferPresetFromModelName(String model) {
        String lower = model.toLowerCase(Locale.ROOT);
        if (lower.startsWith("gpt-") || lower.startsWith("o1") || lower.startsWith("o3")
                || lower.startsWith("o4") || lower.startsWith("chatgpt-")) {
            return OPENAI;
        }
        if (lower.startsWith("claude-")) {
            return ANTHROPIC;
        }
        if (lower.startsWith("gemini-")) {
            return GEMINI;
        }
        if (lower.startsWith("deepseek-")) {
            return DEEPSEEK;
        }
        if (lower.startsWith("qwen")) {
            return DASHSCOPE;
        }
        if (lower.startsWith("doubao-")) {
            return DOUBAO;
        }
        if (lower.startsWith("glm-")) {
            return ZHIPU;
        }
        if (lower.startsWith("minimax") || lower.startsWith("abab")) {
            return MINIMAX;
        }
        if (lower.startsWith("moonshot-") || lower.startsWith("kimi-")) {
            return MOONSHOT;
        }
        return null;
    }

    /** Parsed {@code "provider:model"} specification. */
    public static final class ModelSpec {

        private final String providerId;
        private final Platform platform;
        private final String model;
        private final ProviderPreset preset;

        private ModelSpec(String providerId, Platform platform, String model, ProviderPreset preset) {
            this.providerId = providerId;
            this.platform = platform;
            this.model = Objects.requireNonNull(model, "model is required");
            this.preset = preset;
        }

        public String getProviderId() {
            return providerId;
        }

        public Platform getPlatform() {
            return platform;
        }

        public String getModel() {
            return model;
        }

        public ProviderPreset getPreset() {
            return preset;
        }

        @Override
        public String toString() {
            return providerId == null ? model : (providerId + ":" + model);
        }
    }
}
