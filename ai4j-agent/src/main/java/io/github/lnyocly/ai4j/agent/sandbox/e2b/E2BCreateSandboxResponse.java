package io.github.lnyocly.ai4j.agent.sandbox.e2b;

/**
 * Minimal E2B {@code POST /sandboxes} response DTO.
 *
 * <p>The modern E2B control API returns {@code sandboxID}, {@code clientID}, {@code envdVersion},
 * {@code templateID} and {@code alias}. Older/secure flows additionally return an
 * {@code envdAccessToken} used to authenticate the per-sandbox execution host; when absent the
 * API key itself is used as the execution bearer token.</p>
 */
public final class E2BCreateSandboxResponse {

    private String sandboxID;
    private String clientID;
    private String envdVersion;
    private String templateID;
    private String alias;
    private String envdAccessToken;

    public E2BCreateSandboxResponse() {
    }

    public String getSandboxID() {
        return sandboxID;
    }

    public void setSandboxID(String sandboxID) {
        this.sandboxID = sandboxID;
    }

    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    public String getEnvdVersion() {
        return envdVersion;
    }

    public void setEnvdVersion(String envdVersion) {
        this.envdVersion = envdVersion;
    }

    public String getTemplateID() {
        return templateID;
    }

    public void setTemplateID(String templateID) {
        this.templateID = templateID;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getEnvdAccessToken() {
        return envdAccessToken;
    }

    public void setEnvdAccessToken(String envdAccessToken) {
        this.envdAccessToken = envdAccessToken;
    }
}
