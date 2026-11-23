package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Boot 2/3/4-compatible auto-configuration for the {@code /actuator/ai4j} diagnostic endpoint.
 */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Endpoint.class, ReadOperation.class, AiService.class})
@AutoConfigureAfter({AiConfigAutoConfiguration.class, AiWebAutoConfiguration.class})
public class Ai4jActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Ai4jActuatorEndpoint ai4jActuatorEndpoint(
            ObjectProvider<AiService> aiServiceProvider,
            ObjectProvider<AiServiceRegistry> aiServiceRegistryProvider,
            ObjectProvider<Configuration> configurationProvider,
            ObjectProvider<ExtensionRegistry> extensionRegistryProvider,
            ObjectProvider<ExtensionRuntimeSnapshot> extensionSnapshotProvider,
            ObjectProvider<AiConfigProperties> aiConfigPropertiesProvider) {
        return new Ai4jActuatorEndpoint(
                aiServiceProvider,
                aiServiceRegistryProvider,
                configurationProvider,
                extensionRegistryProvider,
                extensionSnapshotProvider,
                aiConfigPropertiesProvider
        );
    }
}
