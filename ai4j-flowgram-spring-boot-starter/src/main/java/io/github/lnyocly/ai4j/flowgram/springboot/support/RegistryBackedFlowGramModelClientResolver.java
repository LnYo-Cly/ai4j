package io.github.lnyocly.ai4j.flowgram.springboot.support;

import io.github.lnyocly.ai4j.agent.flowgram.Ai4jFlowGramLlmNodeRunner;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.flowgram.springboot.config.FlowGramProperties;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RegistryBackedFlowGramModelClientResolver implements Ai4jFlowGramLlmNodeRunner.ModelClientResolver {

    private final AiServiceRegistry aiServiceRegistry;
    private final FlowGramProperties properties;
    private final ConcurrentMap<String, AgentModelClient> chatClients =
            new ConcurrentHashMap<String, AgentModelClient>();

    public RegistryBackedFlowGramModelClientResolver(AiServiceRegistry aiServiceRegistry,
                                                     FlowGramProperties properties) {
        this.aiServiceRegistry = aiServiceRegistry;
        this.properties = properties;
    }

    @Override
    public AgentModelClient resolve(FlowGramNodeSchema node, Map<String, Object> inputs) {
        String serviceId = firstNonBlank(
                valueAsString(inputs == null ? null : inputs.get("serviceId")),
                valueAsString(inputs == null ? null : inputs.get("aiServiceId")),
                properties == null ? null : properties.getDefaultServiceId()
        );
        if (serviceId == null) {
            throw new IllegalArgumentException("FlowGram LLM node requires serviceId/aiServiceId or ai4j.flowgram.default-service-id");
        }
        return chatClients.computeIfAbsent(serviceId, key -> new ChatModelClient(aiServiceRegistry.getChatService(key)));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

