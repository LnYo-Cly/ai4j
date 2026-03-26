package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.service.PlatformType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class CodeCommandOptionsParser {

    public CodeCommandOptions parse(List<String> args,
                                    Map<String, String> env,
                                    Properties properties,
                                    Path currentDirectory) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        boolean help = false;
        boolean allowOutsideWorkspace = false;
        boolean noSession = false;
        boolean noSessionSpecified = false;
        boolean verbose = false;

        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    help = true;
                    continue;
                }
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("Unsupported argument: " + arg);
                }

                String option = arg.substring(2);
                String value = null;
                int equalsIndex = option.indexOf('=');
                if (equalsIndex >= 0) {
                    value = option.substring(equalsIndex + 1);
                    option = option.substring(0, equalsIndex);
                }

                if ("allow-outside-workspace".equals(option)) {
                    if (value == null && i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        value = args.get(++i);
                    }
                    allowOutsideWorkspace = value == null || parseBoolean(value);
                    continue;
                }
                if ("no-session".equals(option)) {
                    if (value == null && i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        value = args.get(++i);
                    }
                    noSession = value == null || parseBoolean(value);
                    noSessionSpecified = true;
                    continue;
                }
                if ("verbose".equals(option)) {
                    if (value == null && i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        value = args.get(++i);
                    }
                    verbose = value == null || parseBoolean(value);
                    continue;
                }

                if (value == null) {
                    if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for --" + option);
                    }
                    value = args.get(++i);
                }
                values.put(normalizeOptionName(option), value);
            }
        }

        if (help) {
            return new CodeCommandOptions(
                    true,
                    resolveUiMode(values, env, properties, CliUiMode.CLI),
                    PlatformType.OPENAI,
                    CliProtocol.defaultProtocol(PlatformType.OPENAI, null),
                    null,
                    null,
                    null,
                    currentDirectory.toAbsolutePath().normalize().toString(),
                    null,
                    null,
                    null,
                    null,
                    12,
                    32,
                    null,
                    null,
                    null,
                    Boolean.FALSE,
                    allowOutsideWorkspace,
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    128000,
                    16384,
                    20000,
                    400,
                    verbose
            );
        }

        CliUiMode uiMode = resolveUiMode(values, env, properties, CliUiMode.CLI);
        String cliProtocolOverride = values.get("protocol");
        if ("auto".equalsIgnoreCase(trimToNull(cliProtocolOverride))) {
            throw new IllegalArgumentException("Unsupported protocol: auto. Expected: chat, responses");
        }
        String workspace = firstNonBlank(
                values.get("workspace"),
                envValue(env, "AI4J_WORKSPACE"),
                propertyValue(properties, "ai4j.workspace"),
                currentDirectory.toAbsolutePath().normalize().toString()
        );
        CliProviderConfigManager providerConfigManager = new CliProviderConfigManager(Paths.get(workspace));
        CliResolvedProviderConfig resolvedProviderConfig = providerConfigManager.resolve(
                values.get("provider"),
                values.get("protocol"),
                values.get("model"),
                values.get("api-key"),
                values.get("base-url"),
                env,
                properties
        );
        PlatformType provider = resolvedProviderConfig.getProvider();
        String model = resolvedProviderConfig.getModel();
        if (isBlank(model)) {
            throw new IllegalArgumentException("model is required");
        }

        CliProtocol protocol = resolvedProviderConfig.getProtocol();

        String sessionId = firstNonBlank(
                values.get("session-id"),
                envValue(env, "AI4J_SESSION_ID"),
                propertyValue(properties, "ai4j.session.id")
        );

        String resumeSessionId = firstNonBlank(
                values.get("resume"),
                values.get("load"),
                envValue(env, "AI4J_RESUME_SESSION"),
                propertyValue(properties, "ai4j.session.resume")
        );

        String forkSessionId = firstNonBlank(
                values.get("fork"),
                envValue(env, "AI4J_FORK_SESSION"),
                propertyValue(properties, "ai4j.session.fork")
        );

        if (!noSessionSpecified) {
            noSession = parseBooleanOrDefault(firstNonBlank(
                    envValue(env, "AI4J_NO_SESSION"),
                    propertyValue(properties, "ai4j.session.no-session")
            ), false);
        }

        String sessionStoreDir = firstNonBlank(
                values.get("session-dir"),
                envValue(env, "AI4J_SESSION_DIR"),
                propertyValue(properties, "ai4j.session.dir"),
                Paths.get(workspace).resolve(".ai4j").resolve("sessions").toAbsolutePath().normalize().toString()
        );

        String theme = firstNonBlank(
                values.get("theme"),
                envValue(env, "AI4J_THEME"),
                propertyValue(properties, "ai4j.theme")
        );

        ApprovalMode approvalMode = ApprovalMode.parse(firstNonBlank(
                values.get("approval"),
                envValue(env, "AI4J_APPROVAL"),
                propertyValue(properties, "ai4j.approval"),
                ApprovalMode.AUTO.getValue()
        ));

        String apiKey = resolvedProviderConfig.getApiKey();

        String baseUrl = resolvedProviderConfig.getBaseUrl();

        String workspaceDescription = firstNonBlank(
                values.get("workspace-description"),
                envValue(env, "AI4J_WORKSPACE_DESCRIPTION"),
                propertyValue(properties, "ai4j.workspace.description")
        );

        String systemPrompt = firstNonBlank(
                values.get("system"),
                envValue(env, "AI4J_SYSTEM_PROMPT"),
                propertyValue(properties, "ai4j.system")
        );

        String instructions = firstNonBlank(
                values.get("instructions"),
                envValue(env, "AI4J_INSTRUCTIONS"),
                propertyValue(properties, "ai4j.instructions")
        );

        String prompt = firstNonBlank(
                values.get("prompt"),
                envValue(env, "AI4J_PROMPT"),
                propertyValue(properties, "ai4j.prompt")
        );

        int maxSteps = parseInteger(firstNonBlank(
                values.get("max-steps"),
                envValue(env, "AI4J_MAX_STEPS"),
                propertyValue(properties, "ai4j.max.steps"),
                "12"
        ), "max-steps");

        int maxToolCalls = parseInteger(firstNonBlank(
                values.get("max-tool-calls"),
                envValue(env, "AI4J_MAX_TOOL_CALLS"),
                propertyValue(properties, "ai4j.max.tool.calls"),
                "32"
        ), "max-tool-calls");

        Double temperature = parseDoubleOrNull(firstNonBlank(
                values.get("temperature"),
                envValue(env, "AI4J_TEMPERATURE"),
                propertyValue(properties, "ai4j.temperature")
        ), "temperature");

        Double topP = parseDoubleOrNull(firstNonBlank(
                values.get("top-p"),
                envValue(env, "AI4J_TOP_P"),
                propertyValue(properties, "ai4j.top.p")
        ), "top-p");

        Integer maxOutputTokens = parseIntegerOrNull(firstNonBlank(
                values.get("max-output-tokens"),
                envValue(env, "AI4J_MAX_OUTPUT_TOKENS"),
                propertyValue(properties, "ai4j.max.output.tokens")
        ), "max-output-tokens");

        Boolean parallelToolCalls = parseBooleanOrDefault(firstNonBlank(
                values.get("parallel-tool-calls"),
                envValue(env, "AI4J_PARALLEL_TOOL_CALLS"),
                propertyValue(properties, "ai4j.parallel.tool.calls")
        ), false);

        boolean autoSaveSession = parseBooleanOrDefault(firstNonBlank(
                values.get("auto-save-session"),
                envValue(env, "AI4J_AUTO_SAVE_SESSION"),
                propertyValue(properties, "ai4j.session.auto-save")
        ), true);

        boolean autoCompact = parseBooleanOrDefault(firstNonBlank(
                values.get("auto-compact"),
                envValue(env, "AI4J_AUTO_COMPACT"),
                propertyValue(properties, "ai4j.compact.auto")
        ), true);

        int compactContextWindowTokens = parseInteger(firstNonBlank(
                values.get("compact-context-window-tokens"),
                envValue(env, "AI4J_COMPACT_CONTEXT_WINDOW_TOKENS"),
                propertyValue(properties, "ai4j.compact.context-window-tokens"),
                "128000"
        ), "compact-context-window-tokens");

        int compactReserveTokens = parseInteger(firstNonBlank(
                values.get("compact-reserve-tokens"),
                envValue(env, "AI4J_COMPACT_RESERVE_TOKENS"),
                propertyValue(properties, "ai4j.compact.reserve-tokens"),
                "16384"
        ), "compact-reserve-tokens");

        int compactKeepRecentTokens = parseInteger(firstNonBlank(
                values.get("compact-keep-recent-tokens"),
                envValue(env, "AI4J_COMPACT_KEEP_RECENT_TOKENS"),
                propertyValue(properties, "ai4j.compact.keep-recent-tokens"),
                "20000"
        ), "compact-keep-recent-tokens");

        int compactSummaryMaxOutputTokens = parseInteger(firstNonBlank(
                values.get("compact-summary-max-output-tokens"),
                envValue(env, "AI4J_COMPACT_SUMMARY_MAX_OUTPUT_TOKENS"),
                propertyValue(properties, "ai4j.compact.summary-max-output-tokens"),
                "400"
        ), "compact-summary-max-output-tokens");

        if (!isBlank(resumeSessionId) && !isBlank(forkSessionId)) {
            throw new IllegalArgumentException("--resume and --fork cannot be used together");
        }
        if (noSession && !isBlank(resumeSessionId)) {
            throw new IllegalArgumentException("--no-session cannot be combined with --resume");
        }
        if (noSession && !isBlank(forkSessionId)) {
            throw new IllegalArgumentException("--no-session cannot be combined with --fork");
        }

        return new CodeCommandOptions(
                false,
                uiMode,
                provider,
                protocol,
                model,
                apiKey,
                baseUrl,
                workspace,
                workspaceDescription,
                systemPrompt,
                instructions,
                prompt,
                maxSteps,
                maxToolCalls,
                temperature,
                topP,
                maxOutputTokens,
                parallelToolCalls,
                allowOutsideWorkspace,
                sessionId,
                resumeSessionId,
                forkSessionId,
                sessionStoreDir,
                theme,
                approvalMode,
                noSession,
                autoSaveSession,
                autoCompact,
                compactContextWindowTokens,
                compactReserveTokens,
                compactKeepRecentTokens,
                compactSummaryMaxOutputTokens,
                verbose
        );
    }

    private CliUiMode resolveUiMode(Map<String, String> values,
                                    Map<String, String> env,
                                    Properties properties,
                                    CliUiMode defaultValue) {
        return CliUiMode.parse(firstNonBlank(
                values.get("ui"),
                envValue(env, "AI4J_UI"),
                propertyValue(properties, "ai4j.ui"),
                defaultValue.getValue()
        ));
    }

    private String envValue(Map<String, String> env, String name) {
        return env == null ? null : env.get(name);
    }

    private String propertyValue(Properties properties, String name) {
        return properties == null ? null : properties.getProperty(name);
    }

    private String normalizeOptionName(String option) {
        if (option == null) {
            return null;
        }
        return option.trim().toLowerCase(Locale.ROOT);
    }

    private int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for --" + name + ": " + value, ex);
        }
    }

    private Integer parseIntegerOrNull(String value, String name) {
        if (isBlank(value)) {
            return null;
        }
        return parseInteger(value, name);
    }

    private Double parseDoubleOrNull(String value, String name) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number for --" + name + ": " + value, ex);
        }
    }

    private Boolean parseBooleanOrDefault(String value, boolean defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return parseBoolean(value);
    }

    private boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value: " + value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
