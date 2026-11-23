package io.github.lnyocly.ai4j.ambient.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal shared utility helpers for {@code ai4j-ambient}.
 */
public final class AmbientSupport {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private AmbientSupport() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String emptyToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String requireNonBlank(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    /**
     * Normalizes line endings to LF and trims leading/trailing whitespace so prompt
     * digests remain identical across operating systems.
     */
    public static String normalizeMarkdown(String raw) {
        if (raw == null) {
            return "";
        }
        String lf = raw.replace("\r\n", "\n").replace('\r', '\n');
        return lf.trim();
    }

    public static String sha256Hex(String input) {
        String normalized = input == null ? "" : input;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                hex[i * 2] = HEX_DIGITS[v >>> 4];
                hex[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public static <T> List<T> copyList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public static <K, V> Map<K, V> copyMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(source));
    }
}
