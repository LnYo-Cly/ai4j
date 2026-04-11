package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SummaryChatMemoryPolicy implements ChatMemoryPolicy {

    private final ChatMemorySummarizer summarizer;
    private final int maxRecentMessages;
    private final int summaryTriggerMessages;
    private final String summaryRole;
    private final String summaryTextPrefix;

    public SummaryChatMemoryPolicy(SummaryChatMemoryPolicyConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        if (config.getSummarizer() == null) {
            throw new IllegalArgumentException("summarizer is required");
        }
        if (config.getMaxRecentMessages() < 0) {
            throw new IllegalArgumentException("maxRecentMessages must be >= 0");
        }
        if (config.getSummaryTriggerMessages() <= config.getMaxRecentMessages()) {
            throw new IllegalArgumentException("summaryTriggerMessages must be > maxRecentMessages");
        }
        String role = trimToNull(config.getSummaryRole());
        if (!ChatMessageType.SYSTEM.getRole().equals(role) && !ChatMessageType.ASSISTANT.getRole().equals(role)) {
            throw new IllegalArgumentException("summaryRole must be system or assistant");
        }
        this.summarizer = config.getSummarizer();
        this.maxRecentMessages = config.getMaxRecentMessages();
        this.summaryTriggerMessages = config.getSummaryTriggerMessages();
        this.summaryRole = role;
        this.summaryTextPrefix = config.getSummaryTextPrefix();
    }

    public SummaryChatMemoryPolicy(ChatMemorySummarizer summarizer, int maxRecentMessages, int summaryTriggerMessages) {
        this(SummaryChatMemoryPolicyConfig.builder()
                .summarizer(summarizer)
                .maxRecentMessages(maxRecentMessages)
                .summaryTriggerMessages(summaryTriggerMessages)
                .build());
    }

    @Override
    public List<ChatMemoryItem> apply(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = copyItems(items);
        if (copied.isEmpty()) {
            return copied;
        }

        List<Integer> summaryEligibleIndices = collectSummaryEligibleIndices(copied);
        if (summaryEligibleIndices.size() <= summaryTriggerMessages) {
            return copied;
        }

        int keepRecent = Math.min(maxRecentMessages, summaryEligibleIndices.size());
        int summarizeCount = summaryEligibleIndices.size() - keepRecent;
        if (summarizeCount <= 0) {
            return copied;
        }

        String existingSummary = mergeExistingSummary(copied, summaryEligibleIndices, summarizeCount);
        List<ChatMemoryItem> itemsToSummarize = collectItemsToSummarize(copied, summaryEligibleIndices, summarizeCount);
        if (itemsToSummarize.isEmpty()) {
            return copied;
        }

        String summaryText = summarizer.summarize(ChatMemorySummaryRequest.from(existingSummary, itemsToSummarize));
        String renderedSummary = renderSummary(summaryText);
        if (renderedSummary == null) {
            return copied;
        }

        ChatMemoryItem summaryItem = ChatMemoryItem.summary(summaryRole, renderedSummary);
        return rebuildWithSummary(copied, summaryEligibleIndices, summarizeCount, summaryItem);
    }

    private List<Integer> collectSummaryEligibleIndices(List<ChatMemoryItem> items) {
        List<Integer> indices = new ArrayList<Integer>();
        for (int i = 0; i < items.size(); i++) {
            ChatMemoryItem item = items.get(i);
            if (item == null) {
                continue;
            }
            if (isPinnedSystemItem(item)) {
                continue;
            }
            indices.add(i);
        }
        return indices;
    }

    private boolean isPinnedSystemItem(ChatMemoryItem item) {
        return ChatMessageType.SYSTEM.getRole().equals(item.getRole()) && !item.isSummary();
    }

    private String mergeExistingSummary(List<ChatMemoryItem> items, List<Integer> eligibleIndices, int summarizeCount) {
        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < summarizeCount; i++) {
            ChatMemoryItem item = items.get(eligibleIndices.get(i));
            if (item == null || !item.isSummary()) {
                continue;
            }
            String text = stripSummaryPrefix(item.getText());
            if (!hasText(text)) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append("\n\n");
            }
            merged.append(text);
        }
        return trimToNull(merged.toString());
    }

    private List<ChatMemoryItem> collectItemsToSummarize(List<ChatMemoryItem> items,
                                                         List<Integer> eligibleIndices,
                                                         int summarizeCount) {
        List<ChatMemoryItem> collected = new ArrayList<ChatMemoryItem>();
        for (int i = 0; i < summarizeCount; i++) {
            ChatMemoryItem item = items.get(eligibleIndices.get(i));
            if (item == null || item.isSummary()) {
                continue;
            }
            collected.add(ChatMemoryItem.copyOf(item));
        }
        return collected;
    }

    private String stripSummaryPrefix(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        if (hasText(summaryTextPrefix) && value.startsWith(summaryTextPrefix)) {
            return trimToNull(value.substring(summaryTextPrefix.length()));
        }
        return value;
    }

    private String renderSummary(String summaryText) {
        String value = trimToNull(summaryText);
        if (value == null) {
            return null;
        }
        if (hasText(summaryTextPrefix) && value.startsWith(summaryTextPrefix)) {
            return value;
        }
        return hasText(summaryTextPrefix) ? summaryTextPrefix + value : value;
    }

    private List<ChatMemoryItem> rebuildWithSummary(List<ChatMemoryItem> items,
                                                    List<Integer> eligibleIndices,
                                                    int summarizeCount,
                                                    ChatMemoryItem summaryItem) {
        Set<Integer> summarizedIndices = new HashSet<Integer>();
        for (int i = 0; i < summarizeCount; i++) {
            summarizedIndices.add(eligibleIndices.get(i));
        }

        List<ChatMemoryItem> rebuilt = new ArrayList<ChatMemoryItem>();
        int insertionIndex = eligibleIndices.get(0);
        for (int i = 0; i < items.size(); i++) {
            if (i == insertionIndex) {
                rebuilt.add(ChatMemoryItem.copyOf(summaryItem));
            }
            if (summarizedIndices.contains(i)) {
                continue;
            }
            ChatMemoryItem item = items.get(i);
            if (item != null) {
                rebuilt.add(ChatMemoryItem.copyOf(item));
            }
        }
        return rebuilt;
    }

    private List<ChatMemoryItem> copyItems(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = new ArrayList<ChatMemoryItem>();
        if (items == null) {
            return copied;
        }
        for (ChatMemoryItem item : items) {
            if (item != null) {
                copied.add(ChatMemoryItem.copyOf(item));
            }
        }
        return copied;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }
}
