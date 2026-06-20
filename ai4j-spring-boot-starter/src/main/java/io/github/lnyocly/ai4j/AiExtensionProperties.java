package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.extensions")
public class AiExtensionProperties {

    private List<String> enabled = new ArrayList<String>();

    private boolean explicitResourceActivation;

    private ToolsProperties tools = new ToolsProperties();

    private CommandsProperties commands = new CommandsProperties();

    private SkillsProperties skills = new SkillsProperties();

    private PromptsProperties prompts = new PromptsProperties();

    private GuardrailsProperties guardrails = new GuardrailsProperties();

    @Data
    public static class ToolsProperties {
        private List<String> expose = new ArrayList<String>();
    }

    @Data
    public static class CommandsProperties {
        private List<String> allow = new ArrayList<String>();
    }

    @Data
    public static class SkillsProperties {
        private List<String> allow = new ArrayList<String>();
    }

    @Data
    public static class PromptsProperties {
        private List<String> allow = new ArrayList<String>();
    }

    @Data
    public static class GuardrailsProperties {
        private List<String> allow = new ArrayList<String>();
    }
}
