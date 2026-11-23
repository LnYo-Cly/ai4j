package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.util.AgentJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis-backed {@link AgentSessionStore} persisting {@link AgentSessionSnapshot} payloads as JSON
 * with configurable key prefix and optional TTL.
 */
public class RedisAgentSessionStore implements AgentSessionStore {

    public static final String DEFAULT_KEY_PREFIX = "ai4j:agent_session:";

    private final RedisClientAdapter client;
    private final String keyPrefix;
    private final long ttlSeconds;

    public RedisAgentSessionStore(RedisClientAdapter client) {
        this(client, DEFAULT_KEY_PREFIX, 0L);
    }

    public RedisAgentSessionStore(RedisClientAdapter client, String keyPrefix, long ttlSeconds) {
        if (client == null) {
            throw new IllegalArgumentException("RedisClientAdapter must not be null");
        }
        this.client = client;
        this.keyPrefix = RedisConfig.normalizePrefix(keyPrefix, DEFAULT_KEY_PREFIX);
        this.ttlSeconds = Math.max(0L, ttlSeconds);
    }

    private RedisAgentSessionStore(Builder builder) {
        this(builder.client, builder.keyPrefix, builder.ttlSeconds);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    @Override
    public void save(AgentSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getSessionId() == null || snapshot.getSessionId().trim().isEmpty()) {
            return;
        }
        String key = keyPrefix + snapshot.getSessionId().trim();
        String json = AgentJson.toJson(snapshot);
        if (ttlSeconds > 0L) {
            client.setex(key, ttlSeconds, json);
        } else {
            client.set(key, json);
        }
    }

    @Override
    public AgentSessionSnapshot load(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        String json = client.get(keyPrefix + sessionId.trim());
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return AgentJson.fromJson(json, AgentSessionSnapshot.class);
    }

    @Override
    public boolean delete(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        return client.del(keyPrefix + sessionId.trim());
    }

    @Override
    public List<String> listSessionIds() {
        List<String> keys = client.scanKeys(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (String key : keys) {
            if (key != null && key.startsWith(keyPrefix) && key.length() > keyPrefix.length()) {
                ids.add(key.substring(keyPrefix.length()));
            }
        }
        List<String> sorted = new ArrayList<String>(ids);
        Collections.sort(sorted);
        return sorted;
    }

    public static final class Builder {
        private RedisClientAdapter client;
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private long ttlSeconds = 0L;

        private Builder() {
        }

        public Builder client(RedisClientAdapter client) {
            this.client = client;
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

        public RedisAgentSessionStore build() {
            return new RedisAgentSessionStore(this);
        }
    }
}
