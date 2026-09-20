package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative agent assembly from Agent Blueprint YAML files.
 *
 * <pre>
 * ai:
 *   agent:
 *     enabled: true
 *     default-agent: reviewer
 *     blueprints:
 *       reviewer: classpath:agents/reviewer.yaml
 *       support:  file:/etc/ai4j/agents/support.yaml
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ai.agent")
public class AiAgentProperties {

    /** Master switch for blueprint-driven agent auto-configuration. */
    private boolean enabled = false;

    /** Blueprint name exposed as the default {@code Agent} bean. */
    private String defaultAgent;

    /**
     * Model client protocol when a blueprint does not declare
     * {@code model.options.protocol}: {@code chat}, {@code responses} or {@code messages}.
     * Blank means auto: messages for anthropic platforms, chat otherwise.
     */
    private String protocol;

    /** Accept {@code sandbox.enabled} declarations without creating real sandbox sessions. */
    private boolean allowSandboxDeclaration = false;

    /** Agent name to blueprint YAML resource location (classpath:/file:/...). */
    private Map<String, String> blueprints = new LinkedHashMap<String, String>();
}
