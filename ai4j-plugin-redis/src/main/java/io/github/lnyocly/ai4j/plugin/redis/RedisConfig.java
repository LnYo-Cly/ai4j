package io.github.lnyocly.ai4j.plugin.redis;

import java.util.Collections;
import java.util.Map;

/**
 * Connection and keyspace configuration for {@code ai4j-plugin-redis}.
 */
public final class RedisConfig {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 6379;
    public static final int DEFAULT_DATABASE = 0;
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;
    public static final String DEFAULT_KEY_PREFIX = "ai4j:";
    public static final long DEFAULT_TTL_SECONDS = 0L;

    public static final String ENV_REDIS_HOST = "AI4J_REDIS_HOST";
    public static final String ENV_REDIS_PORT = "AI4J_REDIS_PORT";
    public static final String ENV_REDIS_USERNAME = "AI4J_REDIS_USERNAME";
    public static final String ENV_REDIS_PASSWORD = "AI4J_REDIS_PASSWORD";
    public static final String ENV_REDIS_DATABASE = "AI4J_REDIS_DATABASE";
    public static final String ENV_REDIS_SSL = "AI4J_REDIS_SSL";
    public static final String ENV_REDIS_TIMEOUT_MS = "AI4J_REDIS_TIMEOUT_MS";
    public static final String ENV_REDIS_KEY_PREFIX = "AI4J_REDIS_KEY_PREFIX";
    public static final String ENV_REDIS_TTL_SECONDS = "AI4J_REDIS_TTL_SECONDS";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final int database;
    private final boolean ssl;
    private final int timeoutMillis;
    private final String keyPrefix;
    private final long defaultTtlSeconds;

    private RedisConfig(Builder builder) {
        this.host = firstNonBlank(builder.host, DEFAULT_HOST);
        this.port = builder.port > 0 ? builder.port : DEFAULT_PORT;
        this.username = trimToNull(builder.username);
        this.password = trimToNull(builder.password);
        this.database = Math.max(0, builder.database);
        this.ssl = builder.ssl;
        this.timeoutMillis = builder.timeoutMillis > 0 ? builder.timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
        this.keyPrefix = normalizePrefix(builder.keyPrefix, DEFAULT_KEY_PREFIX);
        this.defaultTtlSeconds = Math.max(0L, builder.defaultTtlSeconds);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RedisConfig from(Map<String, String> env) {
        Map<String, String> resolved = env == null ? Collections.<String, String>emptyMap() : env;
        Builder builder = builder();
        String host = trimToNull(resolved.get(ENV_REDIS_HOST));
        if (host != null) {
            builder.host(host);
        }
        Integer port = parseInt(resolved.get(ENV_REDIS_PORT));
        if (port != null && port.intValue() > 0) {
            builder.port(port.intValue());
        }
        String username = trimToNull(resolved.get(ENV_REDIS_USERNAME));
        if (username != null) {
            builder.username(username);
        }
        String password = trimToNull(resolved.get(ENV_REDIS_PASSWORD));
        if (password != null) {
            builder.password(password);
        }
        Integer db = parseInt(resolved.get(ENV_REDIS_DATABASE));
        if (db != null && db.intValue() >= 0) {
            builder.database(db.intValue());
        }
        String sslRaw = trimToNull(resolved.get(ENV_REDIS_SSL));
        if (sslRaw != null) {
            builder.ssl("true".equalsIgnoreCase(sslRaw));
        }
        Integer timeout = parseInt(resolved.get(ENV_REDIS_TIMEOUT_MS));
        if (timeout != null && timeout.intValue() > 0) {
            builder.timeoutMillis(timeout.intValue());
        }
        String prefix = trimToNull(resolved.get(ENV_REDIS_KEY_PREFIX));
        if (prefix != null) {
            builder.keyPrefix(prefix);
        }
        Long ttl = parseLong(resolved.get(ENV_REDIS_TTL_SECONDS));
        if (ttl != null && ttl.longValue() >= 0L) {
            builder.defaultTtlSeconds(ttl.longValue());
        }
        return builder.build();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    public boolean isSsl() {
        return ssl;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public static String normalizePrefix(String prefix, String fallback) {
        String candidate = trimToNull(prefix);
        if (candidate == null) {
            candidate = fallback;
        }
        return candidate.endsWith(":") ? candidate : candidate + ":";
    }

    private static String firstNonBlank(String primary, String fallback) {
        String value = trimToNull(primary);
        return value == null ? fallback : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer parseInt(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(trimmed));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(trimmed));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static final class Builder {
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private String username;
        private String password;
        private int database = DEFAULT_DATABASE;
        private boolean ssl = false;
        private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private long defaultTtlSeconds = DEFAULT_TTL_SECONDS;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(int database) {
            this.database = database;
            return this;
        }

        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        public Builder timeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder defaultTtlSeconds(long defaultTtlSeconds) {
            this.defaultTtlSeconds = defaultTtlSeconds;
            return this;
        }

        public RedisConfig build() {
            return new RedisConfig(this);
        }
    }
}
