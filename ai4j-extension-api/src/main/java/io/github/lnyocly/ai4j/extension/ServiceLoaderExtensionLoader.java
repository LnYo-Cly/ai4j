package io.github.lnyocly.ai4j.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class ServiceLoaderExtensionLoader implements ExtensionLoader {

    private final ClassLoader classLoader;

    public ServiceLoaderExtensionLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public ServiceLoaderExtensionLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public List<Ai4jExtension> load() {
        List<Ai4jExtension> extensions = new ArrayList<Ai4jExtension>();
        ServiceLoader<Ai4jExtension> loader = classLoader == null
                ? ServiceLoader.load(Ai4jExtension.class)
                : ServiceLoader.load(Ai4jExtension.class, classLoader);
        for (Ai4jExtension extension : loader) {
            extensions.add(extension);
        }
        return extensions;
    }
}
