package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.extensions")
public class AiExtensionProperties {

    private List<String> enabled = new ArrayList<String>();

    private ToolsProperties tools = new ToolsProperties();

    @Data
    public static class ToolsProperties {
        private List<String> expose = new ArrayList<String>();
    }
}
