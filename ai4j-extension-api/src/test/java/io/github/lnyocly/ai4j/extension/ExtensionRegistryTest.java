package io.github.lnyocly.ai4j.extension;

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

import java.util.Collections;
import java.util.List;

public class ExtensionRegistryTest {

    @Test
    public void shouldDiscoverWithoutEnablingOrExposingTools() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension());

        List<DiscoveredExtension> discovered = registry.list();

        Assert.assertEquals(1, discovered.size());
        Assert.assertEquals("weather-pack", discovered.get(0).getManifest().getId());
        Assert.assertFalse(discovered.get(0).isEnabled());
        Assert.assertTrue(registry.snapshot().getTools().isEmpty());
    }

    @Test
    public void shouldRequireToolAllowlistAfterEnable() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack");

        ExtensionRuntimeSnapshot withoutExpose = registry.snapshot();

        Assert.assertTrue(withoutExpose.getTools().isEmpty());
        Assert.assertTrue(withoutExpose.getToolExecutors().isEmpty());
        Assert.assertEquals(1, withoutExpose.getCommands().size());
        Assert.assertEquals(1, withoutExpose.getSkills().size());
        Assert.assertEquals(1, withoutExpose.getPrompts().size());
        Assert.assertEquals(1, withoutExpose.getGuardrails().size());

        ExtensionRuntimeSnapshot exposed = registry.exposeTool("weather.search").snapshot();

        Assert.assertEquals(1, exposed.getTools().size());
        Assert.assertEquals("weather.search", exposed.getTools().get(0).getName());
        Assert.assertEquals("ok:{}", execute(exposed.getToolExecutors().get("weather.search"), "{}"));
    }

    @Test
    public void shouldFilterNonToolResourcesWhenExplicitActivationIsRequired() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack")
                .requireExplicitResourceActivation()
                .allowCommand("weather")
                .allowSkill("weather-skill")
                .allowPrompt("weather-summary")
                .allowGuardrail("weather-guardrail");

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();

        Assert.assertTrue(registry.isExplicitResourceActivation());
        Assert.assertTrue(registry.getAllowedCommandIds().contains("weather"));
        Assert.assertTrue(registry.getAllowedSkillIds().contains("weather-skill"));
        Assert.assertTrue(registry.getAllowedPromptIds().contains("weather-summary"));
        Assert.assertTrue(registry.getAllowedGuardrailIds().contains("weather-guardrail"));
        Assert.assertEquals(1, snapshot.getCommands().size());
        Assert.assertEquals("weather", snapshot.getCommands().get(0).getName());
        Assert.assertEquals(1, snapshot.getSkills().size());
        Assert.assertEquals("weather-skill", snapshot.getSkills().get(0).getName());
        Assert.assertEquals(1, snapshot.getPrompts().size());
        Assert.assertEquals("weather-summary", snapshot.getPrompts().get(0).getName());
        Assert.assertEquals(1, snapshot.getGuardrails().size());
        Assert.assertEquals("weather-guardrail", snapshot.getGuardrails().get(0).name());
    }

    @Test
    public void shouldFailFastWhenAllowedResourceIsNotRegistered() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack")
                .allowPrompt("missing-prompt");

        try {
            registry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("prompt not registered by enabled extensions"));
        }
    }

    @Test
    public void shouldDescribeActivationPlanForEnabledAndAllowedResources() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension())
                .enable("weather-pack")
                .exposeTool("weather.search")
                .allowCommand("weather")
                .allowSkill("missing-skill")
                .allowPrompt("weather-summary")
                .allowGuardrail("weather-guardrail");

        ExtensionActivationPlan plan = registry.activationPlan("weather-pack");

        Assert.assertEquals("weather-pack", plan.getManifest().getId());
        Assert.assertTrue(plan.isEnabled());
        Assert.assertTrue(plan.isExplicitResourceActivation());
        Assert.assertTrue(find(plan.getTools(), "weather.search").isActive());
        Assert.assertTrue(find(plan.getCommands(), "weather").isActive());
        Assert.assertFalse(find(plan.getSkills(), "weather-skill").isActive());
        Assert.assertTrue(find(plan.getSkills(), "weather-skill").getReason().contains("not allowed"));
        Assert.assertFalse(find(plan.getSkills(), "missing-skill").isActive());
        Assert.assertTrue(find(plan.getSkills(), "missing-skill").getReason().contains("not registered"));
        Assert.assertTrue(find(plan.getPrompts(), "weather-summary").isActive());
        Assert.assertTrue(find(plan.getGuardrails(), "weather-guardrail").isActive());
    }

    @Test
    public void shouldInspectRuntimeWithoutEnablingExtensionOrExposingTools() {
        ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension());

        ExtensionInspectionSnapshot inspected = registry.inspectRuntime(" weather-pack ");

        Assert.assertEquals(1, inspected.getTools().size());
        Assert.assertEquals("weather.search", inspected.getTools().get(0).getName());
        Assert.assertEquals(1, inspected.getCommands().size());
        Assert.assertEquals("weather", inspected.getCommands().get(0).getName());
        Assert.assertEquals(1, inspected.getSkills().size());
        Assert.assertEquals("weather-skill", inspected.getSkills().get(0).getName());
        Assert.assertEquals(1, inspected.getPrompts().size());
        Assert.assertEquals("weather-summary", inspected.getPrompts().get(0).getName());
        Assert.assertEquals(1, inspected.getGuardrails().size());
        Assert.assertEquals("weather-guardrail", inspected.getGuardrails().get(0));
        Assert.assertTrue(registry.getEnabledIds().isEmpty());
        Assert.assertTrue(registry.snapshot().getTools().isEmpty());
    }

    @Test
    public void shouldRejectUnknownExtensionEnable() {
        try {
            ExtensionRegistry.of(new WeatherExtension()).enable("missing-pack");
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("extension not discovered"));
        }
    }

    @Test
    public void shouldRejectDuplicateExtensionIds() {
        try {
            ExtensionRegistry.of(new WeatherExtension(), new DuplicateWeatherExtension());
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("duplicate extension id"));
        }
    }

    @Test
    public void shouldRejectInvalidPublicIdsAndResourceNames() {
        assertInvalid(new Runnable() {
            public void run() {
                ExtensionManifest.builder()
                        .id("bad id")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }
        }, "extension id");
        assertInvalid(new Runnable() {
            public void run() {
                ExtensionToolSpec.builder()
                        .name("bad:tool")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }
        }, "tool name");
        assertInvalid(new Runnable() {
            public void run() {
                ExtensionCommandSpec.builder().name("/bad-command").build();
            }
        }, "command name");
        assertInvalid(new Runnable() {
            public void run() {
                ExtensionSkillResource.builder()
                        .name("bad skill")
                        .resourcePath("skills/bad/SKILL.md")
                        .build();
            }
        }, "skill name");
        assertInvalid(new Runnable() {
            public void run() {
                ExtensionPromptResource.builder()
                        .name("bad/prompt")
                        .resourcePath("prompts/bad.md")
                        .build();
            }
        }, "prompt name");
    }

    @Test
    public void shouldRejectInvalidGuardrailNameDuringRegistration() {
        try {
            ExtensionRegistry.of(new InvalidGuardrailNameExtension())
                    .enable("bad-guardrail")
                    .snapshot();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("guardrail name"));
        }
    }

    @Test
    public void shouldRejectRegistrationForUndeclaredCapability() {
        ExtensionRegistry registry = ExtensionRegistry.of(new BadCapabilityExtension()).enable("bad-capability");

        try {
            registry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("did not declare capability"));
        }
    }

    @Test
    public void shouldLoadFromCustomLoader() {
        ExtensionRegistry registry = ExtensionRegistry.discover(new ExtensionLoader() {
            public List<Ai4jExtension> load() {
                return Collections.<Ai4jExtension>singletonList(new WeatherExtension());
            }
        });

        Assert.assertEquals("weather-pack", registry.inspect("weather-pack").getId());
    }

    private static String execute(ExtensionToolExecutor executor, String arguments) {
        try {
            return executor.execute(new ExtensionToolCall("weather.search", arguments));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void assertInvalid(Runnable action, String messagePart) {
        try {
            action.run();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains(messagePart));
        }
    }

    private static ExtensionActivationItem find(List<ExtensionActivationItem> items, String name) {
        if (items != null) {
            for (ExtensionActivationItem item : items) {
                if (item != null && item.getName().equals(name)) {
                    return item;
                }
            }
        }
        Assert.fail("missing activation item: " + name);
        return null;
    }

    private static class WeatherExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("weather-pack")
                    .name("Weather Pack")
                    .version("1.0.0")
                    .vendor("tests")
                    .capability(ExtensionCapability.TOOL)
                    .capability(ExtensionCapability.COMMAND)
                    .capability(ExtensionCapability.SKILL)
                    .capability(ExtensionCapability.PROMPT)
                    .capability(ExtensionCapability.GUARDRAIL)
                    .permission("network:weather.example")
                    .configPrefix("ai4j.extensions.weather")
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
                            return "ok:" + call.getArguments();
                        }
                    });
            context.commands().register(ExtensionCommandSpec.builder()
                    .name("weather")
                    .description("Weather command")
                    .usage("/weather <city>")
                    .build(),
                    request -> "command:" + request.getArguments());
            context.skills().register(ExtensionSkillResource.builder()
                    .name("weather-skill")
                    .description("Weather workflow")
                    .resourcePath("skills/weather/SKILL.md")
                    .build());
            context.prompts().register(ExtensionPromptResource.builder()
                    .name("weather-summary")
                    .description("Weather summary prompt")
                    .resourcePath("prompts/weather-summary.md")
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

    private static class DuplicateWeatherExtension extends WeatherExtension {
    }

    private static class BadCapabilityExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("bad-capability")
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.commands().register(ExtensionCommandSpec.builder().name("bad").build(),
                    request -> "bad");
        }
    }

    private static class InvalidGuardrailNameExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("bad-guardrail")
                    .capability(ExtensionCapability.GUARDRAIL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.guardrails().register(new ExtensionGuardrail() {
                public String name() {
                    return "bad/guardrail";
                }

                public GuardrailDecision evaluate(GuardrailRequest request) {
                    return GuardrailDecision.allow();
                }
            });
        }
    }
}
