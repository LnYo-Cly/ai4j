package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SummaryChatMemoryPolicyConfig {

    private ChatMemorySummarizer summarizer;

    @Builder.Default
    private int maxRecentMessages = 12;

    @Builder.Default
    private int summaryTriggerMessages = 20;

    @Builder.Default
    private String summaryRole = ChatMessageType.ASSISTANT.getRole();

    @Builder.Default
    private String summaryTextPrefix = "Summary of earlier conversation:\n";
}
