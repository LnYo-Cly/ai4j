package io.github.lnyocly.ai4j.plugin.redis;

import java.util.List;
import java.util.Map;

/**
 * Minimal Redis client abstraction used by {@code ai4j-plugin-redis} so storage,
 * memory, and lease-notification components remain testable offline.
 */
public interface RedisClientAdapter extends AutoCloseable {

    String get(String key);

    void set(String key, String value);

    void setex(String key, long ttlSeconds, String value);

    boolean setIfAbsent(String key, String value, long ttlSeconds);

    boolean del(String key);

    boolean expire(String key, long ttlSeconds);

    List<String> scanKeys(String pattern);

    long rpush(String key, String... values);

    List<String> lrange(String key, long start, long stop);

    void ltrim(String key, long start, long stop);

    void hset(String key, String field, String value);

    String hget(String key, String field);

    Map<String, String> hgetAll(String key);

    long hdel(String key, String... fields);

    long publish(String channel, String message);

    boolean ping();

    @Override
    void close();
}
