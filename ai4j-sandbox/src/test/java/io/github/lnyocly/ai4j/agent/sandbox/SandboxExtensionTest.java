package io.github.lnyocly.ai4j.agent.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;
import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests Cordis discovery, {@link SandboxProviderRegistry}, {@link SandboxExtension} service provision,
 * and {@link Disposable} cleanup on disable.
 */
public class SandboxExtensionTest {

    @Test
    public void serviceLoaderDiscoversSandboxExtensionAndProvidesRegistryAndProviders() {
        ExtensionRegistry registry = ExtensionRegistry.discover();
        DiscoveredExtension discovered = findById(registry.list(), SandboxExtension.EXTENSION_ID);
        Assert.assertNotNull("ServiceLoader should discover SandboxExtension", discovered);

        registry.enable(SandboxExtension.EXTENSION_ID);

        SandboxProviderRegistry providerRegistry = registry.findService(SandboxExtension.REGISTRY_KEY).orElse(null);
        Assert.assertNotNull(providerRegistry);
        Assert.assertEquals(3, providerRegistry.list().size());
        Assert.assertTrue(providerRegistry.find("e2b").isPresent());
        Assert.assertTrue(providerRegistry.find("daytona").isPresent());
        Assert.assertTrue(providerRegistry.find("cubesandbox").isPresent());

        Assert.assertTrue(registry.findService(SandboxExtension.DEFAULT_PROVIDER_KEY).get() instanceof E2BSandboxProvider);
        Assert.assertTrue(registry.findService(SandboxExtension.E2B_PROVIDER_KEY).get() instanceof E2BSandboxProvider);
        Assert.assertTrue(registry.findService(SandboxExtension.DAYTONA_PROVIDER_KEY).get() instanceof DaytonaSandboxProvider);
        Assert.assertTrue(registry.findService(SandboxExtension.CUBE_PROVIDER_KEY).get() instanceof CubeSandboxProvider);

        Assert.assertTrue(providerRegistry.findForSpec(SandboxSpec.builder().providerId("daytona").build()).get()
                instanceof DaytonaSandboxProvider);
        Assert.assertTrue(providerRegistry.findForSpec(SandboxSpec.builder().providerId("cubesandbox").build()).get()
                instanceof CubeSandboxProvider);

        ExtensionValidationReport report = ExtensionValidator.validate(registry, SandboxExtension.EXTENSION_ID);
        Assert.assertTrue("Extension validation should produce zero errors", report.isValid());

        registry.disable(SandboxExtension.EXTENSION_ID);
        Assert.assertFalse(registry.findService(SandboxExtension.REGISTRY_KEY).isPresent());
        Assert.assertFalse(registry.findService(SandboxExtension.DEFAULT_PROVIDER_KEY).isPresent());
        Assert.assertTrue("Registry must clear registered providers on disable", providerRegistry.list().isEmpty());
    }

    @Test
    public void providerRegistryDisposableRemovesOnlyExactRegistration() {
        SandboxProviderRegistry registry = new SandboxProviderRegistry();
        SandboxProvider first = new E2BSandboxProvider();
        SandboxProvider second = new E2BSandboxProvider();

        Disposable firstHandle = registry.register(first);
        Assert.assertEquals(1, registry.list().size());

        Disposable secondHandle = registry.register(second);
        // Disposing stale handle must not evict replacement provider
        firstHandle.dispose();
        Assert.assertEquals(1, registry.list().size());
        Assert.assertSame(second, registry.find("e2b").get());

        secondHandle.dispose();
        Assert.assertTrue(registry.list().isEmpty());
        Assert.assertFalse(registry.unregister("e2b"));
    }

    private static DiscoveredExtension findById(List<DiscoveredExtension> extensions, String id) {
        for (DiscoveredExtension ext : extensions) {
            if (ext != null && id.equals(ext.getManifest().getId())) {
                return ext;
            }
        }
        return null;
    }
}
