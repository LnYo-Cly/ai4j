package io.github.lnyocly.ai4j;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

public class Ai4jActuatorEndpointTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AiConfigAutoConfiguration.class,
                    AiWebAutoConfiguration.class,
                    Ai4jActuatorAutoConfiguration.class
            ));

    @Test
    @SuppressWarnings("unchecked")
    public void exposesMaskedDiagnosticPayloadWhenActuatorPresent() {
        contextRunner
                .withPropertyValues(
                        "ai.openai.api-key=sk-super-secret-value",
                        "ai.anthropic.api-key=sk-ant-secret",
                        "ai.platforms[0].id=primary",
                        "ai.platforms[0].platform=openai",
                        "ai.platforms[0].api-key=sk-preset-secret"
                )
                .run(context -> {
                    Assert.assertTrue(context.containsBean("ai4jActuatorEndpoint"));
                    Ai4jActuatorEndpoint endpoint = context.getBean(Ai4jActuatorEndpoint.class);
                    Map<String, Object> payload = endpoint.ai4j();

                    Assert.assertEquals("UP", payload.get("status"));

                    Map<String, Object> services = (Map<String, Object>) payload.get("services");
                    Assert.assertEquals(Boolean.TRUE, services.get("defaultAiServiceAvailable"));
                    Assert.assertEquals(Integer.valueOf(1), services.get("platformPresetCount"));
                    List<String> configuredPlatforms = (List<String>) services.get("configuredPlatforms");
                    Assert.assertTrue(configuredPlatforms.contains("openai"));
                    Assert.assertTrue(configuredPlatforms.contains("anthropic"));

                    Map<String, Object> extensions = (Map<String, Object>) payload.get("extensions");
                    Assert.assertEquals(Boolean.TRUE, extensions.get("available"));
                    Assert.assertNotNull(extensions.get("snapshot"));

                    Map<String, Object> capabilities = (Map<String, Object>) payload.get("capabilities");
                    Assert.assertEquals(Boolean.TRUE, capabilities.get("webStreams"));

                    String serialized = payload.toString();
                    Assert.assertFalse(serialized.contains("sk-super-secret-value"));
                    Assert.assertFalse(serialized.contains("sk-ant-secret"));
                    Assert.assertFalse(serialized.contains("sk-preset-secret"));
                });
    }

    @Test
    public void backsOffWhenActuatorEndpointClassIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Endpoint.class))
                .run(context -> Assert.assertEquals(0, context.getBeansOfType(Ai4jActuatorEndpoint.class).size()));
    }
}
