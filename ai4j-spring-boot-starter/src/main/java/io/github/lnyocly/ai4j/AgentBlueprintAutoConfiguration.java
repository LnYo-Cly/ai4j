package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactory;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryContext;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.extension.ExtensionAgentTools;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistration;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Assembles injectable {@link Agent} beans from Agent Blueprint YAML files
 * declared under {@code ai.agent.blueprints.<name>}. Host dependencies
 * (model client, tools, memory, permissions, session store, event publisher)
 * are resolved from the Spring context; the blueprint only describes the agent.
 */
@Configuration
@ConditionalOnClass(AgentFactory.class)
@AutoConfigureAfter(AiConfigAutoConfiguration.class)
@EnableConfigurationProperties(AiAgentProperties.class)
@ConditionalOnProperty(prefix = "ai.agent", name = "enabled", havingValue = "true")
public class AgentBlueprintAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentBlueprintLoader agentBlueprintLoader() {
        return new AgentBlueprintLoader();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentFactory agentFactory() {
        return new AgentFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry(AgentBlueprintLoader agentBlueprintLoader,
                                       AgentFactory agentFactory,
                                       AiServiceRegistry aiServiceRegistry,
                                       AiAgentProperties aiAgentProperties,
                                       ResourceLoader resourceLoader,
                                       ObjectProvider<ExtensionRuntimeSnapshot> snapshotProvider,
                                       ObjectProvider<ExtensionAgentTools> extensionAgentToolsProvider,
                                       ObjectProvider<AgentToolRegistry> toolRegistryProvider,
                                       ObjectProvider<ToolExecutor> toolExecutorProvider,
                                       ObjectProvider<AgentOptions> optionsProvider,
                                       ObjectProvider<Supplier<AgentMemory>> memorySupplierProvider,
                                       ObjectProvider<AgentPermissionPolicy> permissionPolicyProvider,
                                       ObjectProvider<AgentExecutionEnvironment> environmentProvider,
                                       ObjectProvider<ContextProjector> contextProjectorProvider,
                                       ObjectProvider<ContextBudget> contextBudgetProvider,
                                       ObjectProvider<AgentEventPublisher> eventPublisherProvider,
                                       ObjectProvider<AgentSessionStore> sessionStoreProvider) {
        Map<String, Agent> agents = new LinkedHashMap<String, Agent>();
        for (Map.Entry<String, String> entry : aiAgentProperties.getBlueprints().entrySet()) {
            agents.put(entry.getKey(), buildAgent(entry.getKey(), entry.getValue(),
                    agentBlueprintLoader, agentFactory, aiServiceRegistry, aiAgentProperties, resourceLoader,
                    snapshotProvider, extensionAgentToolsProvider, toolRegistryProvider, toolExecutorProvider,
                    optionsProvider, memorySupplierProvider, permissionPolicyProvider, environmentProvider,
                    contextProjectorProvider, contextBudgetProvider, eventPublisherProvider, sessionStoreProvider));
        }
        return new AgentRegistry(agents, aiAgentProperties.getDefaultAgent());
    }

    @Bean
    @ConditionalOnBean(AgentRegistry.class)
    @ConditionalOnProperty(prefix = "ai.agent", name = "default-agent")
    @ConditionalOnMissingBean(Agent.class)
    public Agent agent(AgentRegistry agentRegistry) {
        return agentRegistry.getDefault();
    }

    private Agent buildAgent(String name,
                             String location,
                             AgentBlueprintLoader loader,
                             AgentFactory agentFactory,
                             AiServiceRegistry aiServiceRegistry,
                             AiAgentProperties properties,
                             ResourceLoader resourceLoader,
                             ObjectProvider<ExtensionRuntimeSnapshot> snapshotProvider,
                             ObjectProvider<ExtensionAgentTools> extensionAgentToolsProvider,
                             ObjectProvider<AgentToolRegistry> toolRegistryProvider,
                             ObjectProvider<ToolExecutor> toolExecutorProvider,
                             ObjectProvider<AgentOptions> optionsProvider,
                             ObjectProvider<Supplier<AgentMemory>> memorySupplierProvider,
                             ObjectProvider<AgentPermissionPolicy> permissionPolicyProvider,
                             ObjectProvider<AgentExecutionEnvironment> environmentProvider,
                             ObjectProvider<ContextProjector> contextProjectorProvider,
                             ObjectProvider<ContextBudget> contextBudgetProvider,
                             ObjectProvider<AgentEventPublisher> eventPublisherProvider,
                             ObjectProvider<AgentSessionStore> sessionStoreProvider) {
        AgentBlueprint blueprint = loadBlueprint(name, location, loader, resourceLoader);
        AgentFactoryContext.Builder contextBuilder = AgentFactoryContext.builder()
                .modelClient(resolveModelClient(name, blueprint, aiServiceRegistry, properties))
                .allowSandboxDeclaration(properties.isAllowSandboxDeclaration());
        applyIfPresent(contextBuilder, toolRegistryProvider, toolExecutorProvider, optionsProvider,
                memorySupplierProvider, permissionPolicyProvider, environmentProvider, contextProjectorProvider,
                contextBudgetProvider, eventPublisherProvider, sessionStoreProvider);
        AgentBuilder agentBuilder = agentFactory.builder(blueprint, contextBuilder.build());
        ExtensionAgentTools extensionAgentTools = extensionAgentToolsProvider.getIfAvailable();
        if (extensionAgentTools == null) {
            ExtensionRuntimeSnapshot snapshot = snapshotProvider.getIfAvailable();
            extensionAgentTools = snapshot == null ? null : ExtensionAgentTools.from(snapshot);
        }
        if (extensionAgentTools != null) {
            agentBuilder.extensions(extensionAgentTools);
        }
        return agentBuilder.build();
    }

    private AgentBlueprint loadBlueprint(String name, String location, AgentBlueprintLoader loader, ResourceLoader resourceLoader) {
        if (location == null || location.trim().length() == 0) {
            throw new IllegalStateException("ai.agent.blueprints." + name + " must declare a blueprint resource location.");
        }
        Resource resource = resourceLoader.getResource(location.trim());
        if (!resource.exists()) {
            throw new IllegalStateException("ai.agent.blueprints." + name + " blueprint not found: " + location);
        }
        try {
            InputStream inputStream = resource.getInputStream();
            try {
                return loader.load(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("ai.agent.blueprints." + name + " unreadable: " + location, ex);
        }
    }

    private void applyIfPresent(AgentFactoryContext.Builder contextBuilder,
                                ObjectProvider<AgentToolRegistry> toolRegistryProvider,
                                ObjectProvider<ToolExecutor> toolExecutorProvider,
                                ObjectProvider<AgentOptions> optionsProvider,
                                ObjectProvider<Supplier<AgentMemory>> memorySupplierProvider,
                                ObjectProvider<AgentPermissionPolicy> permissionPolicyProvider,
                                ObjectProvider<AgentExecutionEnvironment> environmentProvider,
                                ObjectProvider<ContextProjector> contextProjectorProvider,
                                ObjectProvider<ContextBudget> contextBudgetProvider,
                                ObjectProvider<AgentEventPublisher> eventPublisherProvider,
                                ObjectProvider<AgentSessionStore> sessionStoreProvider) {
        AgentToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        if (toolRegistry != null) {
            contextBuilder.toolRegistry(toolRegistry);
        }
        ToolExecutor toolExecutor = toolExecutorProvider.getIfAvailable();
        if (toolExecutor != null) {
            contextBuilder.toolExecutor(toolExecutor);
        }
        AgentOptions options = optionsProvider.getIfAvailable();
        if (options != null) {
            contextBuilder.baseOptions(options);
        }
        Supplier<AgentMemory> memorySupplier = memorySupplierProvider.getIfAvailable();
        if (memorySupplier != null) {
            contextBuilder.memorySupplier(memorySupplier);
        }
        AgentPermissionPolicy permissionPolicy = permissionPolicyProvider.getIfAvailable();
        if (permissionPolicy != null) {
            contextBuilder.permissionPolicy(permissionPolicy);
        }
        AgentExecutionEnvironment environment = environmentProvider.getIfAvailable();
        if (environment != null) {
            contextBuilder.executionEnvironment(environment);
        }
        ContextProjector contextProjector = contextProjectorProvider.getIfAvailable();
        if (contextProjector != null) {
            contextBuilder.contextProjector(contextProjector);
        }
        ContextBudget contextBudget = contextBudgetProvider.getIfAvailable();
        if (contextBudget != null) {
            contextBuilder.contextBudget(contextBudget);
        }
        AgentEventPublisher eventPublisher = eventPublisherProvider.getIfAvailable();
        if (eventPublisher != null) {
            contextBuilder.eventPublisher(eventPublisher);
        }
        AgentSessionStore sessionStore = sessionStoreProvider.getIfAvailable();
        if (sessionStore != null) {
            contextBuilder.sessionStore(sessionStore);
        }
    }

    private AgentModelClient resolveModelClient(String name,
                                                AgentBlueprint blueprint,
                                                AiServiceRegistry aiServiceRegistry,
                                                AiAgentProperties properties) {
        String provider = blueprint.getModel() == null ? null : trimToNull(blueprint.getModel().getProvider());
        if (provider == null) {
            throw new IllegalStateException("ai.agent.blueprints." + name
                    + ": model.provider is required and must match an ai.platforms[].id.");
        }
        AiServiceRegistration registration = aiServiceRegistry.find(provider);
        if (registration == null) {
            throw new IllegalStateException("ai.agent.blueprints." + name
                    + ": unknown model.provider '" + provider
                    + "'; configure a matching ai.platforms[].id. Known ids: " + aiServiceRegistry.ids());
        }
        AiService aiService = registration.getAiService();
        PlatformType platformType = registration.getPlatformType();
        String protocol = resolveProtocol(blueprint, properties, platformType);
        if ("responses".equals(protocol)) {
            return new ResponsesModelClient(aiService.getResponsesService(platformType));
        }
        if ("messages".equals(protocol)) {
            return new MessagesModelClient(aiService.getMessagesService(platformType));
        }
        if ("chat".equals(protocol)) {
            return new ChatModelClient(aiService.getChatService(platformType));
        }
        throw new IllegalStateException("ai.agent.blueprints." + name
                + ": unsupported protocol '" + protocol + "' (expected chat, responses or messages).");
    }

    private String resolveProtocol(AgentBlueprint blueprint, AiAgentProperties properties, PlatformType platformType) {
        if (blueprint.getModel() != null && blueprint.getModel().getOptions() != null) {
            Object declared = blueprint.getModel().getOptions().get("protocol");
            if (declared != null && String.valueOf(declared).trim().length() > 0) {
                return String.valueOf(declared).trim().toLowerCase(Locale.ROOT);
            }
        }
        String configured = trimToNull(properties.getProtocol());
        if (configured != null) {
            return configured.toLowerCase(Locale.ROOT);
        }
        return platformType == PlatformType.ANTHROPIC ? "messages" : "chat";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }
}
