package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailDecision;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRequest;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

public class ExtensionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class);

    @Test
    public void shouldDiscoverClasspathExtensionsWithoutEnablingOrExposingTools() {
        contextRunner.run(context -> {
            ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
            List<DiscoveredExtension> discovered = registry.list();

            Assert.assertEquals(1, discovered.size());
            Assert.assertEquals("spring-weather-pack", discovered.get(0).getManifest().getId());
            Assert.assertFalse(discovered.get(0).isEnabled());

            ExtensionRuntimeSnapshot snapshot = context.getBean(ExtensionRuntimeSnapshot.class);
            Assert.assertTrue(snapshot.getTools().isEmpty());
            Assert.assertTrue(snapshot.getToolExecutors().isEmpty());
        });
    }

    @Test
    public void shouldEnableConfiguredExtensionsAndExposeAllowlistedTools() {
        contextRunner
                .withPropertyValues(
                        "ai.extensions.enabled[0]=spring-weather-pack",
                        "ai.extensions.tools.expose[0]=weather.search"
                )
                .run(context -> {
                    ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
                    ExtensionRuntimeSnapshot snapshot = context.getBean(ExtensionRuntimeSnapshot.class);

                    Assert.assertTrue(registry.getEnabledIds().contains("spring-weather-pack"));
                    Assert.assertTrue(registry.getExposedToolIds().contains("weather.search"));
                    Assert.assertEquals(1, snapshot.getTools().size());
                    Assert.assertEquals("weather.search", snapshot.getTools().get(0).getName());
                    Assert.assertEquals(1, snapshot.getCommands().size());
                    Assert.assertEquals(1, snapshot.getSkills().size());
                    Assert.assertEquals(1, snapshot.getPrompts().size());
                    Assert.assertEquals(1, snapshot.getGuardrails().size());
                    Assert.assertEquals("spring-weather:{}", execute(snapshot.getToolExecutors().get("weather.search"), "{}"));
                });
    }

    @Test
    public void shouldConfigureExplicitResourceActivationAllowlists() {
        contextRunner
                .withPropertyValues(
                        "ai.extensions.enabled[0]=spring-weather-pack",
                        "ai.extensions.explicit-resource-activation=true",
                        "ai.extensions.tools.expose[0]=weather.search",
                        "ai.extensions.commands.allow[0]=weather",
                        "ai.extensions.skills.allow[0]=weather-skill",
                        "ai.extensions.prompts.allow[0]=weather-prompt",
                        "ai.extensions.guardrails.allow[0]=weather-guardrail"
                )
                .run(context -> {
                    ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
                    ExtensionRuntimeSnapshot snapshot = context.getBean(ExtensionRuntimeSnapshot.class);

                    Assert.assertTrue(registry.isExplicitResourceActivation());
                    Assert.assertTrue(registry.getAllowedCommandIds().contains("weather"));
                    Assert.assertTrue(registry.getAllowedSkillIds().contains("weather-skill"));
                    Assert.assertTrue(registry.getAllowedPromptIds().contains("weather-prompt"));
                    Assert.assertTrue(registry.getAllowedGuardrailIds().contains("weather-guardrail"));
                    Assert.assertEquals(1, snapshot.getTools().size());
                    Assert.assertEquals(1, snapshot.getCommands().size());
                    Assert.assertEquals("weather", snapshot.getCommands().get(0).getName());
                    Assert.assertEquals(1, snapshot.getSkills().size());
                    Assert.assertEquals("weather-skill", snapshot.getSkills().get(0).getName());
                    Assert.assertEquals(1, snapshot.getPrompts().size());
                    Assert.assertEquals("weather-prompt", snapshot.getPrompts().get(0).getName());
                    Assert.assertEquals(1, snapshot.getGuardrails().size());
                    Assert.assertEquals("weather-guardrail", snapshot.getGuardrails().get(0).name());
                });
    }

    @Test
    public void shouldFailFastWhenConfiguredExtensionWasNotDiscovered() {
        contextRunner
                .withPropertyValues("ai.extensions.enabled[0]=missing-pack")
                .run(context -> Assert.assertNotNull(context.getStartupFailure()));
    }

    @Test
    public void shouldFailFastWhenToolIsExposedWithoutEnablingExtension() {
        contextRunner
                .withPropertyValues("ai.extensions.tools.expose[0]=weather.search")
                .run(context -> Assert.assertNotNull(context.getStartupFailure()));
    }

    @Test
    public void shouldFailFastWhenAllowedResourceIsNotRegistered() {
        contextRunner
                .withPropertyValues(
                        "ai.extensions.enabled[0]=spring-weather-pack",
                        "ai.extensions.commands.allow[0]=missing-command"
                )
                .run(context -> Assert.assertNotNull(context.getStartupFailure()));
    }

    private static String execute(ExtensionToolExecutor executor, String arguments) {
        try {
            return executor.execute(new ExtensionToolCall("weather.search", arguments));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    public static class SpringWeatherExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("spring-weather-pack")
                    .name("Spring Weather Pack")
                    .version("1.0.0")
                    .vendor("tests")
                    .capability(ExtensionCapability.TOOL)
                    .capability(ExtensionCapability.COMMAND)
                    .capability(ExtensionCapability.SKILL)
                    .capability(ExtensionCapability.PROMPT)
                    .capability(ExtensionCapability.GUARDRAIL)
                    .configPrefix("ai.extensions.weather")
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.tools().register(ExtensionToolSpec.builder()
                            .name("weather.search")
                            .description("Search weather")
                            .inputSchema("{\"type\":\"object\"}")
                            .build(),
                    new ExtensionToolExecutor() {
                        public String execute(ExtensionToolCall call) {
                            return "spring-weather:" + call.getArguments();
                        }
                    });
            context.commands().register(ExtensionCommandSpec.builder()
                            .name("weather")
                            .description("Weather command")
                            .usage("/weather <city>")
                            .build(),
                    request -> "spring-command:" + request.getArguments());
            context.skills().register(ExtensionSkillResource.builder()
                    .name("weather-skill")
                    .description("Weather skill")
                    .resourcePath("skills/weather/SKILL.md")
                    .build());
            context.prompts().register(ExtensionPromptResource.builder()
                    .name("weather-prompt")
                    .description("Weather prompt")
                    .resourcePath("prompts/weather.md")
                    .build());
            context.guardrails().register(new ExtensionGuardrail() {
                public String name() {
                    return "weather-guardrail";
                }

                public GuardrailDecision evaluate(GuardrailRequest request) {
                    return GuardrailDecision.allow();
                }
            });
        }
    }
}
