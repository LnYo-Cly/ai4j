package io.github.lnyocly.ai4j.cli.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CliMcpConfigManager {

    private final Path workspaceRoot;

    public CliMcpConfigManager(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
    }

    public CliMcpConfig loadGlobalConfig() {
        CliMcpConfig config = loadConfig(globalMcpPath(), CliMcpConfig.class);
        if (config == null) {
            config = new CliMcpConfig();
        }
        boolean changed = normalize(config);
        if (changed) {
            persistNormalizedGlobalConfig(config);
        }
        return config;
    }

    public CliMcpConfig saveGlobalConfig(CliMcpConfig config) throws IOException {
        CliMcpConfig normalized = config == null ? new CliMcpConfig() : config;
        normalize(normalized);
        Path path = globalMcpPath();
        if (path == null) {
            throw new IOException("user home directory is unavailable");
        }
        Files.createDirectories(path.getParent());
        Files.write(path, JSON.toJSONString(normalized, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
        return normalized;
    }

    public CliWorkspaceConfig loadWorkspaceConfig() {
        CliWorkspaceConfig config = loadConfig(workspaceConfigPath(), CliWorkspaceConfig.class);
        if (config == null) {
            config = new CliWorkspaceConfig();
        }
        normalize(config);
        return config;
    }

    public CliWorkspaceConfig saveWorkspaceConfig(CliWorkspaceConfig config) throws IOException {
        CliWorkspaceConfig normalized = config == null ? new CliWorkspaceConfig() : config;
        normalize(normalized);
        Path path = workspaceConfigPath();
        Files.createDirectories(path.getParent());
        Files.write(path, JSON.toJSONString(normalized, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
        return normalized;
    }

    public CliResolvedMcpConfig resolve(Collection<String> pausedServerNames) {
        CliMcpConfig globalConfig = loadGlobalConfig();
        CliWorkspaceConfig workspaceConfig = loadWorkspaceConfig();

        Set<String> enabledNames = toOrderedSet(workspaceConfig.getEnabledMcpServers());
        Set<String> pausedNames = toOrderedSet(pausedServerNames);
        Map<String, CliResolvedMcpServer> resolvedServers = new LinkedHashMap<String, CliResolvedMcpServer>();
        List<String> effectiveEnabledNames = new ArrayList<String>();
        List<String> unknownEnabledNames = new ArrayList<String>();

        Map<String, CliMcpServerDefinition> definitions = globalConfig.getMcpServers();
        if (definitions == null) {
            definitions = Collections.emptyMap();
        }

        for (Map.Entry<String, CliMcpServerDefinition> entry : definitions.entrySet()) {
            String name = entry.getKey();
            CliMcpServerDefinition definition = entry.getValue();
            boolean workspaceEnabled = enabledNames.contains(name);
            boolean sessionPaused = pausedNames.contains(name);
            String validationError = validate(definition);
            boolean active = workspaceEnabled && !sessionPaused && validationError == null;
            if (workspaceEnabled) {
                effectiveEnabledNames.add(name);
            }
            resolvedServers.put(name, new CliResolvedMcpServer(
                    name,
                    definition == null ? null : definition.getType(),
                    workspaceEnabled,
                    sessionPaused,
                    active,
                    validationError,
                    definition
            ));
        }

        for (String enabledName : enabledNames) {
            if (!definitions.containsKey(enabledName)) {
                unknownEnabledNames.add(enabledName);
            }
        }

        List<String> effectivePausedNames = new ArrayList<String>();
        for (String pausedName : pausedNames) {
            if (definitions.containsKey(pausedName)) {
                effectivePausedNames.add(pausedName);
            }
        }

        return new CliResolvedMcpConfig(
                resolvedServers,
                effectiveEnabledNames,
                effectivePausedNames,
                unknownEnabledNames
        );
    }

    public Path globalMcpPath() {
        String userHome = System.getProperty("user.home");
        return isBlank(userHome) ? null : Paths.get(userHome).resolve(".ai4j").resolve("mcp.json");
    }

    public Path workspaceConfigPath() {
        return workspaceRoot.resolve(".ai4j").resolve("workspace.json");
    }

    private <T> T loadConfig(Path path, Class<T> type) {
        if (path == null || type == null || !Files.exists(path)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readAllBytes(path), type);
        } catch (IOException ex) {
            return null;
        }
    }

    private void persistNormalizedGlobalConfig(CliMcpConfig config) {
        Path path = globalMcpPath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private boolean normalize(CliMcpConfig config) {
        if (config == null) {
            return false;
        }
        boolean changed = false;
        Map<String, CliMcpServerDefinition> currentServers = config.getMcpServers();
        if (currentServers == null) {
            config.setMcpServers(new LinkedHashMap<String, CliMcpServerDefinition>());
            return true;
        }

        Map<String, CliMcpServerDefinition> normalizedServers = new LinkedHashMap<String, CliMcpServerDefinition>();
        for (Map.Entry<String, CliMcpServerDefinition> entry : currentServers.entrySet()) {
            String normalizedName = normalizeName(entry.getKey());
            if (isBlank(normalizedName)) {
                changed = true;
                continue;
            }
            CliMcpServerDefinition definition = entry.getValue();
            CliMcpServerDefinition normalizedDefinition = normalize(definition);
            normalizedServers.put(normalizedName, normalizedDefinition);
            if (!normalizedName.equals(entry.getKey()) || normalizedDefinition != definition) {
                changed = true;
            }
        }

        if (!normalizedServers.equals(currentServers)) {
            config.setMcpServers(normalizedServers);
            changed = true;
        }
        return changed;
    }

    private void normalize(CliWorkspaceConfig config) {
        if (config == null) {
            return;
        }
        config.setActiveProfile(normalizeName(config.getActiveProfile()));
        config.setModelOverride(normalizeValue(config.getModelOverride()));
        config.setEnabledMcpServers(normalizeNames(config.getEnabledMcpServers()));
        config.setSkillDirectories(normalizeNames(config.getSkillDirectories()));
        config.setAgentDirectories(normalizeNames(config.getAgentDirectories()));
    }

    private CliMcpServerDefinition normalize(CliMcpServerDefinition definition) {
        String command = definition == null ? null : normalizeValue(definition.getCommand());
        return CliMcpServerDefinition.builder()
                .type(normalizeTransportType(definition == null ? null : definition.getType(), command))
                .url(definition == null ? null : normalizeValue(definition.getUrl()))
                .command(command)
                .args(definition == null ? null : normalizeNames(definition.getArgs()))
                .env(definition == null ? null : normalizeMap(definition.getEnv()))
                .cwd(definition == null ? null : normalizeValue(definition.getCwd()))
                .headers(definition == null ? null : normalizeMap(definition.getHeaders()))
                .build();
    }

    private String validate(CliMcpServerDefinition definition) {
        if (definition == null) {
            return "missing MCP server definition";
        }
        String type = normalizeValue(definition.getType());
        if (isBlank(type)) {
            return "missing MCP transport type";
        }
        if ("stdio".equals(type)) {
            return isBlank(definition.getCommand()) ? "stdio transport requires command" : null;
        }
        if ("sse".equals(type) || "streamable_http".equals(type)) {
            return isBlank(definition.getUrl()) ? type + " transport requires url" : null;
        }
        return "unsupported MCP transport: " + type;
    }

    private String normalizeTransportType(String rawType, String command) {
        String normalizedType = normalizeValue(rawType);
        if (isBlank(normalizedType)) {
            return isBlank(command) ? null : "stdio";
        }
        String lowerCaseType = normalizedType.toLowerCase(Locale.ROOT);
        if ("http".equals(lowerCaseType)) {
            return "streamable_http";
        }
        return lowerCaseType;
    }

    private Set<String> toOrderedSet(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        for (String value : values) {
            String candidate = normalizeName(value);
            if (!isBlank(candidate)) {
                normalized.add(candidate);
            }
        }
        return normalized;
    }

    private List<String> normalizeNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        for (String value : values) {
            String candidate = normalizeName(value);
            if (!isBlank(candidate)) {
                normalized.add(candidate);
            }
        }
        return normalized.isEmpty() ? null : new ArrayList<String>(normalized);
    }

    private Map<String, String> normalizeMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalizeName(entry.getKey());
            if (isBlank(key)) {
                continue;
            }
            normalized.put(key, normalizeValue(entry.getValue()));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeName(String value) {
        return normalizeValue(value);
    }

    private String normalizeValue(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

