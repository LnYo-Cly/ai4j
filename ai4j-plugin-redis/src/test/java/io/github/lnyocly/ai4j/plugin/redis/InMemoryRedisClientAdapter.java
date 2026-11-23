package io.github.lnyocly.ai4j.plugin.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Deterministic offline in-memory {@link RedisClientAdapter} test double with controllable logical clock.
 */
final class InMemoryRedisClientAdapter implements RedisClientAdapter {

    private final AtomicLong clockMillis = new AtomicLong(1_000_000L);
    private final Map<String, String> strings = new LinkedHashMap<String, String>();
    private final Map<String, List<String>> lists = new LinkedHashMap<String, List<String>>();
    private final Map<String, Map<String, String>> hashes = new LinkedHashMap<String, Map<String, String>>();
    private final Map<String, Long> expiresAtMillis = new LinkedHashMap<String, Long>();
    private final List<PublishedMessage> publishedMessages = new ArrayList<PublishedMessage>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    void advanceSeconds(long seconds) {
        clockMillis.addAndGet(seconds * 1000L);
    }

    Long getExpireMillis(String key) {
        evictIfExpired(key);
        return expiresAtMillis.get(key);
    }

    List<PublishedMessage> getPublishedMessages() {
        return Collections.unmodifiableList(new ArrayList<PublishedMessage>(publishedMessages));
    }

    boolean isClosed() {
        return closed.get();
    }

    @Override
    public synchronized String get(String key) {
        evictIfExpired(key);
        return strings.get(key);
    }

    @Override
    public synchronized void set(String key, String value) {
        expiresAtMillis.remove(key);
        strings.put(key, value);
    }

    @Override
    public synchronized void setex(String key, long ttlSeconds, String value) {
        strings.put(key, value);
        if (ttlSeconds > 0L) {
            expiresAtMillis.put(key, Long.valueOf(clockMillis.get() + ttlSeconds * 1000L));
        } else {
            expiresAtMillis.remove(key);
        }
    }

    @Override
    public synchronized boolean setIfAbsent(String key, String value, long ttlSeconds) {
        evictIfExpired(key);
        if (strings.containsKey(key)) {
            return false;
        }
        setex(key, ttlSeconds, value);
        return true;
    }

    @Override
    public synchronized boolean del(String key) {
        expiresAtMillis.remove(key);
        boolean existed = strings.remove(key) != null;
        existed = (lists.remove(key) != null) || existed;
        existed = (hashes.remove(key) != null) || existed;
        return existed;
    }

    @Override
    public synchronized boolean expire(String key, long ttlSeconds) {
        evictIfExpired(key);
        if (ttlSeconds <= 0L) {
            return false;
        }
        if (!strings.containsKey(key) && !lists.containsKey(key) && !hashes.containsKey(key)) {
            return false;
        }
        expiresAtMillis.put(key, Long.valueOf(clockMillis.get() + ttlSeconds * 1000L));
        return true;
    }

    @Override
    public synchronized List<String> scanKeys(String pattern) {
        evictAllExpired();
        Pattern regex = globToRegex(pattern == null ? "*" : pattern);
        List<String> matched = new ArrayList<String>();
        for (String key : strings.keySet()) {
            if (regex.matcher(key).matches()) {
                matched.add(key);
            }
        }
        for (String key : lists.keySet()) {
            if (!matched.contains(key) && regex.matcher(key).matches()) {
                matched.add(key);
            }
        }
        for (String key : hashes.keySet()) {
            if (!matched.contains(key) && regex.matcher(key).matches()) {
                matched.add(key);
            }
        }
        return matched;
    }

    @Override
    public synchronized long rpush(String key, String... values) {
        evictIfExpired(key);
        List<String> list = lists.get(key);
        if (list == null) {
            list = new ArrayList<String>();
            lists.put(key, list);
        }
        if (values != null) {
            Collections.addAll(list, values);
        }
        return list.size();
    }

    @Override
    public synchronized List<String> lrange(String key, long start, long stop) {
        evictIfExpired(key);
        List<String> list = lists.get(key);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int size = list.size();
        int s = normalizeIndex(start, size);
        int e = normalizeIndex(stop, size);
        if (s < 0) {
            s = 0;
        }
        if (e >= size) {
            e = size - 1;
        }
        if (s > e || s >= size) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(list.subList(s, e + 1));
    }

    @Override
    public synchronized void ltrim(String key, long start, long stop) {
        evictIfExpired(key);
        List<String> list = lists.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        List<String> sliced = lrange(key, start, stop);
        if (sliced.isEmpty()) {
            lists.remove(key);
        } else {
            lists.put(key, new ArrayList<String>(sliced));
        }
    }

    @Override
    public synchronized void hset(String key, String field, String value) {
        evictIfExpired(key);
        Map<String, String> hash = hashes.get(key);
        if (hash == null) {
            hash = new LinkedHashMap<String, String>();
            hashes.put(key, hash);
        }
        hash.put(field, value);
    }

    @Override
    public synchronized String hget(String key, String field) {
        evictIfExpired(key);
        Map<String, String> hash = hashes.get(key);
        return hash == null ? null : hash.get(field);
    }

    @Override
    public synchronized Map<String, String> hgetAll(String key) {
        evictIfExpired(key);
        Map<String, String> hash = hashes.get(key);
        return hash == null ? Collections.<String, String>emptyMap() : new LinkedHashMap<String, String>(hash);
    }

    @Override
    public synchronized long hdel(String key, String... fields) {
        evictIfExpired(key);
        Map<String, String> hash = hashes.get(key);
        if (hash == null || fields == null) {
            return 0L;
        }
        long removed = 0L;
        for (String field : fields) {
            if (hash.remove(field) != null) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public synchronized long publish(String channel, String message) {
        publishedMessages.add(new PublishedMessage(channel, message));
        return 1L;
    }

    @Override
    public boolean ping() {
        return !closed.get();
    }

    @Override
    public void close() {
        closed.set(true);
    }

    private void evictAllExpired() {
        List<String> keys = new ArrayList<String>(expiresAtMillis.keySet());
        for (String key : keys) {
            evictIfExpired(key);
        }
    }

    private void evictIfExpired(String key) {
        Long expiry = expiresAtMillis.get(key);
        if (expiry != null && clockMillis.get() >= expiry.longValue()) {
            expiresAtMillis.remove(key);
            strings.remove(key);
            lists.remove(key);
            hashes.remove(key);
        }
    }

    private static int normalizeIndex(long index, int size) {
        return index < 0 ? (int) (size + index) : (int) index;
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    static final class PublishedMessage {
        final String channel;
        final String message;

        PublishedMessage(String channel, String message) {
            this.channel = channel;
            this.message = message;
        }
    }
}
