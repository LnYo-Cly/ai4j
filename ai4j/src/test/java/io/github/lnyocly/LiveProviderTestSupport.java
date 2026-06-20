package io.github.lnyocly;

import org.junit.Assume;

import java.io.File;

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

    static String firstEnv(String... envKeys) {
        for (String envKey : envKeys) {
            String value = System.getenv(envKey);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    static File requireReadableFile(String envKey, String skipMessage) {
        String path = System.getenv(envKey);
        Assume.assumeTrue(skipMessage + " Expected env var: " + envKey, !isBlank(path));
        File file = new File(path);
        Assume.assumeTrue(skipMessage + " File is not readable: " + path, file.isFile() && file.canRead());
        return file;
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
