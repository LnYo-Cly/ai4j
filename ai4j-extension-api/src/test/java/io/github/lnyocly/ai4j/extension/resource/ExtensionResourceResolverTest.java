package io.github.lnyocly.ai4j.extension.resource;

import io.github.lnyocly.ai4j.extension.ExtensionException;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;

public class ExtensionResourceResolverTest {

    @Test
    public void strictReadShouldNotFallbackToThreadContextClassLoader() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        URLClassLoader empty = new URLClassLoader(new URL[0], null);
        Thread.currentThread().setContextClassLoader(ExtensionResourceResolverTest.class.getClassLoader());
        try {
            Assert.assertTrue(ExtensionResourceResolver.exists("skills/validator/SKILL.md", empty));
            Assert.assertFalse(ExtensionResourceResolver.existsStrict("skills/validator/SKILL.md", empty));

            try {
                ExtensionResourceResolver.readTextStrict("skills/validator/SKILL.md", empty);
                Assert.fail("expected ExtensionException");
            } catch (ExtensionException ex) {
                Assert.assertTrue(ex.getMessage().contains("extension resource not found"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            empty.close();
        }
    }
}
