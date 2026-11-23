package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.ServiceLoader;

public class RedisExtensionTest {

    @Test
    public void discoversExtensionRegistersServicesAndCleansUpOnDisable() {
        RedisExtension discovered = null;
        for (Ai4jExtension ext : ServiceLoader.load(Ai4jExtension.class)) {
            if (ext instanceof RedisExtension) {
                discovered = (RedisExtension) ext;
                break;
            }
        }
        Assert.assertNotNull(discovered);
        Assert.assertEquals(RedisExtension.EXTENSION_ID, discovered.manifest().getId());
        Assert.assertTrue(discovered.manifest().hasCapability(ExtensionCapability.TOOL));

        InMemoryRedisClientAdapter inMemoryClient = new InMemoryRedisClientAdapter();
        RedisConfig config = RedisConfig.builder()
                .keyPrefix("ext:redis:")
                .defaultTtlSeconds(300L)
                .build();
        RedisExtension extension = new RedisExtension(config, inMemoryClient);

        ExtensionRegistry registry = ExtensionRegistry.of(extension)
                .enable(RedisExtension.EXTENSION_ID);

        Assert.assertTrue(registry.findService(RedisExtension.CONFIG_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(RedisExtension.CLIENT_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(RedisExtension.SESSION_STORE_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(RedisExtension.LEASE_NOTIFIER_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(RedisExtension.CHAT_MEMORY_FACTORY_SERVICE_KEY).isPresent());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, RedisExtension.EXTENSION_ID);
        Assert.assertTrue("Expected zero validation errors", report.isValid());

        AgentSessionStore sessionStore = registry.findService(RedisExtension.SESSION_STORE_SERVICE_KEY).get();
        AgentSessionSnapshot snapshot = new AgentSessionSnapshot();
        snapshot.setMetadata(new AgentSessionMetadata("ext-sess-1", 10L, 11L, null));
        sessionStore.save(snapshot);
        Assert.assertNotNull(sessionStore.load("ext-sess-1"));

        RedisExtension.RedisChatMemoryFactory memoryFactory =
                registry.findService(RedisExtension.CHAT_MEMORY_FACTORY_SERVICE_KEY).get();
        RedisChatMemory chatMemory = memoryFactory.open("ext-chat-1");
        chatMemory.addUser("hello from cordis");
        Assert.assertEquals("ext:redis:chat_memory:ext-chat-1", chatMemory.getKey());
        Assert.assertEquals(1, chatMemory.getItems().size());

        registry.disable(RedisExtension.EXTENSION_ID);
        Assert.assertTrue(extension.isStopped());
        Assert.assertFalse(registry.findService(RedisExtension.SESSION_STORE_SERVICE_KEY).isPresent());
    }

    @Test
    public void defaultConstructorDiscoversAndDisposesOwnedJedisPoolWithoutConnecting() {
        ExtensionRegistry registry = ExtensionRegistry.discover()
                .enable(RedisExtension.EXTENSION_ID);
        Assert.assertTrue(registry.findService(RedisExtension.CLIENT_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(RedisExtension.LEASE_NOTIFIER_SERVICE_KEY).isPresent());

        registry.disable(RedisExtension.EXTENSION_ID);
        Assert.assertFalse(registry.findService(RedisExtension.CLIENT_SERVICE_KEY).isPresent());
    }
}
