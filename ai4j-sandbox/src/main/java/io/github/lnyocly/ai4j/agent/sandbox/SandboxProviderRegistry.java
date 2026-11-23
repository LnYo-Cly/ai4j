package io.github.lnyocly.ai4j.agent.sandbox;

import io.github.lnyocly.ai4j.extension.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe registry of {@link SandboxProvider} implementations managed via Cordis extensions.
 */
public final class SandboxProviderRegistry {

    private final Map<String, SandboxProvider> providers = new LinkedHashMap<String, SandboxProvider>();

    public synchronized Disposable register(final SandboxProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("sandbox provider must not be null");
        }
        final String key = normalizeId(provider.getProviderId());
        if (key == null) {
            throw new IllegalArgumentException("sandbox providerId must not be blank");
        }
        providers.put(key, provider);
        return new Disposable() {
            @Override
            public void dispose() {
                synchronized (SandboxProviderRegistry.this) {
                    SandboxProvider current = providers.get(key);
                    if (current == provider) {
                        providers.remove(key);
                    }
                }
            }
        };
    }

    public synchronized boolean unregister(String providerId) {
        String key = normalizeId(providerId);
        return key != null && providers.remove(key) != null;
    }

    public synchronized Optional<SandboxProvider> find(String providerId) {
        String key = normalizeId(providerId);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(key));
    }

    public synchronized Optional<SandboxProvider> findForSpec(SandboxSpec spec) {
        if (spec != null && spec.getProviderId() != null && !spec.getProviderId().trim().isEmpty()) {
            SandboxProvider direct = providers.get(normalizeId(spec.getProviderId()));
            if (direct != null) {
                return Optional.of(direct);
            }
        }
        for (SandboxProvider provider : providers.values()) {
            if (provider != null && provider.supports(spec)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    public synchronized List<SandboxProvider> list() {
        return Collections.unmodifiableList(new ArrayList<SandboxProvider>(providers.values()));
    }

    public synchronized void clear() {
        providers.clear();
    }

    private static String normalizeId(String providerId) {
        if (providerId == null) {
            return null;
        }
        String trimmed = providerId.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
