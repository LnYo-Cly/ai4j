package io.github.lnyocly.ai4j.coding.definition;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder(toBuilder = true)
public class CodingAgentDefinition {

    private String name;

    private String description;

    private String toolName;

    private String model;

    private String instructions;

    private String systemPrompt;

    private Set<String> allowedToolNames;

    @Builder.Default
    private CodingSessionMode sessionMode = CodingSessionMode.FORK;

    @Builder.Default
    private CodingIsolationMode isolationMode = CodingIsolationMode.INHERIT;

    @Builder.Default
    private CodingMemoryScope memoryScope = CodingMemoryScope.INHERIT;

    @Builder.Default
    private CodingApprovalMode approvalMode = CodingApprovalMode.INHERIT;

    @Builder.Default
    private boolean background = false;
}
