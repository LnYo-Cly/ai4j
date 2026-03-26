package io.github.lnyocly.ai4j.cli;

import java.util.Map;

public class CustomCommandTemplate {

    private final String name;
    private final String description;
    private final String template;
    private final String source;

    public CustomCommandTemplate(String name, String description, String template, String source) {
        this.name = name;
        this.description = description;
        this.template = template;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTemplate() {
        return template;
    }

    public String getSource() {
        return source;
    }

    public String render(Map<String, String> variables) {
        String result = template == null ? "" : template;
        if (variables == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            result = result.replace("$" + key, entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
