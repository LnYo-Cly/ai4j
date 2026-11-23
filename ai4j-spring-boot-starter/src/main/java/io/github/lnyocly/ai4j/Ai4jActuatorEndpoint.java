package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistration;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Boot 2/3/4-compatible diagnostic endpoint exposed at {@code /actuator/ai4j}.
 * Reports registered services, non-secret platform readiness, Cordis extension runtime state,
 * and optional module availability without exposing credentials.
 */
@Endpoint(id = "ai4j")
public class Ai4jActuatorEndpoint {

    private static final String DEFAULT_OLLAMA_HOST = "http://localhost:11434/";

    private final ObjectProvider<AiService> aiServiceProvider;
    private final ObjectProvider<AiServiceRegistry> aiServiceRegistryProvider;
    private final ObjectProvider<Configuration> configurationProvider;
    private final ObjectProvider<ExtensionRegistry> extensionRegistryProvider;
    private final ObjectProvider<ExtensionRuntimeSnapshot> extensionSnapshotProvider;
    private final ObjectProvider<AiConfigProperties> aiConfigPropertiesProvider;

    public Ai4jActuatorEndpoint(ObjectProvider<AiService> aiServiceProvider,
                                ObjectProvider<AiServiceRegistry> aiServiceRegistryProvider,
                                ObjectProvider<Configuration> configurationProvider,
                                ObjectProvider<ExtensionRegistry> extensionRegistryProvider,
                                ObjectProvider<ExtensionRuntimeSnapshot> extensionSnapshotProvider,
                                ObjectProvider<AiConfigProperties> aiConfigPropertiesProvider) {
        this.aiServiceProvider = aiServiceProvider;
        this.aiServiceRegistryProvider = aiServiceRegistryProvider;
        this.configurationProvider = configurationProvider;
        this.extensionRegistryProvider = extensionRegistryProvider;
        this.extensionSnapshotProvider = extensionSnapshotProvider;
        this.aiConfigPropertiesProvider = aiConfigPropertiesProvider;
    }

    @ReadOperation
    public Map<String, Object> ai4j() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("status", "UP");
        payload.put("services", buildServicesSection());
        payload.put("extensions", buildExtensionsSection());
        payload.put("capabilities", buildCapabilitiesSection());
        return payload;
    }

    private Map<String, Object> buildServicesSection() {
        Map<String, Object> services = new LinkedHashMap<String, Object>();
        AiService aiService = aiServiceProvider == null ? null : aiServiceProvider.getIfAvailable();
        services.put("defaultAiServiceAvailable", Boolean.valueOf(aiService != null));

        List<String> registryIds = new ArrayList<String>();
        List<Map<String, Object>> registrations = new ArrayList<Map<String, Object>>();
        AiServiceRegistry registry = aiServiceRegistryProvider == null ? null : aiServiceRegistryProvider.getIfAvailable();
        if (registry != null && registry.ids() != null) {
            registryIds.addAll(registry.ids());
            Collections.sort(registryIds);
            for (String id : registryIds) {
                AiServiceRegistration registration = registry.find(id);
                if (registration == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", registration.getId());
                item.put("platform", registration.getPlatformType() == null ? null : registration.getPlatformType().getPlatform());
                registrations.add(item);
            }
        }
        services.put("registryIds", registryIds);
        services.put("registrations", registrations);
        services.put("configuredPlatforms", collectConfiguredPlatforms(aiService));

        AiConfigProperties multiPlatformProps = aiConfigPropertiesProvider == null
                ? null
                : aiConfigPropertiesProvider.getIfAvailable();
        int configuredPlatformEntries = multiPlatformProps == null || multiPlatformProps.getPlatforms() == null
                ? 0
                : multiPlatformProps.getPlatforms().size();
        services.put("platformPresetCount", Integer.valueOf(configuredPlatformEntries));
        return services;
    }

    private List<String> collectConfiguredPlatforms(AiService aiService) {
        Configuration configuration = configurationProvider == null ? null : configurationProvider.getIfAvailable();
        if (configuration == null && aiService != null) {
            configuration = aiService.getConfiguration();
        }
        if (configuration == null) {
            return Collections.emptyList();
        }
        List<String> configured = new ArrayList<String>();
        addIfHasText(configured, "openai", configuration.getOpenAiConfig() == null ? null : configuration.getOpenAiConfig().getApiKey());
        addIfHasText(configured, "anthropic", configuration.getAnthropicConfig() == null ? null : configuration.getAnthropicConfig().getApiKey());
        addIfHasText(configured, "zhipu", configuration.getZhipuConfig() == null ? null : configuration.getZhipuConfig().getApiKey());
        addIfHasText(configured, "deepseek", configuration.getDeepSeekConfig() == null ? null : configuration.getDeepSeekConfig().getApiKey());
        addIfHasText(configured, "moonshot", configuration.getMoonshotConfig() == null ? null : configuration.getMoonshotConfig().getApiKey());
        addIfHasText(configured, "hunyuan", configuration.getHunyuanConfig() == null ? null : configuration.getHunyuanConfig().getApiKey());
        addIfHasText(configured, "lingyi", configuration.getLingyiConfig() == null ? null : configuration.getLingyiConfig().getApiKey());
        addIfOllamaConfigured(configured, configuration.getOllamaConfig());
        addIfHasText(configured, "minimax", configuration.getMinimaxConfig() == null ? null : configuration.getMinimaxConfig().getApiKey());
        addIfHasText(configured, "baichuan", configuration.getBaichuanConfig() == null ? null : configuration.getBaichuanConfig().getApiKey());
        addIfHasText(configured, "dashscope", configuration.getDashScopeConfig() == null ? null : configuration.getDashScopeConfig().getApiKey());
        addIfHasText(configured, "doubao", configuration.getDoubaoConfig() == null ? null : configuration.getDoubaoConfig().getApiKey());
        addIfHasText(configured, "jina", configuration.getJinaConfig() == null ? null : configuration.getJinaConfig().getApiKey());
        addIfHasText(configured, "suno", configuration.getSunoConfig() == null ? null : configuration.getSunoConfig().getApiKey());
        addIfHasText(configured, "mineru", configuration.getMineruConfig() == null ? null : configuration.getMineruConfig().getApiKey());
        addIfHasText(configured, "typesafe", configuration.getTypeSafeConfig() == null ? null : configuration.getTypeSafeConfig().getApiKey());
        return configured;
    }

    private static void addIfOllamaConfigured(List<String> target, OllamaConfig config) {
        if (config == null) {
            return;
        }
        String host = config.getApiHost() == null ? null : config.getApiHost().trim();
        if (hasText(config.getApiKey()) || (hasText(host) && !DEFAULT_OLLAMA_HOST.equals(host))) {
            target.add("ollama");
        }
    }

    private static void addIfHasText(List<String> target, String name, String secretValue) {
        if (hasText(secretValue)) {
            target.add(name);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Map<String, Object> buildExtensionsSection() {
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        ExtensionRegistry registry = extensionRegistryProvider == null ? null : extensionRegistryProvider.getIfAvailable();
        if (registry == null) {
            extensions.put("available", Boolean.FALSE);
        } else {
            extensions.put("available", Boolean.TRUE);
            List<DiscoveredExtension> discoveredList = registry.list();
            List<Map<String, Object>> discovered = new ArrayList<Map<String, Object>>();
            if (discoveredList != null) {
                for (DiscoveredExtension entry : discoveredList) {
                    if (entry == null) {
                        continue;
                    }
                    ExtensionManifest manifest = entry.getManifest();
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", manifest.getId());
                    item.put("name", manifest.getName());
                    item.put("version", manifest.getVersion());
                    item.put("enabled", Boolean.valueOf(entry.isEnabled()));
                    List<String> capabilities = new ArrayList<String>();
                    if (manifest.getCapabilities() != null) {
                        for (ExtensionCapability capability : manifest.getCapabilities()) {
                            capabilities.add(capability.name());
                        }
                        Collections.sort(capabilities);
                    }
                    item.put("capabilities", capabilities);
                    discovered.add(item);
                }
            }
            List<String> enabledIds = new ArrayList<String>(registry.getEnabledIds());
            Collections.sort(enabledIds);
            List<String> exposedToolIds = new ArrayList<String>(registry.getExposedToolIds());
            Collections.sort(exposedToolIds);

            extensions.put("discoveredCount", Integer.valueOf(discovered.size()));
            extensions.put("enabledCount", Integer.valueOf(enabledIds.size()));
            extensions.put("enabledIds", enabledIds);
            extensions.put("exposedToolIds", exposedToolIds);
            extensions.put("explicitResourceActivation", Boolean.valueOf(registry.isExplicitResourceActivation()));
            extensions.put("discovered", discovered);
        }

        ExtensionRuntimeSnapshot snapshot = extensionSnapshotProvider == null
                ? null
                : extensionSnapshotProvider.getIfAvailable();
        if (snapshot != null) {
            extensions.put("snapshot", buildSnapshotSection(snapshot));
        }
        return extensions;
    }

    private Map<String, Object> buildSnapshotSection(ExtensionRuntimeSnapshot snapshot) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("tools", Integer.valueOf(snapshot.getTools().size()));
        summary.put("commands", Integer.valueOf(snapshot.getCommands().size()));
        summary.put("skills", Integer.valueOf(snapshot.getSkills().size()));
        summary.put("prompts", Integer.valueOf(snapshot.getPrompts().size()));
        summary.put("guardrails", Integer.valueOf(snapshot.getGuardrails().size()));
        summary.put("lifecycleHooks", Integer.valueOf(snapshot.getLifecycleHooks().size()));
        summary.put("toolCallInterceptors", Integer.valueOf(snapshot.getToolCallInterceptors().size()));
        summary.put("promptInterceptors", Integer.valueOf(snapshot.getPromptInterceptors().size()));
        summary.put("modelRequestInterceptors", Integer.valueOf(snapshot.getModelRequestInterceptors().size()));

        List<String> toolNames = new ArrayList<String>();
        for (ExtensionToolSpec toolSpec : snapshot.getTools()) {
            if (toolSpec != null && toolSpec.getName() != null) {
                toolNames.add(toolSpec.getName());
            }
        }
        Collections.sort(toolNames);
        summary.put("toolNames", toolNames);
        return summary;
    }

    private Map<String, Object> buildCapabilitiesSection() {
        ClassLoader classLoader = Ai4jActuatorEndpoint.class.getClassLoader();
        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("webStreams", Boolean.valueOf(
                ClassUtils.isPresent("io.github.lnyocly.ai4j.web.spring.AiWebStreams", classLoader)));
        capabilities.put("agentRuntime", Boolean.valueOf(
                ClassUtils.isPresent("io.github.lnyocly.ai4j.agent.Agent", classLoader)));
        capabilities.put("codingRuntime", Boolean.valueOf(
                ClassUtils.isPresent("io.github.lnyocly.ai4j.coding.CodingAgent", classLoader)));
        return capabilities;
    }
}
