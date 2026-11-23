package io.github.lnyocly.ai4j.protocol;

import java.util.Objects;

/**
 * Endpoint credentials, applied by the transport at request time.
 *
 * <p>Secrets never enter the semantic model, message content or metadata
 * (design 7.1); this type only carries the header shape and the value source.
 * {@link #bearerEnv(String)} resolves the environment variable lazily so no
 * secret is captured at configuration time and none is written to git.</p>
 */
public final class Credentials {

    /** How the credential is attached to an HTTP request. */
    public enum Type {
        /** {@code Authorization: Bearer <value>}. */
        BEARER,

        /** {@code <name>: <value>} for protocols such as Anthropic Messages. */
        HEADER,

        /** No credential is attached. */
        NONE
    }

    private static final Credentials NONE = new Credentials(Type.NONE, null, null, null);

    private final Type type;
    private final String headerName;
    private final String value;
    private final String envVar;

    private Credentials(Type type, String headerName, String value, String envVar) {
        this.type = type;
        this.headerName = headerName;
        this.value = value;
        this.envVar = envVar;
    }

    public static Credentials none() {
        return NONE;
    }

    public static Credentials bearer(String apiKey) {
        return new Credentials(Type.BEARER, null, Objects.requireNonNull(apiKey, "apiKey is required"), null);
    }

    /** Bearer credential resolved from an environment variable at request time. */
    public static Credentials bearerEnv(String envVar) {
        return new Credentials(Type.BEARER, null, null,
                Objects.requireNonNull(envVar, "envVar is required"));
    }

    /** Explicit header credential, e.g. {@code x-api-key} for Anthropic Messages. */
    public static Credentials header(String headerName, String value) {
        return new Credentials(Type.HEADER,
                Objects.requireNonNull(headerName, "headerName is required"),
                Objects.requireNonNull(value, "value is required"), null);
    }

    /** Header credential resolved from an environment variable at request time. */
    public static Credentials headerEnv(String headerName, String envVar) {
        return new Credentials(Type.HEADER,
                Objects.requireNonNull(headerName, "headerName is required"),
                null, Objects.requireNonNull(envVar, "envVar is required"));
    }

    public Type getType() {
        return type;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Resolved credential value.
     *
     * @return the value, or {@code null} when no credential is configured or the
     *         environment variable is missing
     */
    public String resolveValue() {
        if (value != null) {
            return value;
        }
        if (envVar == null) {
            return null;
        }
        return System.getenv(envVar);
    }

    /** The environment variable name when this credential was declared by env. */
    public String getEnvVar() {
        return envVar;
    }
}
