package io.github.lnyocly.ai4j.extension;

import java.util.Objects;

/**
 * Immutable typed key identifying a service seam shared across extensions.
 */
public final class ServiceKey<T> {

    private final String id;
    private final Class<T> type;

    private ServiceKey(String id, Class<T> type) {
        this.id = ExtensionManifest.requireId(id, "service key id");
        if (type == null) {
            throw new IllegalArgumentException("service key type must not be null");
        }
        this.type = type;
    }

    public static <T> ServiceKey<T> of(String id, Class<T> type) {
        return new ServiceKey<T>(id, type);
    }

    public String id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    public T cast(Object instance) {
        if (instance == null) {
            return null;
        }
        if (!type.isInstance(instance)) {
            throw new ClassCastException("cannot cast " + instance.getClass().getName()
                    + " to service key " + id + " (" + type.getName() + ")");
        }
        return type.cast(instance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceKey)) {
            return false;
        }
        ServiceKey<?> other = (ServiceKey<?>) o;
        return id.equals(other.id) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "ServiceKey{id='" + id + "', type=" + type.getName() + "}";
    }
}
