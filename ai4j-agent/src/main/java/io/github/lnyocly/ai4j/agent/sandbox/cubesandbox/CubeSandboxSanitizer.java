package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class CubeSandboxSanitizer {

    private CubeSandboxSanitizer() {
    }

    static Map<String, String> nonSensitiveStringMap(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry == null || isSensitiveKey(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ENGLISH);
        return lower.contains("secret")
                || lower.contains("token")
                || lower.contains("key")
                || lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("credential")
                || lower.contains("authorization")
                || lower.contains("cookie");
    }
}
