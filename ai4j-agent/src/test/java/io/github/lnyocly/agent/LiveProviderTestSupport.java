package io.github.lnyocly.agent;

import org.junit.Assume;

final class LiveProviderTestSupport {

    private LiveProviderTestSupport() {
    }

    static String requireEnv(String skipMessage, String... envKeys) {
        for (String envKey : envKeys) {
            String value = System.getenv(envKey);
            if (!isBlank(value)) {
                return value;
            }
        }
        Assume.assumeTrue(skipMessage + " Expected env vars: " + join(envKeys), false);
        return null;
    }

    static String readNonSecretValue(String envKey, String propertyKey) {
        String value = envKey == null ? null : System.getenv(envKey);
        if (isBlank(value) && propertyKey != null) {
            value = System.getProperty(propertyKey);
        }
        return value;
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }
}
