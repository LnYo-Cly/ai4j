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
public class AgentBlueprintDiff {

    private String name;
    private long fromVersion;
    private long toVersion;
    private boolean modelChanged;
    private String previousModel;
    private String currentModel;
    private boolean systemPromptChanged;
    private String previousSystemPrompt;
    private String currentSystemPrompt;

    @Builder.Default
    private List<String> addedTools = new ArrayList<String>();

    @Builder.Default
    private List<String> removedTools = new ArrayList<String>();

    @Builder.Default
    private List<String> addedCapabilities = new ArrayList<String>();

    @Builder.Default
    private List<String> removedCapabilities = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> changedMetadata = new LinkedHashMap<String, Object>();
}
