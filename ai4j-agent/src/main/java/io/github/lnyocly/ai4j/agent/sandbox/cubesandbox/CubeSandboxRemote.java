package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;

final class CubeSandboxRemote {
    private final String templateId;
    private final String sandboxId;
    private final String clientId;
    private final String envdVersion;
    private final String envdAccessToken;
    private final String trafficAccessToken;
    private final String domain;

    CubeSandboxRemote(String templateId,
                      String sandboxId,
                      String clientId,
                      String envdVersion,
                      String envdAccessToken,
                      String trafficAccessToken,
                      String domain) {
        this.templateId = trimToNull(templateId);
        this.sandboxId = trimToNull(sandboxId);
        this.clientId = trimToNull(clientId);
        this.envdVersion = trimToNull(envdVersion);
        this.envdAccessToken = trimToNull(envdAccessToken);
        this.trafficAccessToken = trimToNull(trafficAccessToken);
        this.domain = trimToNull(domain);
    }

    String getTemplateId() {
        return templateId;
    }

    String getSandboxId() {
        return sandboxId;
    }

    String getClientId() {
        return clientId;
    }

    String getEnvdVersion() {
        return envdVersion;
    }

    String getEnvdAccessToken() {
        return envdAccessToken;
    }

    String getTrafficAccessToken() {
        return trafficAccessToken;
    }

    String getDomain() {
        return domain;
    }

    String host(int port, String fallbackDomain) throws SandboxException {
        String effectiveDomain = domain == null ? fallbackDomain : domain;
        validateHostPart(sandboxId, "sandboxID");
        validateHostPart(effectiveDomain, "domain");
        return port + "-" + sandboxId + "." + effectiveDomain;
    }

    private static void validateHostPart(String value, String field) throws SandboxException {
        if (value == null || value.trim().isEmpty()) {
            throw new SandboxException("CubeSandbox response missing " + field);
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '.'
                    || ch == '_';
            if (!allowed) {
                throw new SandboxException("CubeSandbox response contains invalid " + field + " for virtual host");
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
