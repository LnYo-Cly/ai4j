package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.memory.ChatMemory;
import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.memory.ChatMemoryPolicy;
import io.github.lnyocly.ai4j.memory.ChatMemorySnapshot;
import io.github.lnyocly.ai4j.memory.MessageWindowChatMemoryPolicy;
import io.github.lnyocly.ai4j.memory.UnboundedChatMemoryPolicy;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed {@link ChatMemory} supporting configurable key prefix, TTL expiration,
 * and {@link ChatMemoryPolicy} windowing/compaction.
 */
public class RedisChatMemory implements ChatMemory {

    public static final String DEFAULT_KEY_PREFIX = "ai4j:chat_memory:";

    private final RedisClientAdapter client;
    private final String sessionId;
    private final String keyPrefix;
    private final long ttlSeconds;
    private ChatMemoryPolicy policy;

    public RedisChatMemory(RedisClientAdapter client, String sessionId) {
        this(client, sessionId, DEFAULT_KEY_PREFIX, 0L, new UnboundedChatMemoryPolicy());
    }

    public RedisChatMemory(RedisClientAdapter client, String sessionId, ChatMemoryPolicy policy) {
        this(client, sessionId, DEFAULT_KEY_PREFIX, 0L, policy);
    }

    public RedisChatMemory(RedisClientAdapter client,
                           String sessionId,
                           String keyPrefix,
                           long ttlSeconds,
                           ChatMemoryPolicy policy) {
        if (client == null) {
            throw new IllegalArgumentException("RedisClientAdapter must not be null");
        }
        String normalizedSessionId = trimToNull(sessionId);
        if (normalizedSessionId == null) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        this.client = client;
        this.sessionId = normalizedSessionId;
        this.keyPrefix = RedisConfig.normalizePrefix(keyPrefix, DEFAULT_KEY_PREFIX);
        this.ttlSeconds = Math.max(0L, ttlSeconds);
        this.policy = policy == null ? new UnboundedChatMemoryPolicy() : policy;
    }

    private RedisChatMemory(Builder builder) {
        this(
                builder.client,
                builder.sessionId,
                builder.keyPrefix,
                builder.ttlSeconds,
                resolveBuilderPolicy(builder.policy, builder.maxMessages)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getKey() {
        return keyPrefix + sessionId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public synchronized void setPolicy(ChatMemoryPolicy policy) {
        this.policy = policy == null ? new UnboundedChatMemoryPolicy() : policy;
        List<ChatMemoryItem> current = loadItems();
        if (!current.isEmpty()) {
            replaceItems(applyPolicy(current));
        }
    }

    @Override
    public void addSystem(String text) {
        add(ChatMemoryItem.system(text));
    }

    @Override
    public void addUser(String text) {
        add(ChatMemoryItem.user(text));
    }

    @Override
    public void addUser(String text, String... imageUrls) {
        add(ChatMemoryItem.user(text, imageUrls));
    }

    @Override
    public void addAssistant(String text) {
        add(ChatMemoryItem.assistant(text));
    }

    @Override
    public void addAssistant(String text, List<ToolCall> toolCalls) {
        add(ChatMemoryItem.assistant(text, toolCalls));
    }

    @Override
    public void addAssistantToolCalls(List<ToolCall> toolCalls) {
        add(ChatMemoryItem.assistantToolCalls(toolCalls));
    }

    @Override
    public void addToolOutput(String toolCallId, String output) {
        add(ChatMemoryItem.tool(toolCallId, output));
    }

    @Override
    public synchronized void add(ChatMemoryItem item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        List<ChatMemoryItem> items = loadItems();
        items.add(ChatMemoryItem.copyOf(item));
        List<ChatMemoryItem> retained = applyPolicy(items);
        if (policy instanceof UnboundedChatMemoryPolicy && retained.size() == items.size()) {
            client.rpush(getKey(), JsonCodec.toJson(ChatMemoryItem.copyOf(item)));
            refreshTtl();
            return;
        }
        replaceItems(retained);
    }

    @Override
    public synchronized void addAll(List<ChatMemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ChatMemoryItem> merged = loadItems();
        int added = 0;
        List<String> appendedJson = new ArrayList<String>();
        for (ChatMemoryItem item : items) {
            if (item != null && !item.isEmpty()) {
                ChatMemoryItem copy = ChatMemoryItem.copyOf(item);
                merged.add(copy);
                appendedJson.add(JsonCodec.toJson(copy));
                added++;
            }
        }
        if (added == 0) {
            return;
        }
        List<ChatMemoryItem> retained = applyPolicy(merged);
        if (policy instanceof UnboundedChatMemoryPolicy && retained.size() == merged.size()) {
            client.rpush(getKey(), appendedJson.toArray(new String[0]));
            refreshTtl();
            return;
        }
        replaceItems(retained);
    }

    @Override
    public synchronized List<ChatMemoryItem> getItems() {
        return copyItems(loadItems());
    }

    @Override
    public synchronized List<ChatMessage> toChatMessages() {
        List<ChatMemoryItem> items = loadItems();
        List<ChatMessage> messages = new ArrayList<ChatMessage>(items.size());
        for (ChatMemoryItem item : items) {
            messages.add(item.toChatMessage());
        }
        return messages;
    }

    @Override
    public synchronized List<Object> toResponsesInput() {
        List<ChatMemoryItem> items = loadItems();
        List<Object> input = new ArrayList<Object>(items.size());
        for (ChatMemoryItem item : items) {
            input.add(item.toResponsesInput());
        }
        return input;
    }

    @Override
    public synchronized ChatMemorySnapshot snapshot() {
        return ChatMemorySnapshot.from(loadItems());
    }

    @Override
    public synchronized void restore(ChatMemorySnapshot snapshot) {
        List<ChatMemoryItem> items = new ArrayList<ChatMemoryItem>();
        if (snapshot != null && snapshot.getItems() != null) {
            for (ChatMemoryItem item : snapshot.getItems()) {
                if (item != null && !item.isEmpty()) {
                    items.add(ChatMemoryItem.copyOf(item));
                }
            }
        }
        replaceItems(applyPolicy(items));
    }

    @Override
    public synchronized void clear() {
        client.del(getKey());
    }

    private List<ChatMemoryItem> loadItems() {
        List<String> rawItems = client.lrange(getKey(), 0L, -1L);
        if (rawItems == null || rawItems.isEmpty()) {
            return new ArrayList<ChatMemoryItem>();
        }
        List<ChatMemoryItem> parsed = new ArrayList<ChatMemoryItem>(rawItems.size());
        for (String raw : rawItems) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            ChatMemoryItem item = JsonCodec.fromJson(raw, ChatMemoryItem.class);
            if (item != null && !item.isEmpty()) {
                parsed.add(item);
            }
        }
        return parsed;
    }

    private void replaceItems(List<ChatMemoryItem> items) {
        String key = getKey();
        client.del(key);
        if (items == null || items.isEmpty()) {
            return;
        }
        String[] serialized = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            serialized[i] = JsonCodec.toJson(items.get(i));
        }
        client.rpush(key, serialized);
        refreshTtl();
    }

    private void refreshTtl() {
        if (ttlSeconds > 0L) {
            client.expire(getKey(), ttlSeconds);
        }
    }

    private List<ChatMemoryItem> applyPolicy(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = copyItems(items);
        ChatMemoryPolicy activePolicy = policy == null ? new UnboundedChatMemoryPolicy() : policy;
        List<ChatMemoryItem> applied = activePolicy.apply(copied);
        return applied == null ? Collections.<ChatMemoryItem>emptyList() : copyItems(applied);
    }

    private static List<ChatMemoryItem> copyItems(List<ChatMemoryItem> items) {
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

    private static ChatMemoryPolicy resolveBuilderPolicy(ChatMemoryPolicy policy, Integer maxMessages) {
        if (policy != null) {
            return policy;
        }
        if (maxMessages != null) {
            return new MessageWindowChatMemoryPolicy(maxMessages.intValue());
        }
        return new UnboundedChatMemoryPolicy();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private RedisClientAdapter client;
        private String sessionId;
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private long ttlSeconds = 0L;
        private ChatMemoryPolicy policy;
        private Integer maxMessages;

        private Builder() {
        }

        public Builder client(RedisClientAdapter client) {
            this.client = client;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder ttlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder policy(ChatMemoryPolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            if (maxMessages < 0) {
                throw new IllegalArgumentException("maxMessages must be >= 0");
            }
            this.maxMessages = Integer.valueOf(maxMessages);
            return this;
        }

        public RedisChatMemory build() {
            return new RedisChatMemory(this);
        }
    }
}
