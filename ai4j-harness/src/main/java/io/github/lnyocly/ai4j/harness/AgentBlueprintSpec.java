package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentBlueprintSpec {

    private String scopeKey;
    private String name;
    private String model;
    private String systemPrompt;
    private String description;

    @Builder.Default
    private List<String> allowedTools = new ArrayList<String>();

    @Builder.Default
    private List<String> capabilities = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
