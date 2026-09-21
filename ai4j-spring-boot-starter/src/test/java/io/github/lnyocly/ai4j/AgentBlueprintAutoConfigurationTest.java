package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collections;

public class AgentBlueprintAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class, AgentBlueprintAutoConfiguration.class)
            .withPropertyValues(
                    "ai.agent.enabled=true",
                    "ai.platforms[0].id=openai",
                    "ai.platforms[0].platform=openai",
                    "ai.platforms[0].api-key=test-key");

    @Test
    public void shouldAssembleAgentFromDeclaredBlueprint() {
        contextRunner
                .withPropertyValues(
                        "ai.agent.blueprints.reviewer=classpath:agents/test-reviewer.yaml",
                        "ai.agent.default-agent=reviewer")
                .run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    AgentRegistry registry = context.getBean(AgentRegistry.class);
                    Assert.assertTrue(registry.names().contains("reviewer"));
                    Assert.assertNotNull(registry.get("reviewer"));
                    Assert.assertSame(registry.get("reviewer"), context.getBean(Agent.class));
                });
    }

    @Test
    public void shouldStayInactiveWhenNotEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiConfigAutoConfiguration.class, AgentBlueprintAutoConfiguration.class)
                .run(context -> Assert.assertTrue(context.getBeansOfType(AgentRegistry.class).isEmpty()));
    }

    @Test
    public void shouldResolveSingleBlueprintAsRegistryDefault() {
        contextRunner
                .withPropertyValues("ai.agent.blueprints.reviewer=classpath:agents/test-reviewer.yaml")
                .run(context -> {
                    Assert.assertNull(context.getStartupFailure());
                    Assert.assertNotNull(context.getBean(AgentRegistry.class).getDefault());
                    Assert.assertTrue(context.getBeansOfType(Agent.class).isEmpty());
                });
    }

    @Test
    public void shouldFailFastWhenBlueprintResourceIsMissing() {
        contextRunner
                .withPropertyValues("ai.agent.blueprints.broken=classpath:agents/does-not-exist.yaml")
                .run(context -> Assert.assertNotNull(context.getStartupFailure()));
    }

    @Test
    public void shouldFailFastWhenBlueprintProviderIsNotConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiConfigAutoConfiguration.class, AgentBlueprintAutoConfiguration.class)
                .withPropertyValues(
                        "ai.agent.enabled=true",
                        "ai.agent.blueprints.reviewer=classpath:agents/test-reviewer.yaml")
                .run(context -> Assert.assertNotNull(context.getStartupFailure()));
    }

    @Test
    public void shouldBackOffWhenUserDefinesAgentRegistry() {
        AgentRegistry userRegistry = new AgentRegistry(Collections.<String, Agent>emptyMap(), null);
        contextRunner
                .withBean(AgentRegistry.class, () -> userRegistry)
                .withPropertyValues("ai.agent.blueprints.reviewer=classpath:agents/test-reviewer.yaml")
                .run(context -> Assert.assertSame(userRegistry, context.getBean(AgentRegistry.class)));
    }

    @Test
    public void shouldBackOffWhenUserDefinesBlueprintLoader() {
        AgentBlueprintLoader userLoader = new AgentBlueprintLoader();
        contextRunner
                .withBean(AgentBlueprintLoader.class, () -> userLoader)
                .run(context -> Assert.assertSame(userLoader, context.getBean(AgentBlueprintLoader.class)));
    }
}
