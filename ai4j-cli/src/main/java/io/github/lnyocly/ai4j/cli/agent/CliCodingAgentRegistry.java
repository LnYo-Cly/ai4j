package io.github.lnyocly.ai4j.cli.agent;

import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.definition.CodingApprovalMode;
import io.github.lnyocly.ai4j.coding.definition.CodingIsolationMode;
import io.github.lnyocly.ai4j.coding.definition.CodingMemoryScope;
import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import io.github.lnyocly.ai4j.coding.definition.StaticCodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CliCodingAgentRegistry {

    private final Path workspaceRoot;
    private final List<String> configuredDirectories;

    public CliCodingAgentRegistry(Path workspaceRoot, List<String> configuredDirectories) {
        this.workspaceRoot = workspaceRoot == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
        this.configuredDirectories = configuredDirectories == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(configuredDirectories);
    }

    public CodingAgentDefinitionRegistry loadRegistry() {
        return new StaticCodingAgentDefinitionRegistry(listDefinitions());
    }

    public List<CodingAgentDefinition> listDefinitions() {
        List<CodingAgentDefinition> ordered = new ArrayList<CodingAgentDefinition>();
        for (Path directory : listRoots()) {
            loadDirectory(directory, ordered);
        }
        return ordered.isEmpty()
                ? Collections.<CodingAgentDefinition>emptyList()
                : Collections.unmodifiableList(ordered);
    }

    public List<Path> listRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<Path>();
        Path home = homeAgentsDirectory();
        if (home != null) {
            roots.add(home);
        }
        roots.add(workspaceAgentsDirectory());
        for (String configuredDirectory : configuredDirectories) {
            if (isBlank(configuredDirectory)) {
                continue;
            }
            Path root = Paths.get(configuredDirectory.trim());
            if (!root.isAbsolute()) {
                root = workspaceRoot.resolve(configuredDirectory.trim());
            }
            roots.add(root.toAbsolutePath().normalize());
        }
        return new ArrayList<Path>(roots);
    }

    private void loadDirectory(Path directory, List<CodingAgentDefinition> ordered) {
        if (directory == null || ordered == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!hasSupportedExtension(file)) {
                    continue;
                }
                CodingAgentDefinition definition = loadDefinition(file);
                if (definition != null) {
                    mergeDefinition(ordered, definition);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private CodingAgentDefinition loadDefinition(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            ParsedDefinition parsed = parse(raw);
            String fallbackName = defaultName(file);
            String name = firstNonBlank(parsed.meta("name"), fallbackName);
            String instructions = trimToNull(parsed.body);
            String description = trimToNull(parsed.meta("description"));
            if (instructions == null) {
                instructions = description;
            }
            if (isBlank(name) || isBlank(instructions)) {
                return null;
            }
            Set<String> allowedTools = parseAllowedTools(parsed.meta("tools"));
            CodingIsolationMode isolationMode = parseEnum(
                    parsed.meta("isolationmode"),
                    CodingIsolationMode.class,
                    allowedTools != null && allowedTools.equals(CodingToolNames.readOnlyBuiltIn())
                            ? CodingIsolationMode.READ_ONLY
                            : CodingIsolationMode.INHERIT
            );
            return CodingAgentDefinition.builder()
                    .name(name)
                    .description(description)
                    .toolName(firstNonBlank(parsed.meta("toolname"), defaultToolName(name)))
                    .model(trimToNull(parsed.meta("model")))
                    .instructions(instructions)
                    .systemPrompt(trimToNull(parsed.meta("systemprompt")))
                    .allowedToolNames(allowedTools)
                    .sessionMode(parseEnum(parsed.meta("sessionmode"), CodingSessionMode.class, CodingSessionMode.FORK))
                    .isolationMode(isolationMode)
                    .memoryScope(parseEnum(parsed.meta("memoryscope"), CodingMemoryScope.class, CodingMemoryScope.INHERIT))
                    .approvalMode(parseEnum(parsed.meta("approvalmode"), CodingApprovalMode.class, CodingApprovalMode.INHERIT))
                    .background(parseBoolean(parsed.meta("background"), false))
                    .build();
        } catch (IOException ignored) {
            return null;
        }
    }

    private ParsedDefinition parse(String raw) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n");
        if (!value.startsWith("---\n")) {
            return new ParsedDefinition(Collections.<MetadataEntry>emptyList(), value.trim());
        }
        int end = value.indexOf("\n---\n", 4);
        if (end < 0) {
            end = value.indexOf("\n---", 4);
        }
        if (end < 0) {
            return new ParsedDefinition(Collections.<MetadataEntry>emptyList(), value.trim());
        }
        String header = value.substring(4, end).trim();
        String body = value.substring(Math.min(value.length(), end + 5)).trim();
        List<MetadataEntry> metadata = new ArrayList<MetadataEntry>();
        if (!isBlank(header)) {
            String[] lines = header.split("\n");
            for (String line : lines) {
                String trimmed = trimToNull(line);
                if (trimmed == null || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = normalizeKey(trimmed.substring(0, separator));
                String content = trimToNull(trimmed.substring(separator + 1));
                if (key != null) {
                    metadata.add(new MetadataEntry(key, stripQuotes(content)));
                }
            }
        }
        return new ParsedDefinition(metadata, body);
    }

    private Set<String> parseAllowedTools(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = normalizeToken(value);
        if ("all".equals(normalized) || "builtin_all".equals(normalized) || "all_built_in".equals(normalized)) {
            return CodingToolNames.allBuiltIn();
        }
        if ("read_only".equals(normalized) || "readonly".equals(normalized)) {
            return CodingToolNames.readOnlyBuiltIn();
        }
        if ("inherit".equals(normalized) || "default".equals(normalized)) {
            return null;
        }
        String flattened = value;
        if (flattened.startsWith("[") && flattened.endsWith("]") && flattened.length() >= 2) {
            flattened = flattened.substring(1, flattened.length() - 1);
        }
        String[] parts = flattened.split(",");
        LinkedHashSet<String> toolNames = new LinkedHashSet<String>();
        for (String part : parts) {
            String token = trimToNull(part);
            if (token != null) {
                toolNames.add(token);
            }
        }
        return toolNames.isEmpty() ? null : Collections.unmodifiableSet(toolNames);
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E defaultValue) {
        String value = normalizeToken(raw);
        if (value == null || type == null) {
            return defaultValue;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant != null && normalizeToken(constant.name()).equals(value)) {
                return constant;
            }
        }
        return defaultValue;
    }

    private boolean parseBoolean(String raw, boolean defaultValue) {
        String value = normalizeToken(raw);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equals(value) || "yes".equals(value) || "on".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "no".equals(value) || "off".equals(value)) {
            return false;
        }
        return defaultValue;
    }

    private void mergeDefinition(List<CodingAgentDefinition> ordered, CodingAgentDefinition candidate) {
        if (ordered == null || candidate == null || isBlank(candidate.getName())) {
            return;
        }
        String name = normalizeToken(candidate.getName());
        String toolName = normalizeToken(candidate.getToolName());
        for (Iterator<CodingAgentDefinition> iterator = ordered.iterator(); iterator.hasNext(); ) {
            CodingAgentDefinition existing = iterator.next();
            if (existing == null) {
                iterator.remove();
                continue;
            }
            if (sameKey(name, existing.getName())
                    || sameKey(toolName, existing.getToolName())
                    || sameKey(name, existing.getToolName())
                    || sameKey(toolName, existing.getName())) {
                iterator.remove();
            }
        }
        ordered.add(candidate);
    }

    private boolean sameKey(String left, String right) {
        String normalizedRight = normalizeToken(right);
        return left != null && normalizedRight != null && left.equals(normalizedRight);
    }

    private Path workspaceAgentsDirectory() {
        return workspaceRoot.resolve(".ai4j").resolve("agents").toAbsolutePath().normalize();
    }

    private Path homeAgentsDirectory() {
        String userHome = System.getProperty("user.home");
        return isBlank(userHome)
                ? null
                : Paths.get(userHome).resolve(".ai4j").resolve("agents").toAbsolutePath().normalize();
    }

    private boolean hasSupportedExtension(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".prompt");
    }

    private String defaultName(Path file) {
        if (file == null || file.getFileName() == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String defaultToolName(String name) {
        String slug = normalizeToolSegment(name);
        if (slug == null) {
            return null;
        }
        return slug.startsWith("delegate_") ? slug : "delegate_" + slug;
    }

    private String normalizeToolSegment(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace('-', '_');
        StringBuilder builder = new StringBuilder();
        boolean previousUnderscore = false;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            boolean allowed = (current >= 'a' && current <= 'z')
                    || (current >= '0' && current <= '9')
                    || current == '_';
            char next = allowed ? current : '_';
            if (next == '_') {
                if (!previousUnderscore && builder.length() > 0) {
                    builder.append(next);
                }
                previousUnderscore = true;
            } else {
                builder.append(next);
                previousUnderscore = false;
            }
        }
        String result = builder.toString();
        while (result.endsWith("_")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? null : result;
    }

    private String normalizeKey(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replace("-", "").replace("_", "");
    }

    private String normalizeToken(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ParsedDefinition {
        private final List<MetadataEntry> metadata;
        private final String body;

        private ParsedDefinition(List<MetadataEntry> metadata, String body) {
            this.metadata = metadata == null ? Collections.<MetadataEntry>emptyList() : metadata;
            this.body = body;
        }

        private String meta(String key) {
            if (metadata.isEmpty() || key == null) {
                return null;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            for (MetadataEntry entry : metadata) {
                if (entry != null && normalized.equals(entry.key)) {
                    return entry.value;
                }
            }
            return null;
        }
    }

    private static final class MetadataEntry {
        private final String key;
        private final String value;

        private MetadataEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
