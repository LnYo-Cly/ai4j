package io.github.lnyocly.ai4j.plugin.redis;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link RedisClientAdapter} backed by {@link JedisPool}.
 */
public class JedisClientAdapter implements RedisClientAdapter {

    private final JedisPool pool;
    private final boolean ownsPool;

    public JedisClientAdapter(RedisConfig config) {
        RedisConfig resolved = config == null ? RedisConfig.builder().build() : config;
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(resolved.getTimeoutMillis())
                .socketTimeoutMillis(resolved.getTimeoutMillis())
                .database(resolved.getDatabase())
                .ssl(resolved.isSsl());
        if (resolved.getUsername() != null) {
            clientConfigBuilder.user(resolved.getUsername());
        }
        if (resolved.getPassword() != null) {
            clientConfigBuilder.password(resolved.getPassword());
        }
        this.pool = new JedisPool(
                poolConfig,
                new HostAndPort(resolved.getHost(), resolved.getPort()),
                clientConfigBuilder.build()
        );
        this.ownsPool = true;
    }

    public JedisClientAdapter(JedisPool pool) {
        this(pool, false);
    }

    public JedisClientAdapter(JedisPool pool, boolean ownsPool) {
        if (pool == null) {
            throw new IllegalArgumentException("JedisPool must not be null");
        }
        this.pool = pool;
        this.ownsPool = ownsPool;
    }

    @Override
    public String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    @Override
    public void set(String key, String value) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, value);
        }
    }

    @Override
    public void setex(String key, long ttlSeconds, String value) {
        if (ttlSeconds <= 0L) {
            set(key, value);
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        SetParams params = SetParams.setParams().nx();
        if (ttlSeconds > 0L) {
            params.ex(ttlSeconds);
        }
        try (Jedis jedis = pool.getResource()) {
            String reply = jedis.set(key, value, params);
            return "OK".equalsIgnoreCase(reply);
        }
    }

    @Override
    public boolean del(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.del(key) > 0L;
        }
    }

    @Override
    public boolean expire(String key, long ttlSeconds) {
        if (ttlSeconds <= 0L) {
            return false;
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.expire(key, ttlSeconds) > 0L;
        }
    }

    @Override
    public List<String> scanKeys(String pattern) {
        String match = pattern == null || pattern.trim().isEmpty() ? "*" : pattern;
        Set<String> keys = new LinkedHashSet<String>();
        ScanParams params = new ScanParams().match(match).count(200);
        String cursor = ScanParams.SCAN_POINTER_START;
        try (Jedis jedis = pool.getResource()) {
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                if (result.getResult() != null) {
                    keys.addAll(result.getResult());
                }
                cursor = result.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        }
        return new ArrayList<String>(keys);
    }

    @Override
    public long rpush(String key, String... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.rpush(key, values);
        }
    }

    @Override
    public List<String> lrange(String key, long start, long stop) {
        try (Jedis jedis = pool.getResource()) {
            List<String> values = jedis.lrange(key, start, stop);
            return values == null ? Collections.<String>emptyList() : values;
        }
    }

    @Override
    public void ltrim(String key, long start, long stop) {
        try (Jedis jedis = pool.getResource()) {
            jedis.ltrim(key, start, stop);
        }
    }

    @Override
    public void hset(String key, String field, String value) {
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(key, field, value);
        }
    }

    @Override
    public String hget(String key, String field) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hget(key, field);
        }
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> map = jedis.hgetAll(key);
            return map == null ? Collections.<String, String>emptyMap() : map;
        }
    }

    @Override
    public long hdel(String key, String... fields) {
        if (fields == null || fields.length == 0) {
            return 0L;
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.hdel(key, fields);
        }
    }

    @Override
    public long publish(String channel, String message) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.publish(channel, message);
        }
    }

    @Override
    public boolean ping() {
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping());
        }
    }

    @Override
    public void close() {
        if (ownsPool) {
            pool.close();
        }
    }
}
