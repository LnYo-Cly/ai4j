package io.github.lnyocly.ai4j.platform.moonshot.chat.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.platform.openai.usage.UsageDetails;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Kimi/Moonshot 专用 Usage：Kimi 把 cached_tokens 放在 usage 顶层，
 * 而 OpenAI 标准放在 usage.prompt_tokens_details.cached_tokens。
 * 此类在反序列化时捕获顶层的 cached_tokens，
 * 再由 MoonshotChatService.convertChatCompletionResponse 归一化到 OpenAI 标准。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoonshotUsage extends Usage {

    @JsonProperty("cached_tokens")
    private Long cachedTokens;

    /**
     * 归一化为 OpenAI 标准 Usage（顶层 cached_tokens 折叠到 prompt_tokens_details）。
     */
    public Usage toStandardUsage() {
        Usage standard = new Usage();
        standard.setPromptTokens(this.getPromptTokens());
        standard.setCompletionTokens(this.getCompletionTokens());
        standard.setTotalTokens(this.getTotalTokens());

        UsageDetails promptDetails = this.getPromptTokensDetails();
        if (promptDetails == null) {
            promptDetails = new UsageDetails();
        }
        if (promptDetails.getCachedTokens() == null && this.cachedTokens != null) {
            promptDetails.setCachedTokens(this.cachedTokens);
        }
        standard.setPromptTokensDetails(promptDetails);

        standard.setCompletionTokensDetails(this.getCompletionTokensDetails());
        return standard;
    }
}
