package io.github.lnyocly.ai4j.coding.definition;

import java.util.List;

public interface CodingAgentDefinitionRegistry {

    CodingAgentDefinition getDefinition(String nameOrToolName);

    List<CodingAgentDefinition> listDefinitions();
}
