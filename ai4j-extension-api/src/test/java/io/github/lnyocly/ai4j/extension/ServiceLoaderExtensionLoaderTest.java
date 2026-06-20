package io.github.lnyocly.ai4j.extension;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ServiceLoaderExtensionLoaderTest {

    @Test
    public void shouldLoadExtensionsFromServiceLoader() {
        ServiceLoaderExtensionLoader loader = new ServiceLoaderExtensionLoader(
                Thread.currentThread().getContextClassLoader());

        List<Ai4jExtension> extensions = loader.load();

        Assert.assertEquals(1, extensions.size());
        Assert.assertEquals("service-loader-pack", extensions.get(0).manifest().getId());
    }
}
