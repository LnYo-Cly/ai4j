package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoadException;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintModel;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationIssue;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactory;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryContext;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryException;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.provider.CliResolvedProviderConfig;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class AgentBlueprintRunCommand {

    private final Map<String, String> env;
    private final Properties properties;
    private final Path currentDirectory;
    private final AgentBlueprintLoader loader;
    private final AgentFactory agentFactory;
    private final AgentBlueprintRunModelClientFactory modelClientFactory;

    public AgentBlueprintRunCommand(Map<String, String> env,
                                    Properties properties,
                                    Path currentDirectory) {
        this(env,
                properties,
                currentDirectory,
                new AgentBlueprintLoader(),
                new AgentFactory(),
                new DefaultAgentBlueprintRunModelClientFactory());
    }

    AgentBlueprintRunCommand(Map<String, String> env,
                             Properties properties,
                             Path currentDirectory,
                             AgentBlueprintLoader loader,
                             AgentFactory agentFactory,
                             AgentBlueprintRunModelClientFactory modelClientFactory) {
        this.env = env;
        this.properties = properties;
        this.currentDirectory = currentDirectory == null ? Paths.get(".").toAbsolutePath().normalize() : currentDirectory;
        this.loader = loader == null ? new AgentBlueprintLoader() : loader;
        this.agentFactory = agentFactory == null ? new AgentFactory() : agentFactory;
        this.modelClientFactory = modelClientFactory == null ? new DefaultAgentBlueprintRunModelClientFactory() : modelClientFactory;
    }

    public int run(List<String> args, TerminalIO terminal) {
        try {
            ParsedRunArguments parsed = parse(args);
            if (parsed.help) {
                printHelp(terminal);
                return 0;
            }
            AgentBlueprint blueprint = loader.load(parsed.blueprintPath);
            AgentBlueprintRunOptions options = resolveOptions(parsed, blueprint);
            AgentModelClient modelClient = modelClientFactory.create(options);
            AgentFactoryContext context = AgentFactoryContext.builder()
                    .modelClient(modelClient)
                    .allowSandboxDeclaration(options.isAllowSandboxDeclaration())
                    .build();
            Agent agent = agentFactory.create(blueprint, context);
            AgentResult result = agent.run(AgentRequest.builder().input(options.getInput()).build());
            terminal.println(safeOutput(result));
            return 0;
        } catch (IllegalArgumentException ex) {
            terminal.errorln("Argument error: " + safeMessage(ex));
            printHelp(terminal);
            return 2;
        } catch (AgentBlueprintLoadException ex) {
            terminal.errorln("Blueprint load error [" + ex.getCode() + "]: " + safeMessage(ex));
            return 2;
        } catch (AgentFactoryException ex) {
            terminal.errorln("Blueprint factory error [" + ex.getCode() + "]: " + safeMessage(ex));
            printValidationReport(ex, terminal);
            return 2;
        } catch (Exception ex) {
            terminal.errorln("Blueprint run failed: " + safeMessage(ex));
            if (isVerbose(args)) {
                terminal.errorln(stackTraceOf(ex));
            }
            return 1;
        }
    }

    private AgentBlueprintRunOptions resolveOptions(ParsedRunArguments parsed, AgentBlueprint blueprint) {
        AgentBlueprintModel model = blueprint == null ? null : blueprint.getModel();
        Path workspace = parsed.workspace == null ? currentDirectory : resolvePath(parsed.workspace);
        CliProviderConfigManager providerConfigManager = new CliProviderConfigManager(workspace);
        String blueprintProvider = normalizeBlueprintProvider(model == null ? null : model.getProvider());
        String profileOverride = firstNonBlank(parsed.profile, model == null ? null : model.getProfile());
        String providerOverride = firstNonBlank(parsed.provider, isBlank(profileOverride) ? blueprintProvider : null);
        String modelOverride = firstNonBlank(parsed.model, model == null ? null : model.getModel());
        CliResolvedProviderConfig resolved = providerConfigManager.resolveWithProfile(
                profileOverride,
                providerOverride,
                parsed.protocol,
                modelOverride,
                parsed.apiKey,
                parsed.baseUrl,
                env,
                properties
        );
        if (!isBlank(profileOverride) && !equalsValue(profileOverride, resolved.getEffectiveProfile())) {
            throw new IllegalArgumentException("profile not found or incompatible with provider: " + profileOverride);
        }
        if (isBlank(resolved.getModel())) {
            throw new IllegalArgumentException("model is required; set model.model in YAML or pass --model");
        }
        String input = firstNonBlank(parsed.input, envValue("AI4J_AGENT_INPUT"), propertyValue("ai4j.agent.input"));
        if (isBlank(input)) {
            throw new IllegalArgumentException("input is required; pass --input or --prompt");
        }
        return new AgentBlueprintRunOptions(
                parsed.blueprintPath,
                workspace,
                resolved.getProvider(),
                resolved.getProtocol(),
                profileOverride,
                resolved.getModel(),
                resolved.getApiKey(),
                resolved.getBaseUrl(),
                input,
                parsed.allowSandboxDeclaration,
                parsed.verbose
        );
    }

    private ParsedRunArguments parse(List<String> args) {
        ParsedRunArguments parsed = new ParsedRunArguments();
        if (args == null || args.isEmpty()) {
            parsed.help = true;
            return parsed;
        }
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-h".equals(arg) || "--help".equals(arg)) {
                parsed.help = true;
                continue;
            }
            if (arg == null) {
                continue;
            }
            if (!arg.startsWith("--")) {
                if (parsed.blueprintPath != null) {
                    throw new IllegalArgumentException("unexpected argument: " + arg);
                }
                parsed.blueprintPath = resolvePath(arg);
                continue;
            }
            String option = arg.substring(2);
            String value = null;
            int equalsIndex = option.indexOf('=');
            if (equalsIndex >= 0) {
                value = option.substring(equalsIndex + 1);
                option = option.substring(0, equalsIndex);
            }
            String normalized = normalizeOptionName(option);
            if ("verbose".equals(normalized)) {
                parsed.verbose = parseBooleanFlag(args, i, value);
                if (value == null && i + 1 < args.size() && isBooleanLiteral(args.get(i + 1))) {
                    i++;
                }
                continue;
            }
            if ("allow-sandbox-declaration".equals(normalized)) {
                parsed.allowSandboxDeclaration = parseBooleanFlag(args, i, value);
                if (value == null && i + 1 < args.size() && isBooleanLiteral(args.get(i + 1))) {
                    i++;
                }
                continue;
            }
            if (value == null) {
                if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                    throw new IllegalArgumentException("missing value for --" + option);
                }
                value = args.get(++i);
            }
            if ("input".equals(normalized) || "prompt".equals(normalized)) {
                parsed.input = value;
            } else if ("provider".equals(normalized)) {
                parsed.provider = value;
            } else if ("protocol".equals(normalized)) {
                parsed.protocol = value;
            } else if ("model".equals(normalized)) {
                parsed.model = value;
            } else if ("profile".equals(normalized)) {
                parsed.profile = value;
            } else if ("api-key".equals(normalized)) {
                parsed.apiKey = value;
            } else if ("base-url".equals(normalized)) {
                parsed.baseUrl = value;
            } else if ("workspace".equals(normalized)) {
                parsed.workspace = value;
            } else {
                throw new IllegalArgumentException("unsupported option: --" + option);
            }
        }
        if (!parsed.help && parsed.blueprintPath == null) {
            throw new IllegalArgumentException("blueprint path is required");
        }
        if (parsed.blueprintPath != null && !Files.exists(parsed.blueprintPath)) {
            throw new IllegalArgumentException("blueprint file does not exist: " + parsed.blueprintPath);
        }
        return parsed;
    }

    private void printHelp(TerminalIO terminal) {
        terminal.println("ai4j-cli run");
        terminal.println("  Run a single-agent AI4J Agent Blueprint YAML once.\n");
        terminal.println("Usage:");
        terminal.println("  ai4j-cli run <agent.yaml> --input \"Your task\" [options]");
        terminal.println("  ai4j-cli run <agent.yaml> --prompt \"Your task\" [options]\n");
        terminal.println("Options:");
        terminal.println("  --input <text> / --prompt <text>      User input for one run");
        terminal.println("  --provider <name>                    Override YAML model.provider");
        terminal.println("  --protocol <chat|responses>          Override protocol");
        terminal.println("  --model <name>                       Override YAML model.model");
        terminal.println("  --profile <name>                     Host provider profile metadata");
        terminal.println("  --api-key <key>                      Host runtime key; prefer env/config");
        terminal.println("  --base-url <url>                     Runtime base URL for compatible providers");
        terminal.println("  --workspace <path>                   Workspace for provider/profile config lookup");
        terminal.println("  --allow-sandbox-declaration          Accept sandbox.enabled declarations without creating a sandbox");
        terminal.println("  --verbose                            Print stack traces for unexpected failures\n");
        terminal.println("Environment:");
        terminal.println("  AI4J_AGENT_INPUT, AI4J_PROVIDER, AI4J_PROTOCOL, AI4J_MODEL, AI4J_API_KEY, AI4J_BASE_URL");
        terminal.println("  Provider-specific keys also work, for example OPENAI_API_KEY / ZHIPU_API_KEY / MINIMAX_API_KEY\n");
        terminal.println("Notes:");
        terminal.println("  AgentFactory remains host-supplied: the YAML file does not store secrets, install plugins, or create real sandbox sessions.");
    }

    private String normalizeBlueprintProvider(String provider) {
        if (isBlank(provider)) {
            return null;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if ("openai-compatible".equals(normalized)) {
            return "openai";
        }
        return provider.trim();
    }

    private Path resolvePath(String value) {
        Path path = Paths.get(value);
        if (!path.isAbsolute()) {
            path = currentDirectory.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private void printValidationReport(AgentFactoryException ex, TerminalIO terminal) {
        if (ex == null || ex.getValidationReport() == null || ex.getValidationReport().getIssues().isEmpty()) {
            return;
        }
        for (AgentBlueprintValidationIssue issue : ex.getValidationReport().getIssues()) {
            terminal.errorln("  " + issue.getSeverity() + " " + issue.getPath() + " " + issue.getCode() + ": " + issue.getMessage());
        }
    }

    private String safeOutput(AgentResult result) {
        if (result == null || isBlank(result.getOutputText())) {
            return "";
        }
        return result.getOutputText();
    }

    private boolean isVerbose(List<String> args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--verbose".equals(arg) || (arg != null && arg.startsWith("--verbose="))) {
                return true;
            }
        }
        return false;
    }

    private boolean parseBooleanFlag(List<String> args, int index, String value) {
        if (value != null) {
            return parseBoolean(value);
        }
        if (args != null && index + 1 < args.size() && isBooleanLiteral(args.get(index + 1))) {
            return parseBoolean(args.get(index + 1));
        }
        return true;
    }

    private boolean isBooleanLiteral(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                || "1".equals(value) || "0".equals(value)
                || "yes".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value);
    }

    private boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("invalid boolean value: " + value);
    }

    private String normalizeOptionName(String option) {
        return option == null ? null : option.trim().toLowerCase(Locale.ROOT);
    }

    private String envValue(String name) {
        return env == null ? null : env.get(name);
    }

    private String propertyValue(String name) {
        return properties == null ? null : properties.getProperty(name);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean equalsValue(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return isBlank(message) ? (throwable == null ? "unknown" : throwable.getClass().getSimpleName()) : message.trim();
    }

    private String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }

    private static final class ParsedRunArguments {
        private boolean help;
        private Path blueprintPath;
        private String workspace;
        private String provider;
        private String protocol;
        private String profile;
        private String model;
        private String apiKey;
        private String baseUrl;
        private String input;
        private boolean allowSandboxDeclaration;
        private boolean verbose;
    }
}
