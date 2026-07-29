package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseUsage;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseUsageDetails;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Provider-neutral token buckets. Input keeps the provider-reported value;
 * uncachedInputTokens is the comparable ordinary-input bucket used for pricing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelUsage {

    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long uncachedInputTokens;
    private Long cacheReadInputTokens;
    private Long cacheCreationInputTokens;
    private Long reasoningTokens;

    public static ModelUsage fromOpenAiChat(Usage usage) {
        if (usage == null) {
            return null;
        }
        Long input = Long.valueOf(usage.getPromptTokens());
        Long output = Long.valueOf(usage.getCompletionTokens());
        Long cacheRead = usage.getCachedTokens();
        return builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(Long.valueOf(usage.getTotalTokens()))
                .uncachedInputTokens(uncachedInput(input, cacheRead))
                .cacheReadInputTokens(cacheRead)
                .reasoningTokens(usage.getReasoningTokens())
                .build();
    }

    public static ModelUsage fromResponses(ResponseUsage usage) {
        if (usage == null) {
            return null;
        }
        Long input = asLong(usage.getInputTokens());
        Long output = asLong(usage.getOutputTokens());
        Long total = asLong(usage.getTotalTokens());
        ResponseUsageDetails inputDetails = usage.getInputTokensDetails();
        ResponseUsageDetails outputDetails = usage.getOutputTokensDetails();
        Long cacheRead = inputDetails == null ? null : asLong(inputDetails.getCachedTokens());
        Long reasoning = outputDetails == null ? null : asLong(outputDetails.getReasoningTokens());
        return builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(total == null ? total(input, output) : total)
                .uncachedInputTokens(uncachedInput(input, cacheRead))
                .cacheReadInputTokens(cacheRead)
                .reasoningTokens(reasoning)
                .build();
    }

    public static ModelUsage fromAnthropic(AnthropicUsage usage) {
        if (usage == null) {
            return null;
        }
        Long input = Long.valueOf(usage.getInputTokens());
        Long output = Long.valueOf(usage.getOutputTokens());
        Long cacheRead = usage.getCacheReadInputTokens();
        Long cacheCreation = usage.getCacheCreationInputTokens();
        return builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(total(input, cacheRead, cacheCreation, output))
                .uncachedInputTokens(input)
                .cacheReadInputTokens(cacheRead)
                .cacheCreationInputTokens(cacheCreation)
                .build();
    }

    @SuppressWarnings("unchecked")
    public static ModelUsage fromMap(Object rawUsage) {
        if (rawUsage instanceof Usage) {
            return fromOpenAiChat((Usage) rawUsage);
        }
        if (rawUsage instanceof ResponseUsage) {
            return fromResponses((ResponseUsage) rawUsage);
        }
        if (rawUsage instanceof AnthropicUsage) {
            return fromAnthropic((AnthropicUsage) rawUsage);
        }
        if (!(rawUsage instanceof Map)) {
            return null;
        }
        Map<String, Object> usage = (Map<String, Object>) rawUsage;
        Long input = asLong(firstNonNull(
                usage.get("prompt_tokens"), usage.get("promptTokens"),
                usage.get("input_tokens"), usage.get("inputTokens")));
        Long output = asLong(firstNonNull(
                usage.get("completion_tokens"), usage.get("completionTokens"),
                usage.get("output_tokens"), usage.get("outputTokens")));
        Long total = asLong(firstNonNull(usage.get("total_tokens"), usage.get("totalTokens")));
        Long cacheRead = asLong(firstNonNull(
                usage.get("cache_read_input_tokens"), usage.get("cacheReadInputTokens"),
                nestedValue(usage, "prompt_tokens_details", "cached_tokens"),
                nestedValue(usage, "promptTokensDetails", "cachedTokens"),
                nestedValue(usage, "input_tokens_details", "cached_tokens"),
                nestedValue(usage, "inputTokensDetails", "cachedTokens")));
        Long cacheCreation = asLong(firstNonNull(
                usage.get("cache_creation_input_tokens"), usage.get("cacheCreationInputTokens")));
        Long reasoning = asLong(firstNonNull(
                nestedValue(usage, "completion_tokens_details", "reasoning_tokens"),
                nestedValue(usage, "completionTokensDetails", "reasoningTokens"),
                nestedValue(usage, "output_tokens_details", "reasoning_tokens"),
                nestedValue(usage, "outputTokensDetails", "reasoningTokens"),
                usage.get("reasoning_tokens"), usage.get("reasoningTokens")));
        boolean anthropic = usage.containsKey("cache_read_input_tokens")
                || usage.containsKey("cacheReadInputTokens")
                || usage.containsKey("cache_creation_input_tokens")
                || usage.containsKey("cacheCreationInputTokens");
        return builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(total == null
                        ? (anthropic ? total(input, cacheRead, cacheCreation, output) : total(input, output))
                        : total)
                .uncachedInputTokens(anthropic ? input : uncachedInput(input, cacheRead))
                .cacheReadInputTokens(cacheRead)
                .cacheCreationInputTokens(cacheCreation)
                .reasoningTokens(reasoning)
                .build();
    }

    public AgentModelResult.AgentModelResultBuilder applyTo(AgentModelResult.AgentModelResultBuilder builder) {
        return builder
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .uncachedInputTokens(uncachedInputTokens)
                .cacheReadInputTokens(cacheReadInputTokens)
                .cacheCreationInputTokens(cacheCreationInputTokens)
                .reasoningTokens(reasoningTokens);
    }

    private static Long uncachedInput(Long input, Long cacheRead) {
        if (input == null) {
            return null;
        }
        long cached = cacheRead == null ? 0L : cacheRead.longValue();
        return Long.valueOf(Math.max(0L, input.longValue() - cached));
    }

    private static Long total(Long... values) {
        long total = 0L;
        boolean present = false;
        if (values != null) {
            for (Long value : values) {
                if (value != null) {
                    total += value.longValue();
                    present = true;
                }
            }
        }
        return present ? Long.valueOf(total) : null;
    }

    @SuppressWarnings("unchecked")
    private static Object nestedValue(Map<String, Object> source, String outerKey, String innerKey) {
        Object value = source.get(outerKey);
        if (!(value instanceof Map)) {
            return null;
        }
        return ((Map<String, Object>) value).get(innerKey);
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
