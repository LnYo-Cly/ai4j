package io.github.lnyocly.ai4j.service;

import java.util.Locale;
import java.util.Objects;

/**
 * Open platform identity contract.
 *
 * <p>{@link PlatformType} implements this interface for built-in platforms while
 * {@link #of(String)} allows private gateways and custom providers to participate
 * without modifying the enum.</p>
 */
public interface Platform {

    /** Canonical lowercase platform identifier, e.g. {@code "openai"} or {@code "corp-gateway"}. */
    String id();

    /**
     * Resolve a platform identifier to a {@link PlatformType} when known or an open
     * custom {@link Platform} instance otherwise.
     */
    static Platform of(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Platform id must not be null");
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Platform id must not be blank");
        }
        for (PlatformType builtIn : PlatformType.values()) {
            if (builtIn.getPlatform().equals(normalized)) {
                return builtIn;
            }
        }
        return new CustomPlatform(normalized);
    }

    /** Immutable value object for non-enum custom platform IDs. */
    final class CustomPlatform implements Platform {

        private final String id;

        private CustomPlatform(String id) {
            this.id = Objects.requireNonNull(id, "id is required");
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Platform)) {
                return false;
            }
            return id.equals(((Platform) o).id());
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
