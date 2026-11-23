package io.github.lnyocly.ai4j.preset;

import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.ProviderProfile;
import io.github.lnyocly.ai4j.service.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pure configuration preset describing a provider's default wire protocol,
 * authentication header strategy, base URL, environment variable names, and
 * {@link ProtocolQuirks}.
 */
public final class ProviderPreset {

    private final String id;
    private final Platform platform;
    private final Protocol protocol;
    private final String baseUrl;
    private final String endpointPath;
    private final Credentials.Type credentialType;
    private final String credentialHeaderName;
    private final List<String> apiKeyEnvVars;
    private final List<String> baseUrlEnvVars;
    private final Map<String, String> defaultHeaders;
    private final ProtocolQuirks quirks;

    private ProviderPreset(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is required").trim().toLowerCase(Locale.ROOT);
        if (this.id.isEmpty()) {
            throw new IllegalArgumentException("preset id must not be blank");
        }
        this.platform = builder.platform == null ? Platform.of(this.id) : builder.platform;
        this.protocol = Objects.requireNonNull(builder.protocol, "protocol is required");
        this.baseUrl = Objects.requireNonNull(builder.baseUrl, "baseUrl is required");
        this.endpointPath = builder.endpointPath;
        this.credentialType = builder.credentialType == null ? Credentials.Type.BEARER : builder.credentialType;
        this.credentialHeaderName = builder.credentialHeaderName;
        this.apiKeyEnvVars = Collections.unmodifiableList(new ArrayList<String>(builder.apiKeyEnvVars));
        this.baseUrlEnvVars = Collections.unmodifiableList(new ArrayList<String>(builder.baseUrlEnvVars));
        this.defaultHeaders = builder.defaultHeaders.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.defaultHeaders));
        this.quirks = builder.quirks == null ? ProtocolQuirks.DEFAULTS : builder.quirks;
    }

    public static Builder builder(String id, Protocol protocol) {
        return new Builder(id, protocol);
    }

    public String id() {
        return id;
    }

    public Platform platform() {
        return platform;
    }

    public Protocol protocol() {
        return protocol;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String endpointPath() {
        return endpointPath;
    }

    public Credentials.Type credentialType() {
        return credentialType;
    }

    public String credentialHeaderName() {
        return credentialHeaderName;
    }

    public List<String> apiKeyEnvVars() {
        return apiKeyEnvVars;
    }

    public List<String> baseUrlEnvVars() {
        return baseUrlEnvVars;
    }

    public Map<String, String> defaultHeaders() {
        return defaultHeaders;
    }

    public ProtocolQuirks quirks() {
        return quirks;
    }

    public ProviderProfile toProviderProfile() {
        return quirks.toProviderProfile(id);
    }

    /** Build an {@link Endpoint} using the preset's default protocol and base URL. */
    public Endpoint toEndpoint(String apiKey) {
        return toEndpoint(protocol, apiKey, null);
    }

    /** Build an {@link Endpoint} with an optional base URL override. */
    public Endpoint toEndpoint(String apiKey, String baseUrlOverride) {
        return toEndpoint(protocol, apiKey, baseUrlOverride);
    }

    /** Build an {@link Endpoint} with optional protocol and base URL overrides. */
    public Endpoint toEndpoint(Protocol protocolOverride, String apiKey, String baseUrlOverride) {
        Credentials credentials = buildCredentials(apiKey);
        return toEndpoint(protocolOverride, credentials, baseUrlOverride);
    }

    /** Build an {@link Endpoint} using explicit {@link Credentials} and optional base URL override. */
    public Endpoint toEndpoint(Credentials credentials, String baseUrlOverride) {
        return toEndpoint(protocol, credentials, baseUrlOverride);
    }

    /** Build an {@link Endpoint} from explicit protocol, credentials, and base URL override. */
    public Endpoint toEndpoint(Protocol protocolOverride, Credentials credentials, String baseUrlOverride) {
        Protocol effectiveProtocol = protocolOverride == null ? this.protocol : protocolOverride;
        String effectiveBaseUrl = resolveBaseUrl(baseUrlOverride);
        String effectivePath = resolveEndpointPath(effectiveProtocol, effectiveBaseUrl);
        Endpoint.Builder builder = Endpoint.builder(effectiveProtocol)
                .name(id)
                .baseUrl(effectiveBaseUrl)
                .endpointPath(effectivePath)
                .credentials(credentials == null ? Credentials.none() : credentials)
                .profile(toProviderProfile());
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    /** Build an {@link Endpoint} resolving credentials and optional base URL from environment variables. */
    public Endpoint toEndpointFromEnv() {
        String envBaseUrl = firstNonBlankEnv(baseUrlEnvVars);
        Credentials credentials = buildEnvCredentials();
        return toEndpoint(protocol, credentials, envBaseUrl);
    }

    /** True when at least one configured API key env var is currently non-blank. */
    public boolean hasEnvApiKey() {
        return firstNonBlankEnv(apiKeyEnvVars) != null;
    }

    /** Create {@link Credentials} for a plaintext API key using this preset's auth header policy. */
    public Credentials buildCredentials(String apiKey) {
        if (credentialType == Credentials.Type.NONE) {
            return Credentials.none();
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Credentials.none();
        }
        if (credentialType == Credentials.Type.HEADER) {
            return Credentials.header(requireHeaderName(), apiKey);
        }
        return Credentials.bearer(apiKey);
    }

    /** Create lazy environment-backed {@link Credentials} for this preset. */
    public Credentials buildEnvCredentials() {
        if (credentialType == Credentials.Type.NONE || apiKeyEnvVars.isEmpty()) {
            return Credentials.none();
        }
        String selectedEnvVar = apiKeyEnvVars.get(0);
        for (String candidate : apiKeyEnvVars) {
            String value = System.getenv(candidate);
            if (value != null && !value.trim().isEmpty()) {
                selectedEnvVar = candidate;
                break;
            }
        }
        if (credentialType == Credentials.Type.HEADER) {
            return Credentials.headerEnv(requireHeaderName(), selectedEnvVar);
        }
        return Credentials.bearerEnv(selectedEnvVar);
    }

    private String requireHeaderName() {
        if (credentialHeaderName == null || credentialHeaderName.trim().isEmpty()) {
            throw new IllegalStateException("Preset '" + id + "' requires credentialHeaderName for HEADER auth");
        }
        return credentialHeaderName;
    }

    private String resolveBaseUrl(String override) {
        if (override != null && !override.trim().isEmpty()) {
            return override.trim();
        }
        return baseUrl;
    }

    private String resolveEndpointPath(Protocol effectiveProtocol, String effectiveBaseUrl) {
        String rawPath;
        if (effectiveProtocol == this.protocol && endpointPath != null) {
            rawPath = endpointPath;
        } else {
            rawPath = defaultPathForProtocol(effectiveProtocol);
        }
        if (rawPath == null) {
            return null;
        }
        return stripRedundantVersionPrefix(effectiveBaseUrl, rawPath);
    }

    private String defaultPathForProtocol(Protocol targetProtocol) {
        switch (targetProtocol) {
            case CHAT_COMPLETIONS:
                if ("openai".equals(id) || "minimax".equals(id) || "moonshot".equals(id)) {
                    return "/v1/chat/completions";
                }
                if ("zhipu".equals(id)) {
                    return "/v4/chat/completions";
                }
                return "/chat/completions";
            case RESPONSES:
                return "openai".equals(id) ? "/v1/responses" : "/responses";
            case MESSAGES:
                return "/v1/messages";
            case GEMINI:
                return "/v1beta/models/{model}:generateContent";
            default:
                return null;
        }
    }

    private static String stripRedundantVersionPrefix(String baseUrl, String path) {
        if (baseUrl == null || path == null) {
            return path;
        }
        String trimmedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String[] prefixes = {"/v1beta", "/v1", "/v3", "/v4"};
        for (String prefix : prefixes) {
            if (trimmedBase.endsWith(prefix) && path.startsWith(prefix + "/")) {
                return path.substring(prefix.length());
            }
        }
        return path;
    }

    private static String firstNonBlankEnv(List<String> names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ProviderPreset{id='" + id + "', protocol=" + protocol + ", baseUrl='" + baseUrl + "'}";
    }

    /** Fluent builder. */
    public static final class Builder {

        private final String id;
        private Platform platform;
        private final Protocol protocol;
        private String baseUrl;
        private String endpointPath;
        private Credentials.Type credentialType = Credentials.Type.BEARER;
        private String credentialHeaderName;
        private final List<String> apiKeyEnvVars = new ArrayList<String>();
        private final List<String> baseUrlEnvVars = new ArrayList<String>();
        private final Map<String, String> defaultHeaders = new LinkedHashMap<String, String>();
        private ProtocolQuirks quirks = ProtocolQuirks.DEFAULTS;

        private Builder(String id, Protocol protocol) {
            this.id = id;
            this.protocol = protocol;
        }

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        public Builder bearerAuth() {
            this.credentialType = Credentials.Type.BEARER;
            this.credentialHeaderName = null;
            return this;
        }

        public Builder headerAuth(String headerName) {
            this.credentialType = Credentials.Type.HEADER;
            this.credentialHeaderName = Objects.requireNonNull(headerName, "headerName is required");
            return this;
        }

        public Builder noAuth() {
            this.credentialType = Credentials.Type.NONE;
            this.credentialHeaderName = null;
            return this;
        }

        public Builder apiKeyEnv(String... envVars) {
            if (envVars != null) {
                for (String env : envVars) {
                    if (env != null && !env.trim().isEmpty()) {
                        this.apiKeyEnvVars.add(env.trim());
                    }
                }
            }
            return this;
        }

        public Builder baseUrlEnv(String... envVars) {
            if (envVars != null) {
                for (String env : envVars) {
                    if (env != null && !env.trim().isEmpty()) {
                        this.baseUrlEnvVars.add(env.trim());
                    }
                }
            }
            return this;
        }

        public Builder defaultHeader(String name, String value) {
            if (name != null && value != null) {
                this.defaultHeaders.put(name, value);
            }
            return this;
        }

        public Builder quirks(ProtocolQuirks quirks) {
            this.quirks = quirks;
            return this;
        }

        public ProviderPreset build() {
            return new ProviderPreset(this);
        }
    }
}
