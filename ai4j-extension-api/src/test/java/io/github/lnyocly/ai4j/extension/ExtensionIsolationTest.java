package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.fixture.IsolatedProbeExtension;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public class ExtensionIsolationTest {

    @Test
    public void shouldLoadEachExtensionInItsOwnClassLoader() throws Exception {
        Path dirA = stageExtensionDir();
        Path dirB = stageExtensionDir();
        ClassLoader parent = fixtureHidingParent();

        IsolatedExtensionLoader loader = new IsolatedExtensionLoader(Arrays.asList(dirA, dirB), parent);
        List<Ai4jExtension> extensions = loader.load();

        Assert.assertEquals(2, extensions.size());
        // Same fqcn, two different Class objects -> real per-extension isolation.
        Assert.assertEquals(extensions.get(0).getClass().getName(), extensions.get(1).getClass().getName());
        Assert.assertNotSame(extensions.get(0).getClass(), extensions.get(1).getClass());
        Assert.assertNotSame(loader.classLoaderOf(extensions.get(0)), loader.classLoaderOf(extensions.get(1)));
        loader.close();
    }

    @Test
    public void shouldKeepStaticStateIsolatedPerExtensionClassLoader() throws Exception {
        Path dirA = stageExtensionDir();
        Path dirB = stageExtensionDir();
        IsolatedExtensionLoader loader = new IsolatedExtensionLoader(Arrays.asList(dirA, dirB), fixtureHidingParent());
        List<Ai4jExtension> extensions = loader.load();

        ExtensionRegistry registry = ExtensionRegistry.of(extensions.get(0)).enable("isolated-probe");
        registry.snapshot();

        int applyCountA = ((Integer) extensions.get(0).getClass().getField("applyCount").get(null)).intValue();
        int applyCountB = ((Integer) extensions.get(1).getClass().getField("applyCount").get(null)).intValue();
        Assert.assertEquals(1, applyCountA);
        Assert.assertEquals(0, applyCountB);
        loader.close();
    }

    @Test
    public void shouldReleaseClassLoaderOnDisableAndStopExtension() throws Exception {
        Path dirA = stageExtensionDir();
        IsolatedExtensionLoader loader = new IsolatedExtensionLoader(Arrays.asList(dirA), fixtureHidingParent());
        ExtensionRegistry registry = ExtensionRegistry.discover(loader);
        Ai4jExtension extension = registry.list().get(0).getExtension();
        registry.enable("isolated-probe").exposeTool("probe.echo");
        Assert.assertEquals(1, registry.snapshot().getTools().size());
        Assert.assertEquals("plugin__isolated-probe__probe.echo", registry.snapshot().getTools().get(0).getName());

        registry.disable("isolated-probe");

        Assert.assertNull(loader.classLoaderOf(extension));
        Assert.assertTrue(registry.getEnabledIds().isEmpty());
        int stopCount = ((Integer) extension.getClass().getField("stopCount").get(null)).intValue();
        Assert.assertEquals(1, stopCount);
        loader.close();
    }

    @Test
    public void shouldRollbackRegistrationsAndReleaseLoaderWhenApplyFails() throws Exception {
        FailingExtension failing = new FailingExtension();
        ExtensionRegistry registry = ExtensionRegistry.of(new EchoExtension("pack-a"), failing)
                .enable("pack-a")
                .enable("failing-pack")
                .exposeTool("echo");
        try {
            registry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException expected) {
            Assert.assertTrue(expected.getMessage().contains("apply failed"));
        }
        Assert.assertEquals(1, failing.stopCount);

        // Transactional apply: the failed extension's partial registrations were
        // discarded with nextState. Removing it and retrying exposes only pack-a.
        registry.disable("failing-pack");
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        Assert.assertEquals(1, snapshot.getTools().size());
        Assert.assertEquals("plugin__pack-a__echo", snapshot.getTools().get(0).getName());
    }

    @Test
    public void shouldCoexistSameToolNameAcrossExtensions() {
        ExtensionRegistry registry = ExtensionRegistry.of(new EchoExtension("pack-a"), new EchoExtension("pack-b"))
                .enable("pack-a")
                .enable("pack-b")
                .exposeTool("plugin__pack-a__echo")
                .exposeTool("plugin__pack-b__echo");
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        Assert.assertEquals(2, snapshot.getTools().size());
        Assert.assertEquals("plugin__pack-a__echo", snapshot.getTools().get(0).getName());
        Assert.assertEquals("plugin__pack-b__echo", snapshot.getTools().get(1).getName());
    }

    @Test
    public void shouldResolveBareToolNameToUniqueNamespace() {
        ExtensionRegistry registry = ExtensionRegistry.of(new EchoExtension("pack-a"))
                .enable("pack-a")
                .exposeTool("echo");
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        Assert.assertEquals(1, snapshot.getTools().size());
        Assert.assertEquals("plugin__pack-a__echo", snapshot.getTools().get(0).getName());
    }

    @Test
    public void shouldRejectAmbiguousBareToolName() {
        ExtensionRegistry registry = ExtensionRegistry.of(new EchoExtension("pack-a"), new EchoExtension("pack-b"))
                .enable("pack-a")
                .enable("pack-b")
                .exposeTool("echo");
        try {
            registry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException expected) {
            Assert.assertTrue(expected.getMessage().contains("ambiguous tool id"));
        }
    }

    private static Path stageExtensionDir() throws Exception {
        Path dir = Files.createTempDirectory("ai4j-ext-");
        Path packageDir = dir.resolve("io/github/lnyocly/ai4j/extension/fixture");
        Files.createDirectories(packageDir);
        URL self = IsolatedProbeExtension.class.getResource("IsolatedProbeExtension.class");
        Path classesRoot = Paths.get(self.toURI()).getParent();
        try (java.util.stream.Stream<Path> stream = Files.list(classesRoot)) {
            for (Path classFile : stream.filter(p -> p.getFileName().toString().startsWith("IsolatedProbeExtension")).collect(java.util.stream.Collectors.toList())) {
                Files.copy(classFile, packageDir.resolve(classFile.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path services = dir.resolve("META-INF/services");
        Files.createDirectories(services);
        Files.write(services.resolve("io.github.lnyocly.ai4j.extension.Ai4jExtension"),
                "io.github.lnyocly.ai4j.extension.fixture.IsolatedProbeExtension".getBytes("UTF-8"));
        return dir;
    }

    /**
     * Parent that shares every class except the fixture package: the fixture is
     * hidden so each child loader must define its own copy, while the SPI
     * contract ({@link Ai4jExtension} and friends) still resolves to the same
     * Class objects the host uses.
     */
    private static ClassLoader fixtureHidingParent() {
        return new ClassLoader(ExtensionIsolationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("io.github.lnyocly.ai4j.extension.fixture.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }

            @Override
            public java.util.Enumeration<URL> getResources(String name) throws IOException {
                if ("META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension".equals(name)) {
                    return java.util.Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };
    }

    private static class EchoExtension implements Ai4jExtension {
        private final String id;

        EchoExtension(String id) {
            this.id = id;
        }

        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id(id)
                    .name(id)
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.tools().register(
                    ExtensionToolSpec.builder().name("echo").description("echo").inputSchema("{}").build(),
                    new ExtensionToolExecutor() {
                        public String execute(ExtensionToolCall call) {
                            return "echo";
                        }
                    });
        }
    }

    private static class FailingExtension implements Ai4jExtension {
        private int stopCount;

        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("failing-pack")
                    .name("Failing Pack")
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            // Partial registration before failing — must be rolled back.
            context.tools().register(
                    ExtensionToolSpec.builder().name("half.registered").description("x").inputSchema("{}").build(),
                    new ExtensionToolExecutor() {
                        public String execute(ExtensionToolCall call) {
                            return "x";
                        }
                    });
            throw new ExtensionException("apply failed");
        }

        public void onStop() {
            stopCount++;
        }
    }
}
