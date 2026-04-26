package io.github.lnyocly.ai4j.coding.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class CompositeCodingAgentDefinitionRegistry implements CodingAgentDefinitionRegistry {

    private final List<CodingAgentDefinitionRegistry> registries;

    public CompositeCodingAgentDefinitionRegistry(CodingAgentDefinitionRegistry... registries) {
        this(registries == null ? Collections.<CodingAgentDefinitionRegistry>emptyList() : Arrays.asList(registries));
    }

    public CompositeCodingAgentDefinitionRegistry(List<CodingAgentDefinitionRegistry> registries) {
        if (registries == null || registries.isEmpty()) {
            this.registries = Collections.emptyList();
            return;
        }
        List<CodingAgentDefinitionRegistry> ordered = new ArrayList<CodingAgentDefinitionRegistry>();
        for (CodingAgentDefinitionRegistry registry : registries) {
            if (registry != null) {
                ordered.add(registry);
            }
        }
        this.registries = ordered.isEmpty()
                ? Collections.<CodingAgentDefinitionRegistry>emptyList()
                : Collections.unmodifiableList(ordered);
    }

    @Override
    public CodingAgentDefinition getDefinition(String nameOrToolName) {
        if (isBlank(nameOrToolName)) {
            return null;
        }
        for (int i = registries.size() - 1; i >= 0; i--) {
            CodingAgentDefinitionRegistry registry = registries.get(i);
            CodingAgentDefinition definition = registry == null ? null : registry.getDefinition(nameOrToolName);
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    @Override
    public List<CodingAgentDefinition> listDefinitions() {
        if (registries.isEmpty()) {
            return Collections.emptyList();
        }
        List<CodingAgentDefinition> ordered = new ArrayList<CodingAgentDefinition>();
        for (CodingAgentDefinitionRegistry registry : registries) {
            if (registry == null || registry.listDefinitions() == null) {
                continue;
            }
            for (CodingAgentDefinition definition : registry.listDefinitions()) {
                mergeDefinition(ordered, definition);
            }
        }
        return ordered.isEmpty()
                ? Collections.<CodingAgentDefinition>emptyList()
                : Collections.unmodifiableList(ordered);
    }

    private void mergeDefinition(List<CodingAgentDefinition> ordered, CodingAgentDefinition candidate) {
        if (ordered == null || candidate == null || isBlank(candidate.getName())) {
            return;
        }
        String candidateName = normalize(candidate.getName());
        String candidateToolName = normalize(candidate.getToolName());
        for (Iterator<CodingAgentDefinition> iterator = ordered.iterator(); iterator.hasNext(); ) {
            CodingAgentDefinition existing = iterator.next();
            if (existing == null) {
                iterator.remove();
                continue;
            }
            if (sameKey(candidateName, existing.getName())
                    || sameKey(candidateToolName, existing.getToolName())
                    || sameKey(candidateName, existing.getToolName())
                    || sameKey(candidateToolName, existing.getName())) {
                iterator.remove();
            }
        }
        ordered.add(candidate);
    }

    private boolean sameKey(String left, String right) {
        String normalizedRight = normalize(right);
        return left != null && normalizedRight != null && left.equals(normalizedRight);
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
