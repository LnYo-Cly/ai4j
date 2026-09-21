package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Loads extension JARs with one {@link URLClassLoader} per JAR so that plugin
 * dependencies stay isolated from each other and from the host. SPI contract
 * types ({@link Ai4jExtension}, {@link ExtensionContext}, manifests, specs) are
 * shared through the parent loader — the classloader that loaded
 * {@code ai4j-extension-api} — so isolation never produces ClassCastException
 * on the contract surface.
 *
 * <p>Isolation means dependency conflict freedom, not a security sandbox: a
 * malicious plugin still runs in-process. Untrusted code needs a process
 * boundary, not a classloader.</p>
 */
@Experimental(note = "Isolated JAR loading; track record before stabilizing")
public final class IsolatedExtensionLoader implements ExtensionLoader, AutoCloseable {

    private final List<Path> jarPaths;
    private final ClassLoader parent;
    private final Map<Ai4jExtension, URLClassLoader> loaders = new IdentityHashMap<Ai4jExtension, URLClassLoader>();
    private final List<URLClassLoader> allLoaders = new ArrayList<URLClassLoader>();
    private boolean closed;

    public IsolatedExtensionLoader(List<Path> jarPaths) {
        this(jarPaths, Ai4jExtension.class.getClassLoader());
    }

    public IsolatedExtensionLoader(List<Path> jarPaths, ClassLoader parent) {
        if (jarPaths == null || jarPaths.isEmpty()) {
            throw new IllegalArgumentException("jarPaths must not be empty");
        }
        this.jarPaths = new ArrayList<Path>(jarPaths);
        this.parent = parent;
    }

    /** Scans a directory for {@code *.jar} files (sorted by name for determinism). */
    public static IsolatedExtensionLoader fromDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("extension directory does not exist: " + directory);
        }
        List<Path> jars = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path jar : stream) {
                jars.add(jar);
            }
        } catch (IOException e) {
            throw new ExtensionException("failed to scan extension directory: " + directory, e);
        }
        Collections.sort(jars);
        if (jars.isEmpty()) {
            throw new ExtensionException("no extension jars found in: " + directory);
        }
        return new IsolatedExtensionLoader(jars);
    }

    @Override
    public List<Ai4jExtension> load() {
        if (closed) {
            throw new ExtensionException("isolated extension loader already closed");
        }
        List<Ai4jExtension> extensions = new ArrayList<Ai4jExtension>();
        for (Path jar : jarPaths) {
            URLClassLoader loader = newLoader(jar);
            try {
                for (Ai4jExtension extension : ServiceLoader.load(Ai4jExtension.class, loader)) {
                    extensions.add(extension);
                    loaders.put(extension, loader);
                }
            } catch (Throwable t) {
                closeQuietly(loader);
                throw new ExtensionException("failed to load extension jar: " + jar, t);
            }
        }
        return extensions;
    }

    /** Returns the dedicated classloader that loaded this extension, or null if unknown. */
    public ClassLoader classLoaderOf(Ai4jExtension extension) {
        return loaders.get(extension);
    }

    /** Closes the classloader that loaded this extension. Idempotent. */
    public void release(Ai4jExtension extension) {
        URLClassLoader loader = loaders.remove(extension);
        if (loader != null) {
            closeQuietly(loader);
        }
    }

    @Override
    public void close() {
        closed = true;
        for (URLClassLoader loader : allLoaders) {
            closeQuietly(loader);
        }
        loaders.clear();
    }

    private URLClassLoader newLoader(Path jar) {
        // Directories are accepted too: they let tests and dev setups stage
        // unpacked extension classes without packaging a jar.
        if (!Files.isRegularFile(jar) && !Files.isDirectory(jar)) {
            throw new ExtensionException("extension jar not found: " + jar);
        }
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent);
            allLoaders.add(loader);
            return loader;
        } catch (Exception e) {
            throw new ExtensionException("failed to create classloader for: " + jar, e);
        }
    }

    private static void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
            // best-effort release; nothing actionable on close failure
        }
    }
}
