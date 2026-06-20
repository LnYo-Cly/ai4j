package io.github.lnyocly.ai4j.cli;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CliDistributionLayoutTest {

    @Test
    public void shouldKeepDistributionSourceLayoutStable() throws Exception {
        Path distRoot = resolveCliModuleRoot().resolve("src/main/dist");

        assertFile(distRoot.resolve("bin/ai4j"));
        assertFile(distRoot.resolve("bin/ai4j.cmd"));
        assertFile(distRoot.resolve("conf/providers.example.json"));
        assertFile(distRoot.resolve("conf/workspace.example.json"));
        assertFile(distRoot.resolve("README.md"));

        String unixLauncher = read(distRoot.resolve("bin/ai4j"));
        Assert.assertTrue(unixLauncher.contains("AI4J_JAVA"));
        Assert.assertTrue(unixLauncher.contains("AI4J_JAVA_OPTS"));
        Assert.assertTrue(unixLauncher.contains("AI4J_CLI_JAR"));
        Assert.assertTrue(unixLauncher.contains("-jar"));
        Assert.assertTrue(unixLauncher.contains("@project.version@"));

        String windowsLauncher = read(distRoot.resolve("bin/ai4j.cmd"));
        Assert.assertTrue(windowsLauncher.contains("AI4J_JAVA"));
        Assert.assertTrue(windowsLauncher.contains("AI4J_JAVA_OPTS"));
        Assert.assertTrue(windowsLauncher.contains("AI4J_CLI_JAR"));
        Assert.assertTrue(windowsLauncher.contains("-jar"));
        Assert.assertTrue(windowsLauncher.contains("@project.version@"));
    }

    @Test
    public void shouldKeepDistributionExamplesSecretFreeAndParseable() throws Exception {
        Path distRoot = resolveCliModuleRoot().resolve("src/main/dist");

        JSONObject providers = JSON.parseObject(read(distRoot.resolve("conf/providers.example.json")));
        Assert.assertEquals("openai-compatible", providers.getString("defaultProfile"));
        JSONObject profiles = providers.getJSONObject("profiles");
        Assert.assertNotNull(profiles.getJSONObject("openai-compatible"));
        Assert.assertNotNull(profiles.getJSONObject("private-compatible"));
        Assert.assertEquals("${OPENAI_API_KEY}", profiles.getJSONObject("openai-compatible").getString("apiKey"));
        Assert.assertEquals("${YOUR_PROVIDER_API_KEY}", profiles.getJSONObject("private-compatible").getString("apiKey"));

        JSONObject workspace = JSON.parseObject(read(distRoot.resolve("conf/workspace.example.json")));
        Assert.assertEquals("openai-compatible", workspace.getString("activeProfile"));
        Assert.assertTrue(workspace.getJSONArray("enabledMcpServers").isEmpty());

        String all = read(distRoot.resolve("conf/providers.example.json"))
                + read(distRoot.resolve("conf/workspace.example.json"))
                + read(distRoot.resolve("README.md"));
        Assert.assertFalse("distribution examples must not contain OpenAI-style provider token literals",
                java.util.regex.Pattern.compile("sk-[A-Za-z0-9_-]{20,}").matcher(all).find());
        Assert.assertFalse("distribution examples must not contain GLM-style provider token literals",
                java.util.regex.Pattern.compile("[0-9a-fA-F]{32}\\.[A-Za-z0-9_-]{8,}").matcher(all).find());
    }

    @Test
    public void shouldPackageFilteredDistributionResources() throws Exception {
        Path moduleRoot = resolveCliModuleRoot();
        String descriptor = read(moduleRoot.resolve("src/assembly/dist.xml"));
        Assert.assertTrue(descriptor.contains("${project.build.directory}/filtered-dist/bin"));
        Assert.assertTrue(descriptor.contains("${project.build.directory}/filtered-dist/conf"));
        Assert.assertTrue(descriptor.contains("ai4j-cli-${project.version}-jar-with-dependencies.jar"));
        Assert.assertTrue(descriptor.contains("<format>zip</format>"));
        Assert.assertTrue(descriptor.contains("<format>tar.gz</format>"));
    }

    private static Path resolveCliModuleRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/dist"))) {
            return current;
        }
        if (Files.exists(current.resolve("ai4j-cli/src/main/dist"))) {
            return current.resolve("ai4j-cli");
        }
        throw new IllegalStateException("Cannot resolve ai4j-cli module root from " + current);
    }

    private static void assertFile(Path path) {
        Assert.assertTrue("Expected file: " + path, Files.isRegularFile(path));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
