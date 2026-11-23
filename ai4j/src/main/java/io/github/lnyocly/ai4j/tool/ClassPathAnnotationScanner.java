package io.github.lnyocly.ai4j.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lightweight pure-JDK classpath scanner for annotated classes without third-party reflection dependencies.
 */
final class ClassPathAnnotationScanner {

    private static final Logger log = LoggerFactory.getLogger(ClassPathAnnotationScanner.class);
    private static final String CLASS_SUFFIX = ".class";

    private ClassPathAnnotationScanner() {
    }

    static Set<Class<?>> findTypesAnnotatedWith(String basePackage, Class<? extends Annotation> annotationClass) {
        Set<Class<?>> matched = new LinkedHashSet<Class<?>>();
        if (basePackage == null || basePackage.trim().isEmpty() || annotationClass == null) {
            return matched;
        }
        String packagePath = basePackage.replace('.', '/');
        ClassLoader[] loaders = new ClassLoader[] {
                Thread.currentThread().getContextClassLoader(),
                ClassPathAnnotationScanner.class.getClassLoader()
        };
        Set<String> candidateNames = new LinkedHashSet<String>();
        Set<ClassLoader> visitedLoaders = new LinkedHashSet<ClassLoader>();

        for (ClassLoader loader : loaders) {
            if (loader == null || !visitedLoaders.add(loader)) {
                continue;
            }
            try {
                Enumeration<URL> resources = loader.getResources(packagePath);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    scanUrl(url, basePackage, packagePath, candidateNames);
                }
            } catch (IOException e) {
                log.debug("Failed to enumerate resources for package {}", basePackage, e);
            }
        }

        ClassLoader activeLoader = loaders[0] != null ? loaders[0] : loaders[1];
        for (String className : candidateNames) {
            try {
                Class<?> clazz = Class.forName(className, false, activeLoader);
                if (clazz.isAnnotationPresent(annotationClass)) {
                    matched.add(clazz);
                }
            } catch (ClassNotFoundException ignored) {
                // Skip unreachable class entries.
            } catch (LinkageError ignored) {
                // Skip classes with missing optional transitive dependencies.
            } catch (SecurityException ignored) {
                // Skip restricted classes.
            }
        }
        return matched;
    }

    private static void scanUrl(URL url, String basePackage, String packagePath, Set<String> candidateNames) {
        if (url == null) {
            return;
        }
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            File rootDir = toFile(url);
            if (rootDir != null && rootDir.isDirectory()) {
                scanDirectory(rootDir, basePackage, candidateNames);
            }
        } else if ("jar".equals(protocol)) {
            scanJar(url, packagePath, candidateNames);
        }
    }

    private static File toFile(URL url) {
        try {
            URI uri = url.toURI();
            return new File(uri);
        } catch (URISyntaxException e) {
            return new File(url.getPath());
        } catch (IllegalArgumentException e) {
            return new File(url.getPath());
        }
    }

    private static void scanDirectory(File dir, String currentPackage, Set<String> candidateNames) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                scanDirectory(child, currentPackage + "." + name, candidateNames);
            } else if (name.endsWith(CLASS_SUFFIX)
                    && !"package-info.class".equals(name)
                    && !"module-info.class".equals(name)) {
                String simpleName = name.substring(0, name.length() - CLASS_SUFFIX.length());
                candidateNames.add(currentPackage + "." + simpleName);
            }
        }
    }

    private static void scanJar(URL url, String packagePath, Set<String> candidateNames) {
        String prefix = packagePath.endsWith("/") ? packagePath : packagePath + "/";
        try {
            URLConnection conn = url.openConnection();
            if (!(conn instanceof JarURLConnection)) {
                return;
            }
            conn.setUseCaches(false);
            JarURLConnection jarConn = (JarURLConnection) conn;
            try (JarFile jarFile = jarConn.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = entry.getName();
                    if (entryName.startsWith(prefix)
                            && entryName.endsWith(CLASS_SUFFIX)
                            && !entryName.endsWith("/package-info.class")
                            && !entryName.endsWith("/module-info.class")) {
                        String className = entryName
                                .substring(0, entryName.length() - CLASS_SUFFIX.length())
                                .replace('/', '.');
                        candidateNames.add(className);
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to scan jar resource {}", url, e);
        }
    }
}
