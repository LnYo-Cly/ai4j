package io.github.lnyocly.agent.e2b;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class E2BSandboxConfigTest {

    @Test
    public void fromEnvironmentShouldReadEnvAndDefaults() {
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("E2B_API_KEY", "env-key");
        env.put("E2B_DOMAIN", "e2b.test");
        env.put("E2B_TEMPLATE_ID", "custom-tpl");
        env.put("E2B_ENVD_PORT", "3000");
        env.put("E2B_TIMEOUT", "120");
        env.put("E2B_ACCESS_TOKEN", "tok-1");

        E2BSandboxConfig config = E2BSandboxConfig.fromEnvironment(
                SandboxSpec.builder().providerId("e2b").build(), env);

        Assert.assertEquals("e2b", config.getProviderId());
        Assert.assertEquals("env-key", config.getApiKey());
        Assert.assertEquals("e2b.test", config.getApiDomain());
        Assert.assertEquals("https://api.e2b.test", config.getApiUrl());
        Assert.assertEquals("custom-tpl", config.getTemplateId());
        Assert.assertEquals(Integer.valueOf(3000), config.getEnvdPort());
        Assert.assertEquals(Integer.valueOf(120), config.getTimeoutSeconds());
        Assert.assertEquals("tok-1", config.getEnvdAccessToken());
        Assert.assertEquals("https://3000-sid123.e2b.test", config.buildSandboxHost("sid123"));
        Assert.assertTrue("deleteOnClose defaults to true", config.isDeleteOnClose());
        Assert.assertTrue("useShellWrap defaults to true", config.isUseShellWrap());
    }

    @Test
    public void defaultsShouldUseCanonicalE2BHost() {
        E2BSandboxConfig config = E2BSandboxConfig.builder().apiKey("k").build();
        Assert.assertEquals(E2BSandboxConfig.DEFAULT_DOMAIN, config.getApiDomain());
        Assert.assertEquals("https://api." + E2BSandboxConfig.DEFAULT_DOMAIN, config.getApiUrl());
        Assert.assertEquals(Integer.valueOf(E2BSandboxConfig.DEFAULT_ENVD_PORT), config.getEnvdPort());
        Assert.assertEquals(Integer.valueOf(E2BSandboxConfig.DEFAULT_TIMEOUT_SECONDS), config.getTimeoutSeconds());
        Assert.assertEquals("https://49983-abc.e2b.app", config.buildSandboxHost("abc"));
    }

    @Test
    public void sandboxUrlOverrideShouldWinOverDerivedHost() {
        E2BSandboxConfig config = E2BSandboxConfig.builder()
                .apiKey("k")
                .sandboxUrl("https://my-proxy.example/box")
                .build();
        Assert.assertEquals("https://my-proxy.example/box", config.buildSandboxHost("abc"));
    }

    @Test
    public void specConfigShouldOverrideEnvAndReadImageAsTemplate() {
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("E2B_API_KEY", "env-key");
        env.put("E2B_TEMPLATE_ID", "env-tpl");

        SandboxSpec spec = SandboxSpec.builder()
                .providerId("e2b")
                .image("spec-image")
                .config("apiKey", "spec-key")
                .config("envdPort", Integer.valueOf(49983))
                .config("timeoutSeconds", Integer.valueOf(90))
                .config("useShellWrap", "false")
                .config("deleteOnClose", "false")
                .config("readTimeoutMillis", "5000")
                .build();

        E2BSandboxConfig config = E2BSandboxConfig.fromEnvironment(spec, env);

        Assert.assertEquals("spec-key", config.getApiKey());
        Assert.assertEquals("spec-image", config.getTemplateId());
        Assert.assertEquals(Integer.valueOf(90), config.getTimeoutSeconds());
        Assert.assertFalse(config.isUseShellWrap());
        Assert.assertFalse(config.isDeleteOnClose());
        Assert.assertEquals(5000L, config.getReadTimeoutMillis());
    }

    @Test
    public void missingApiKeyShouldBeNull() {
        E2BSandboxConfig config = E2BSandboxConfig.fromEnvironment(
                SandboxSpec.builder().providerId("e2b").build(), new LinkedHashMap<String, String>());
        Assert.assertNull(config.getApiKey());
    }
}
