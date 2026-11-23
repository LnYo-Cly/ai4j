package io.github.lnyocly.ai4j.agent.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cordis extension registering built-in sandbox providers (E2B, Daytona, CubeSandbox) and the
 * {@link SandboxProviderRegistry} via {@link ServiceKey} and {@link Disposable} cleanup hooks.
 */
public final class SandboxExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "sandbox";
    public static final ServiceKey<SandboxProviderRegistry> REGISTRY_KEY =
            ServiceKey.of("ai4j.sandbox.registry", SandboxProviderRegistry.class);
    public static final ServiceKey<SandboxProvider> DEFAULT_PROVIDER_KEY =
            ServiceKey.of("ai4j.sandbox.provider.default", SandboxProvider.class);
    public static final ServiceKey<SandboxProvider> E2B_PROVIDER_KEY =
            ServiceKey.of("ai4j.sandbox.provider.e2b", SandboxProvider.class);
    public static final ServiceKey<SandboxProvider> DAYTONA_PROVIDER_KEY =
            ServiceKey.of("ai4j.sandbox.provider.daytona", SandboxProvider.class);
    public static final ServiceKey<SandboxProvider> CUBE_PROVIDER_KEY =
            ServiceKey.of("ai4j.sandbox.provider.cubesandbox", SandboxProvider.class);

    private static final String VERSION = "2.8.2";

    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile SandboxProviderRegistry registry;

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("AI4J Sandbox Providers")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("sandbox.exec")
                .configPrefix("ai4j.extensions.sandbox")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("extension context must not be null");
        }
        final SandboxProviderRegistry providerRegistry = new SandboxProviderRegistry();
        final SandboxProvider e2b = new E2BSandboxProvider();
        final SandboxProvider daytona = new DaytonaSandboxProvider();
        final SandboxProvider cube = new CubeSandboxProvider();

        final Disposable regE2b = providerRegistry.register(e2b);
        final Disposable regDaytona = providerRegistry.register(daytona);
        final Disposable regCube = providerRegistry.register(cube);

        this.registry = providerRegistry;
        this.active.set(true);

        context.provide(REGISTRY_KEY, providerRegistry);
        context.provide(DEFAULT_PROVIDER_KEY, e2b);
        context.provide(E2B_PROVIDER_KEY, e2b);
        context.provide(DAYTONA_PROVIDER_KEY, daytona);
        context.provide(CUBE_PROVIDER_KEY, cube);

        context.effect(Disposable.composite(regE2b, regDaytona, regCube, new Disposable() {
            @Override
            public void dispose() {
                providerRegistry.clear();
                active.set(false);
                if (registry == providerRegistry) {
                    registry = null;
                }
            }
        }));
    }

    @Override
    public void onStop() {
        SandboxProviderRegistry current = this.registry;
        if (current != null) {
            current.clear();
        }
        this.active.set(false);
        this.registry = null;
    }

    public boolean isActive() {
        return active.get();
    }
}
