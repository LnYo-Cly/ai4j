package io.github.lnyocly.ai4j.platform.openai.usage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/7 17:38
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Usage implements Serializable {
    @JsonProperty("prompt_tokens")
    private long promptTokens = 0L;
    @JsonProperty("completion_tokens")
    private long completionTokens = 0L;
    @JsonProperty("total_tokens")
    private long totalTokens = 0L;

    @JsonProperty("prompt_tokens_details")
    private UsageDetails promptTokensDetails;

    @JsonProperty("completion_tokens_details")
    private UsageDetails completionTokensDetails;

    /**
     * Compatibility constructor retained for callers that only provide the original totals.
     */
    public Usage(long promptTokens, long completionTokens, long totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    @JsonIgnore
    public Long getCachedTokens() {
        return promptTokensDetails == null ? null : promptTokensDetails.getCachedTokens();
    }

    @JsonIgnore
    public Long getCacheWriteTokens() {
        return promptTokensDetails == null ? null : promptTokensDetails.getCacheWriteTokens();
    }

    @JsonIgnore
    public Long getReasoningTokens() {
        return completionTokensDetails == null ? null : completionTokensDetails.getReasoningTokens();
    }

    /** Adds usage from another completed request, including optional detail buckets. */
    public void merge(Usage source) {
        if (source == null) {
            return;
        }
        promptTokens += source.getPromptTokens();
        completionTokens += source.getCompletionTokens();
        totalTokens += source.getTotalTokens();
        promptTokensDetails = mergeDetails(promptTokensDetails, source.getPromptTokensDetails());
        completionTokensDetails = mergeDetails(completionTokensDetails, source.getCompletionTokensDetails());
    }

    private static UsageDetails mergeDetails(UsageDetails target, UsageDetails source) {
        if (source == null) {
            return target;
        }
        if (target == null) {
            target = new UsageDetails();
        }
        target.setCachedTokens(sum(target.getCachedTokens(), source.getCachedTokens()));
        target.setCacheWriteTokens(sum(target.getCacheWriteTokens(), source.getCacheWriteTokens()));
        target.setReasoningTokens(sum(target.getReasoningTokens(), source.getReasoningTokens()));
        target.setAudioTokens(sum(target.getAudioTokens(), source.getAudioTokens()));
        target.setAcceptedPredictionTokens(sum(target.getAcceptedPredictionTokens(), source.getAcceptedPredictionTokens()));
        target.setRejectedPredictionTokens(sum(target.getRejectedPredictionTokens(), source.getRejectedPredictionTokens()));
        return target;
    }

    private static Long sum(Long current, Long next) {
        if (next == null) {
            return current;
        }
        return current == null ? next : Long.valueOf(current.longValue() + next.longValue());
    }
}
