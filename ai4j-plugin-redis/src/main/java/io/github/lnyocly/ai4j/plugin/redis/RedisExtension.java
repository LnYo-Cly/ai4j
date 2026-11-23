package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.memory.ChatMemoryPolicy;
import io.github.lnyocly.ai4j.memory.UnboundedChatMemoryPolicy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cordis {@link Ai4jExtension} registering Redis client adapter, {@link RedisChatMemoryFactory},
 * {@link RedisAgentSessionStore}, {@link RedisHarnessLeaseNotifier}, and {@link Disposable} cleanup.
 */
public final class RedisExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "redis";

    public static final ServiceKey<RedisConfig> CONFIG_SERVICE_KEY =
            ServiceKey.of("redis.config", RedisConfig.class);
    public static final ServiceKey<RedisClientAdapter> CLIENT_SERVICE_KEY =
            ServiceKey.of("redis.client", RedisClientAdapter.class);
    public static final ServiceKey<AgentSessionStore> SESSION_STORE_SERVICE_KEY =
            ServiceKey.of("redis.sessionStore", AgentSessionStore.class);
    public static final ServiceKey<RedisAgentSessionStore> REDIS_SESSION_STORE_SERVICE_KEY =
            ServiceKey.of("redis.redisSessionStore", RedisAgentSessionStore.class);
    public static final ServiceKey<RedisHarnessLeaseNotifier> LEASE_NOTIFIER_SERVICE_KEY =
            ServiceKey.of("redis.leaseNotifier", RedisHarnessLeaseNotifier.class);
    public static final ServiceKey<RedisChatMemoryFactory> CHAT_MEMORY_FACTORY_SERVICE_KEY =
            ServiceKey.of("redis.chatMemoryFactory", RedisChatMemoryFactory.class);

    private static final String VERSION = "2.8.2-SNAPSHOT";

    private final RedisConfig defaultConfig;
    private final RedisClientAdapter defaultClient;
    private final Set<RedisClientAdapter> ownedClients = Collections.synchronizedSet(new LinkedHashSet<RedisClientAdapter>());
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public RedisExtension() {
        this(null, null);
    }

    public RedisExtension(RedisConfig defaultConfig) {
        this(defaultConfig, null);
    }

    public RedisExtension(RedisClientAdapter defaultClient) {
        this(null, defaultClient);
    }

    public RedisExtension(RedisConfig defaultConfig, RedisClientAdapter defaultClient) {
        this.defaultConfig = defaultConfig;
        this.defaultClient = defaultClient;
    }

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("Redis Storage & Coordination")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("network:redis")
                .configPrefix("ai4j.extensions.redis")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("extension context must not be null");
        }
        stopped.set(false);
        final RedisConfig config = resolveConfig(context);
        final ClientResolution resolution = resolveClient(context, config);
        final RedisClientAdapter client = resolution.client;
        if (resolution.owned) {
            ownedClients.add(client);
        }

        final String prefix = config.getKeyPrefix();
        final long ttlSeconds = config.getDefaultTtlSeconds();
        final RedisAgentSessionStore sessionStore = new RedisAgentSessionStore(
                client,
                prefix + "agent_session:",
                ttlSeconds
        );
        final RedisHarnessLeaseNotifier leaseNotifier = new RedisHarnessLeaseNotifier(
                client,
                prefix + "harness:lease:",
                ttlSeconds
        );
        final RedisChatMemoryFactory memoryFactory = new RedisChatMemoryFactory() {
            @Override
            public RedisChatMemory open(String sessionId, ChatMemoryPolicy policy) {
                return new RedisChatMemory(
                        client,
                        sessionId,
                        prefix + "chat_memory:",
                        ttlSeconds,
                        policy == null ? new UnboundedChatMemoryPolicy() : policy
                );
            }
        };

        context.provide(CONFIG_SERVICE_KEY, config);
        context.provide(CLIENT_SERVICE_KEY, client);
        context.provide(SESSION_STORE_SERVICE_KEY, sessionStore);
        context.provide(REDIS_SESSION_STORE_SERVICE_KEY, sessionStore);
        context.provide(LEASE_NOTIFIER_SERVICE_KEY, leaseNotifier);
        context.provide(CHAT_MEMORY_FACTORY_SERVICE_KEY, memoryFactory);

        context.effect(new Disposable() {
            @Override
            public void dispose() {
                if (resolution.owned) {
                    ownedClients.remove(client);
                    closeQuietly(client);
                }
            }
        });
    }

    @Override
    public void onStop() {
        stopped.set(true);
        RedisClientAdapter[] snapshot;
        synchronized (ownedClients) {
            snapshot = ownedClients.toArray(new RedisClientAdapter[0]);
            ownedClients.clear();
        }
        for (RedisClientAdapter client : snapshot) {
            closeQuietly(client);
        }
    }

    public boolean isStopped() {
        return stopped.get();
    }

    private RedisConfig resolveConfig(ExtensionContext context) {
        Optional<RedisConfig> provided = context.findService(CONFIG_SERVICE_KEY);
        if (provided.isPresent()) {
            return provided.get();
        }
        return defaultConfig != null ? defaultConfig : RedisConfig.from(System.getenv());
    }

    private ClientResolution resolveClient(ExtensionContext context, RedisConfig config) {
        Optional<RedisClientAdapter> provided = context.findService(CLIENT_SERVICE_KEY);
        if (provided.isPresent()) {
            return new ClientResolution(provided.get(), false);
        }
        if (defaultClient != null) {
            return new ClientResolution(defaultClient, false);
        }
        return new ClientResolution(new JedisClientAdapter(config), true);
    }

    private static void closeQuietly(RedisClientAdapter client) {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    public interface RedisChatMemoryFactory {
        RedisChatMemory open(String sessionId, ChatMemoryPolicy policy);

        default RedisChatMemory open(String sessionId) {
            return open(sessionId, new UnboundedChatMemoryPolicy());
        }
    }

    private static final class ClientResolution {
        private final RedisClientAdapter client;
        private final boolean owned;

        private ClientResolution(RedisClientAdapter client, boolean owned) {
            this.client = client;
            this.owned = owned;
        }
    }
}
