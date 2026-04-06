package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.agentflow.AgentFlow;
import io.github.lnyocly.ai4j.agentflow.AgentFlowType;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class AgentFlowAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class);

    @Test
    public void test_agent_flow_registry_is_created() {
        contextRunner
                .withPropertyValues(
                        "ai.agentflow.enabled=true",
                        "ai.agentflow.profiles.dify.type=DIFY",
                        "ai.agentflow.profiles.dify.base-url=https://api.dify.ai",
                        "ai.agentflow.profiles.dify.api-key=app-xxx"
                )
                .run(context -> {
                    Assert.assertTrue(context.containsBean("agentFlowRegistry"));
                    Assert.assertFalse(context.containsBean("agentFlow"));

                    AgentFlowRegistry registry = context.getBean(AgentFlowRegistry.class);
                    Assert.assertTrue(registry.contains("dify"));
                    Assert.assertEquals(AgentFlowType.DIFY, registry.get("dify").getConfig().getType());
                    Assert.assertEquals("https://api.dify.ai", registry.get("dify").getConfig().getBaseUrl());
                    Assert.assertEquals(AgentFlowType.DIFY, registry.getDefault().getConfig().getType());
                });
    }

    @Test
    public void test_default_agent_flow_bean_uses_default_name() {
        contextRunner
                .withPropertyValues(
                        "ai.agentflow.enabled=true",
                        "ai.agentflow.default-name=coze",
                        "ai.agentflow.profiles.dify.type=DIFY",
                        "ai.agentflow.profiles.dify.base-url=https://api.dify.ai",
                        "ai.agentflow.profiles.dify.api-key=app-xxx",
                        "ai.agentflow.profiles.coze.type=COZE",
                        "ai.agentflow.profiles.coze.base-url=https://api.coze.com",
                        "ai.agentflow.profiles.coze.api-key=pat-xxx",
                        "ai.agentflow.profiles.coze.bot-id=bot-123"
                )
                .run(context -> {
                    Assert.assertTrue(context.containsBean("agentFlow"));

                    AgentFlow agentFlow = context.getBean(AgentFlow.class);
                    Assert.assertEquals(AgentFlowType.COZE, agentFlow.getConfig().getType());
                    Assert.assertEquals("bot-123", agentFlow.getConfig().getBotId());

                    AgentFlowRegistry registry = context.getBean(AgentFlowRegistry.class);
                    Assert.assertEquals(AgentFlowType.COZE, registry.getDefault().getConfig().getType());
                });
    }
}
