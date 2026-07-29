package io.github.lnyocly.ai4j.agent.runtime;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.trace.TracePricing;
import io.github.lnyocly.ai4j.agent.trace.TracePricingResolver;

/** Aggregates provider-neutral usage across an agent run. */
final class AgentUsageAccumulator {

    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long uncachedInputTokens;
    private Long cacheReadInputTokens;
    private Long cacheWriteInputTokens;
    private Long cacheCreationInputTokens;
    private Long reasoningTokens;
    private boolean usagePresent;

    void add(AgentModelResult result) {
        if (result == null) {
            return;
        }
        usagePresent = usagePresent || hasUsage(result);
        inputTokens = sum(inputTokens, result.getInputTokens());
        outputTokens = sum(outputTokens, result.getOutputTokens());
        uncachedInputTokens = sum(uncachedInputTokens, result.getUncachedInputTokens());
        cacheReadInputTokens = sum(cacheReadInputTokens, result.getCacheReadInputTokens());
        cacheWriteInputTokens = sum(cacheWriteInputTokens, result.getCacheWriteInputTokens());
        cacheCreationInputTokens = sum(cacheCreationInputTokens, result.getCacheCreationInputTokens());
        reasoningTokens = sum(reasoningTokens, result.getReasoningTokens());
        Long resultTotal = result.getTotalTokens();
        if (resultTotal == null) {
            resultTotal = total(result.getInputTokens(), result.getCacheReadInputTokens(),
                    result.getCacheCreationInputTokens(), result.getOutputTokens());
        }
        totalTokens = sum(totalTokens, resultTotal);
    }

    boolean exceeds(long tokenBudget) {
        return tokenBudget > 0L && usagePresent && totalTokens != null && totalTokens.longValue() >= tokenBudget;
    }

    AgentResult.AgentResultBuilder applyTo(AgentResult.AgentResultBuilder builder, AgentContext context) {
        if (!usagePresent) {
            return builder;
        }
        builder.inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .uncachedInputTokens(uncachedInputTokens)
                .cacheReadInputTokens(cacheReadInputTokens)
                .cacheWriteInputTokens(cacheWriteInputTokens)
                .cacheCreationInputTokens(cacheCreationInputTokens)
                .reasoningTokens(reasoningTokens);

        TracePricing pricing = resolvePricing(context);
        if (pricing == null) {
            return builder;
        }
        Long ordinaryInput = uncachedInputTokens == null ? inputTokens : uncachedInputTokens;
        Double inputCost = bucketCost(ordinaryInput, pricing.getInputCostPerMillionTokens());
        Double cacheReadInputCost = bucketCost(cacheReadInputTokens, pricing.getCacheReadInputCostPerMillionTokens());
        Double cacheCreationInputCost = bucketCost(cacheCreationInputTokens, pricing.getCacheCreationInputCostPerMillionTokens());
        Double outputCost = bucketCost(outputTokens, pricing.getOutputCostPerMillionTokens());
        Double totalCost = canCalculateTotal(ordinaryInput, inputCost)
                && canCalculateTotal(cacheReadInputTokens, cacheReadInputCost)
                && canCalculateTotal(cacheCreationInputTokens, cacheCreationInputCost)
                && canCalculateTotal(outputTokens, outputCost)
                ? Double.valueOf(zeroIfNull(inputCost) + zeroIfNull(cacheReadInputCost)
                        + zeroIfNull(cacheCreationInputCost) + zeroIfNull(outputCost))
                : null;
        return builder.inputCost(inputCost)
                .cacheReadInputCost(cacheReadInputCost)
                .cacheCreationInputCost(cacheCreationInputCost)
                .outputCost(outputCost)
                .totalCost(totalCost)
                .currency(pricing.getCurrency());
    }

    private TracePricing resolvePricing(AgentContext context) {
        TracePricingResolver resolver = context == null ? null : context.getPricingResolver();
        if (resolver == null || context.getModel() == null) {
            return null;
        }
        return resolver.resolve(context.getModel());
    }

    private boolean hasUsage(AgentModelResult result) {
        return result.getInputTokens() != null || result.getOutputTokens() != null
                || result.getTotalTokens() != null || result.getUncachedInputTokens() != null
                || result.getCacheReadInputTokens() != null
                || result.getCacheWriteInputTokens() != null
                || result.getCacheCreationInputTokens() != null
                || result.getReasoningTokens() != null;
    }

    private Long sum(Long current, Long next) {
        if (next == null) {
            return current;
        }
        return current == null ? next : Long.valueOf(current.longValue() + next.longValue());
    }

    private Long total(Long... values) {
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

    private Double bucketCost(Long tokens, Double costPerMillionTokens) {
        if (tokens == null) {
            return null;
        }
        if (tokens.longValue() == 0L) {
            return Double.valueOf(0D);
        }
        if (costPerMillionTokens == null) {
            return null;
        }
        return Double.valueOf((tokens.longValue() / 1000000D) * costPerMillionTokens.doubleValue());
    }

    private boolean canCalculateTotal(Long tokens, Double cost) {
        return tokens == null || tokens.longValue() == 0L || cost != null;
    }

    private double zeroIfNull(Double value) {
        return value == null ? 0D : value.doubleValue();
    }
}
