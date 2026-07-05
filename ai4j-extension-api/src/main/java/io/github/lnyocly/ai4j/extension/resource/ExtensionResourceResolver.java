package io.github.lnyocly.ai4j.extension.resource;

import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ExtensionResourceResolver {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private ExtensionResourceResolver() {
    }

    public static String readText(String resourcePath, ClassLoader preferredClassLoader) {
        return readText(resourcePath, preferredClassLoader, true);
    }

    public static String readTextStrict(String resourcePath, ClassLoader preferredClassLoader) {
        return readText(resourcePath, preferredClassLoader, false);
    }

    private static String readText(String resourcePath, ClassLoader preferredClassLoader, boolean allowFallback) {
        String normalizedPath = normalizeResourcePath(resourcePath);
        InputStream stream = allowFallback
                ? open(normalizedPath, preferredClassLoader)
                : openWith(preferredClassLoader, normalizedPath);
        if (stream == null) {
            throw new ExtensionException("extension resource not found: " + normalizedPath);
        }
        try {
            return readUtf8(stream);
        } catch (IOException ex) {
            throw new ExtensionException("failed to read extension resource: " + normalizedPath, ex);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static boolean exists(String resourcePath, ClassLoader preferredClassLoader) {
        return exists(resourcePath, preferredClassLoader, true);
    }

    public static boolean existsStrict(String resourcePath, ClassLoader preferredClassLoader) {
        return exists(resourcePath, preferredClassLoader, false);
    }

    private static boolean exists(String resourcePath, ClassLoader preferredClassLoader, boolean allowFallback) {
        String normalizedPath = normalizeResourcePath(resourcePath);
        InputStream stream = allowFallback
                ? open(normalizedPath, preferredClassLoader)
                : openWith(preferredClassLoader, normalizedPath);
        if (stream == null) {
            return false;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
        return true;
    }

    public static String normalizeResourcePath(String resourcePath) {
        String normalized = ExtensionManifest.requireId(resourcePath, "extension resource path");
        if (normalized.startsWith(CLASSPATH_PREFIX)) {
            normalized = normalized.substring(CLASSPATH_PREFIX.length()).trim();
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.indexOf("..") >= 0) {
            throw new IllegalArgumentException("extension resource path must not contain '..': " + resourcePath);
        }
        return ExtensionManifest.requireId(normalized, "extension resource path");
    }

    private static InputStream open(String normalizedPath, ClassLoader preferredClassLoader) {
        InputStream stream = openWith(preferredClassLoader, normalizedPath);
        if (stream != null) {
            return stream;
        }
        stream = openWith(Thread.currentThread().getContextClassLoader(), normalizedPath);
        if (stream != null) {
            return stream;
        }
        return openWith(ExtensionResourceResolver.class.getClassLoader(), normalizedPath);
    }

    private static InputStream openWith(ClassLoader classLoader, String normalizedPath) {
        if (classLoader == null) {
            return null;
        }
        return classLoader.getResourceAsStream(normalizedPath);
    }

    private static String readUtf8(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
