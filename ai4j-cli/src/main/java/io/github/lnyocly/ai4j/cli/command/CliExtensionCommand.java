package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionInspectionSnapshot;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.resource.ExtensionResourceResolver;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationIssue;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CliExtensionCommand {

    private static final String EXTENSION_API_VERSION = "2.3.0";

    private final Path currentDirectory;

    public CliExtensionCommand() {
        this(Paths.get(".").toAbsolutePath().normalize());
    }

    public CliExtensionCommand(Path currentDirectory) {
        this.currentDirectory = currentDirectory == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : currentDirectory.toAbsolutePath().normalize();
    }

    public int run(List<String> args, TerminalIO terminal) {
        List<String> arguments = args == null ? Collections.<String>emptyList() : args;
        if (arguments.isEmpty() || isHelp(arguments.get(0))) {
            printHelp(terminal);
            return 0;
        }
        String command = arguments.get(0);
        try {
            if ("list".equalsIgnoreCase(command)) {
                if (arguments.size() > 1) {
                    throw new IllegalArgumentException("unexpected argument for list: " + arguments.get(1));
                }
                return list(terminal);
            }
            if ("inspect".equalsIgnoreCase(command)) {
                return inspect(arguments.subList(1, arguments.size()), terminal);
            }
            if ("init".equalsIgnoreCase(command)) {
                return init(arguments.subList(1, arguments.size()), terminal);
            }
            if ("validate".equalsIgnoreCase(command)) {
                return validate(arguments.subList(1, arguments.size()), terminal);
            }
            if ("run".equalsIgnoreCase(command)) {
                return runExtensionCommand(arguments.subList(1, arguments.size()), terminal);
            }
            if ("resource".equalsIgnoreCase(command)) {
                return readExtensionResource(arguments.subList(1, arguments.size()), terminal);
            }
            terminal.errorln("Unknown extension command: " + command);
            printHelp(terminal);
            return 2;
        } catch (IllegalArgumentException ex) {
            terminal.errorln("Argument error: " + safeMessage(ex));
            printHelp(terminal);
            return 2;
        } catch (ExtensionException ex) {
            terminal.errorln("Extension error: " + safeMessage(ex));
            return 2;
        } catch (Exception ex) {
            terminal.errorln("Extension command failed: " + safeMessage(ex));
            return 1;
        }
    }

    private int list(TerminalIO terminal) {
        ExtensionRegistry registry = ExtensionRegistry.discover();
        List<DiscoveredExtension> discovered = registry.list();
        terminal.println("extensions:");
        terminal.println("count=" + discovered.size());
        for (DiscoveredExtension extension : discovered) {
            ExtensionManifest manifest = extension.getManifest();
            terminal.println("- id=" + manifest.getId()
                    + " name=" + valueOrDash(manifest.getName())
                    + " version=" + valueOrDash(manifest.getVersion())
                    + " capabilities=" + joinCapabilities(manifest.getCapabilities())
                    + " source=" + extension.getSourceClassName());
        }
        return 0;
    }

    private int validate(List<String> args, TerminalIO terminal) {
        ValidateOptions options = parseValidateOptions(args);
        ExtensionRegistry registry = ExtensionRegistry.discover();
        List<ExtensionValidationReport> reports = options.all
                ? ExtensionValidator.validateAll(registry)
                : Collections.singletonList(ExtensionValidator.validate(registry, options.extensionId));
        printValidationReports(reports, terminal);
        return hasValidationErrors(reports) ? 2 : 0;
    }

    private int init(List<String> args, TerminalIO terminal) throws IOException {
        ExtensionScaffoldGenerator.Options options = parseInitOptions(args);
        ExtensionScaffoldGenerator.Result result = ExtensionScaffoldGenerator.generate(options);
        terminal.println("extension scaffold:");
        terminal.println("path=" + result.getTargetDirectory());
        terminal.println("id=" + options.getExtensionId());
        terminal.println("package=" + options.getPackageName());
        terminal.println("class=" + options.getClassName());
        terminal.println("files=" + result.getCreatedFiles().size());
        terminal.println("next=mvn test");
        return 0;
    }

    private int runExtensionCommand(List<String> args, TerminalIO terminal) throws Exception {
        RunOptions options = parseRunOptions(args);
        ExtensionRegistry registry = ExtensionRegistry.discover()
                .enableAll(options.enabledExtensionIds);
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionCommandHandler handler = snapshot.getCommandHandlers().get(options.commandName);
        if (handler == null) {
            throw new ExtensionException("command not registered by enabled extensions: " + options.commandName);
        }
        String result = handler.handle(new ExtensionCommandRequest(options.commandName, joinCommandArguments(options.arguments)));
        if (!isBlank(result)) {
            terminal.println(result);
        }
        return 0;
    }

    private int readExtensionResource(List<String> args, TerminalIO terminal) {
        ResourceOptions options = parseResourceOptions(args);
        ExtensionRegistry registry = ExtensionRegistry.discover()
                .enableAll(options.enabledExtensionIds);
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        String content;
        if ("skill".equals(options.resourceType)) {
            ExtensionSkillResource skill = findSkill(snapshot.getSkills(), options.resourceName);
            if (skill == null) {
                throw new ExtensionException("skill not registered by enabled extensions: " + options.resourceName);
            }
            content = ExtensionResourceResolver.readText(
                    skill.getResourcePath(),
                    registry.getExtensionClassLoader(requireExtensionId(skill.getExtensionId(), "skill"))
            );
        } else if ("prompt".equals(options.resourceType)) {
            ExtensionPromptResource prompt = findPrompt(snapshot.getPrompts(), options.resourceName);
            if (prompt == null) {
                throw new ExtensionException("prompt not registered by enabled extensions: " + options.resourceName);
            }
            content = ExtensionResourceResolver.readText(
                    prompt.getResourcePath(),
                    registry.getExtensionClassLoader(requireExtensionId(prompt.getExtensionId(), "prompt"))
            );
        } else {
            throw new IllegalArgumentException("unsupported resource type: " + options.resourceType);
        }
        terminal.print(content == null ? "" : content);
        return 0;
    }

    private int inspect(List<String> args, TerminalIO terminal) {
        InspectOptions options = parseInspectOptions(args);
        ExtensionRegistry registry = ExtensionRegistry.discover();
        ExtensionManifest manifest = registry.inspect(options.extensionId);
        DiscoveredExtension discovered = find(registry.list(), options.extensionId);

        terminal.println("extension:");
        terminal.println("id=" + manifest.getId());
        terminal.println("name=" + valueOrDash(manifest.getName()));
        terminal.println("version=" + valueOrDash(manifest.getVersion()));
        terminal.println("vendor=" + valueOrDash(manifest.getVendor()));
        terminal.println("capabilities=" + joinCapabilities(manifest.getCapabilities()));
        terminal.println("permissions=" + joinValues(manifest.getPermissions()));
        terminal.println("configPrefix=" + valueOrDash(manifest.getConfigPrefix()));
        if (discovered != null) {
            terminal.println("source=" + discovered.getSourceClassName());
        }

        if (options.runtime) {
            ExtensionInspectionSnapshot snapshot = registry.inspectRuntime(options.extensionId);
            printRuntime(snapshot, terminal);
        } else {
            terminal.println("runtime=not-inspected");
            terminal.println("tip=use --runtime to list contributed tools, commands, skills, prompts, and guardrails");
        }
        return 0;
    }

    private ExtensionScaffoldGenerator.Options parseInitOptions(List<String> args) {
        if (args == null || args.isEmpty() || isHelp(args.get(0))) {
            throw new IllegalArgumentException("Usage: ai4j-cli extension init <directory> --id <extension-id> --package <java-package> [options]");
        }
        String directory = null;
        String extensionId = null;
        String packageName = null;
        String displayName = null;
        String groupId = null;
        String artifactId = null;
        String version = null;
        String className = null;
        String vendor = null;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg != null && arg.startsWith("--")) {
                String value = requireOptionValue(args, i, arg);
                i++;
                if ("--id".equals(arg)) {
                    extensionId = value;
                } else if ("--package".equals(arg)) {
                    packageName = value;
                } else if ("--name".equals(arg)) {
                    displayName = value;
                } else if ("--group-id".equals(arg)) {
                    groupId = value;
                } else if ("--artifact-id".equals(arg)) {
                    artifactId = value;
                } else if ("--version".equals(arg)) {
                    version = value;
                } else if ("--class-name".equals(arg)) {
                    className = value;
                } else if ("--vendor".equals(arg)) {
                    vendor = value;
                } else {
                    throw new IllegalArgumentException("unsupported option: " + arg);
                }
                continue;
            }
            if (directory != null) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            directory = arg;
        }
        if (isBlank(directory)) {
            throw new IllegalArgumentException("target directory is required");
        }
        if (isBlank(extensionId)) {
            throw new IllegalArgumentException("--id is required");
        }
        if (isBlank(packageName)) {
            throw new IllegalArgumentException("--package is required");
        }
        Path target = currentDirectory.resolve(directory).toAbsolutePath().normalize();
        return ExtensionScaffoldGenerator.Options.builder()
                .targetDirectory(target)
                .extensionId(extensionId)
                .packageName(packageName)
                .displayName(displayName)
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .className(className)
                .vendor(vendor)
                .extensionApiVersion(EXTENSION_API_VERSION)
                .build();
    }

    private String requireOptionValue(List<String> args, int index, String option) {
        if (index + 1 >= args.size()) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        String value = args.get(index + 1);
        if (isBlank(value) || value.startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return value;
    }

    private RunOptions parseRunOptions(List<String> args) {
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("Usage: ai4j-cli extension run --enable <extension-id> <command> [arguments...]");
        }
        List<String> enabledExtensionIds = new ArrayList<String>();
        List<String> commandArguments = new ArrayList<String>();
        String commandName = null;
        boolean passthrough = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!passthrough && "--".equals(arg)) {
                passthrough = true;
                continue;
            }
            if (commandName != null) {
                commandArguments.add(arg);
                continue;
            }
            if (!passthrough && ("--enable".equals(arg) || "--extension".equals(arg))) {
                if (i + 1 >= args.size()) {
                    throw new IllegalArgumentException(arg + " requires an extension id");
                }
                enabledExtensionIds.add(ExtensionManifest.requireId(args.get(++i), "extension id"));
                continue;
            }
            if (!passthrough && arg != null && arg.startsWith("--")) {
                throw new IllegalArgumentException("unsupported option: " + arg);
            }
            if (commandName == null) {
                commandName = normalizeCommandName(arg);
                continue;
            }
            commandArguments.add(arg);
        }
        if (enabledExtensionIds.isEmpty()) {
            throw new IllegalArgumentException("at least one --enable <extension-id> is required before running extension commands");
        }
        if (isBlank(commandName)) {
            throw new IllegalArgumentException("command name is required");
        }
        return new RunOptions(enabledExtensionIds, commandName, commandArguments);
    }

    private ResourceOptions parseResourceOptions(List<String> args) {
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("Usage: ai4j-cli extension resource --enable <extension-id> <skill|prompt> <name>");
        }
        List<String> enabledExtensionIds = new ArrayList<String>();
        String resourceType = null;
        String resourceName = null;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--enable".equals(arg) || "--extension".equals(arg)) {
                if (i + 1 >= args.size()) {
                    throw new IllegalArgumentException(arg + " requires an extension id");
                }
                enabledExtensionIds.add(ExtensionManifest.requireId(args.get(++i), "extension id"));
                continue;
            }
            if (arg != null && arg.startsWith("--")) {
                throw new IllegalArgumentException("unsupported option: " + arg);
            }
            if (resourceType == null) {
                resourceType = ExtensionManifest.requireId(arg, "resource type").toLowerCase();
                continue;
            }
            if (resourceName == null) {
                resourceName = ExtensionManifest.requireId(arg, "resource name");
                continue;
            }
            throw new IllegalArgumentException("unexpected argument: " + arg);
        }
        if (enabledExtensionIds.isEmpty()) {
            throw new IllegalArgumentException("at least one --enable <extension-id> is required before reading extension resources");
        }
        if (isBlank(resourceType)) {
            throw new IllegalArgumentException("resource type is required");
        }
        if (!"skill".equals(resourceType) && !"prompt".equals(resourceType)) {
            throw new IllegalArgumentException("unsupported resource type: " + resourceType);
        }
        if (isBlank(resourceName)) {
            throw new IllegalArgumentException("resource name is required");
        }
        return new ResourceOptions(enabledExtensionIds, resourceType, resourceName);
    }

    private ValidateOptions parseValidateOptions(List<String> args) {
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("Usage: ai4j-cli extension validate <id>|--all");
        }
        boolean all = false;
        String extensionId = null;
        for (String arg : args) {
            if ("--all".equals(arg)) {
                all = true;
                continue;
            }
            if (arg != null && arg.startsWith("--")) {
                throw new IllegalArgumentException("unsupported option: " + arg);
            }
            if (extensionId != null) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            extensionId = arg;
        }
        if (all && !isBlank(extensionId)) {
            throw new IllegalArgumentException("validate accepts either <id> or --all, not both");
        }
        if (!all && isBlank(extensionId)) {
            throw new IllegalArgumentException("extension id is required");
        }
        return new ValidateOptions(all, all ? null : ExtensionManifest.requireId(extensionId, "extension id"));
    }

    private InspectOptions parseInspectOptions(List<String> args) {
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("Usage: ai4j-cli extension inspect <id> [--runtime]");
        }
        String extensionId = null;
        boolean runtime = false;
        for (String arg : args) {
            if ("--runtime".equals(arg)) {
                runtime = true;
                continue;
            }
            if (arg != null && arg.startsWith("--")) {
                throw new IllegalArgumentException("unsupported option: " + arg);
            }
            if (extensionId != null) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            extensionId = arg;
        }
        if (isBlank(extensionId)) {
            throw new IllegalArgumentException("extension id is required");
        }
        return new InspectOptions(ExtensionManifest.requireId(extensionId, "extension id"), runtime);
    }

    private void printRuntime(ExtensionInspectionSnapshot snapshot, TerminalIO terminal) {
        terminal.println("runtime=inspected");
        terminal.println("tools=" + joinTools(snapshot.getTools()));
        terminal.println("commands=" + joinCommands(snapshot.getCommands()));
        terminal.println("skills=" + joinSkills(snapshot.getSkills()));
        terminal.println("prompts=" + joinPrompts(snapshot.getPrompts()));
        terminal.println("guardrails=" + joinValues(snapshot.getGuardrails()));
    }

    private void printValidationReports(List<ExtensionValidationReport> reports, TerminalIO terminal) {
        List<ExtensionValidationReport> safeReports = reports == null
                ? Collections.<ExtensionValidationReport>emptyList()
                : reports;
        terminal.println("validation:");
        terminal.println("count=" + safeReports.size());
        for (ExtensionValidationReport report : safeReports) {
            terminal.println("- id=" + report.getExtensionId()
                    + " status=" + report.getStatus()
                    + " errors=" + report.getErrorCount()
                    + " warnings=" + report.getWarningCount()
                    + " source=" + valueOrDash(report.getSourceClassName()));
            if (report.getIssues().isEmpty()) {
                terminal.println("  issues=-");
                continue;
            }
            terminal.println("  issues:");
            for (ExtensionValidationIssue issue : report.getIssues()) {
                terminal.println("  - severity=" + issue.getSeverity().getId()
                        + " code=" + issue.getCode()
                        + " target=" + valueOrDash(issue.getTarget())
                        + " message=" + issue.getMessage());
            }
        }
    }

    private boolean hasValidationErrors(List<ExtensionValidationReport> reports) {
        if (reports == null) {
            return false;
        }
        for (ExtensionValidationReport report : reports) {
            if (report != null && !report.isValid()) {
                return true;
            }
        }
        return false;
    }

    private String joinTools(List<ExtensionToolSpec> tools) {
        if (tools == null || tools.isEmpty()) {
            return "-";
        }
        List<String> values = new ArrayList<String>();
        for (ExtensionToolSpec tool : tools) {
            values.add(tool.getName());
        }
        return joinValues(values);
    }

    private String joinCommands(List<ExtensionCommandSpec> commands) {
        if (commands == null || commands.isEmpty()) {
            return "-";
        }
        List<String> values = new ArrayList<String>();
        for (ExtensionCommandSpec command : commands) {
            values.add(command.getName());
        }
        return joinValues(values);
    }

    private String joinSkills(List<ExtensionSkillResource> skills) {
        if (skills == null || skills.isEmpty()) {
            return "-";
        }
        List<String> values = new ArrayList<String>();
        for (ExtensionSkillResource skill : skills) {
            values.add(skill.getName() + "@" + skill.getResourcePath());
        }
        return joinValues(values);
    }

    private String joinPrompts(List<ExtensionPromptResource> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return "-";
        }
        List<String> values = new ArrayList<String>();
        for (ExtensionPromptResource prompt : prompts) {
            values.add(prompt.getName() + "@" + prompt.getResourcePath());
        }
        return joinValues(values);
    }

    private ExtensionSkillResource findSkill(List<ExtensionSkillResource> skills, String name) {
        if (skills == null) {
            return null;
        }
        for (ExtensionSkillResource skill : skills) {
            if (skill != null && skill.getName().equals(name)) {
                return skill;
            }
        }
        return null;
    }

    private ExtensionPromptResource findPrompt(List<ExtensionPromptResource> prompts, String name) {
        if (prompts == null) {
            return null;
        }
        for (ExtensionPromptResource prompt : prompts) {
            if (prompt != null && prompt.getName().equals(name)) {
                return prompt;
            }
        }
        return null;
    }

    private String joinCapabilities(Iterable<ExtensionCapability> capabilities) {
        if (capabilities == null) {
            return "-";
        }
        List<String> values = new ArrayList<String>();
        for (ExtensionCapability capability : capabilities) {
            if (capability != null) {
                values.add(capability.getId());
            }
        }
        return joinValues(values);
    }

    private String joinValues(Iterable<String> values) {
        if (values == null) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(value.trim());
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private DiscoveredExtension find(List<DiscoveredExtension> extensions, String extensionId) {
        if (extensions == null) {
            return null;
        }
        for (DiscoveredExtension extension : extensions) {
            if (extension != null && extension.getManifest().getId().equals(extensionId)) {
                return extension;
            }
        }
        return null;
    }

    private void printHelp(TerminalIO terminal) {
        terminal.println("ai4j-cli extension");
        terminal.println("  Inspect AI4J extension packages discovered on the current classpath.\n");
        terminal.println("Usage:");
        terminal.println("  ai4j-cli extension list");
        terminal.println("  ai4j-cli extension inspect <id> [--runtime]");
        terminal.println("  ai4j-cli extension init <directory> --id <extension-id> --package <java-package> [options]");
        terminal.println("  ai4j-cli extension validate <id>|--all");
        terminal.println("  ai4j-cli extension run --enable <extension-id> <command> [arguments...]");
        terminal.println("  ai4j-cli extension resource --enable <extension-id> <skill|prompt> <name>\n");
        terminal.println("Commands:");
        terminal.println("  list                 List discovered extension manifests");
        terminal.println("  inspect <id>         Show manifest, permissions, config prefix, and source class");
        terminal.println("  inspect <id> --runtime  Also list contributed tools, commands, skills, prompts, and guardrails");
        terminal.println("  init <directory>     Generate a local Maven Java 8 plugin package scaffold");
        terminal.println("  validate <id>|--all  Validate manifest, runtime resources, and authoring contract");
        terminal.println("  run --enable <id> <command>  Execute a command from explicitly enabled extensions");
        terminal.println("  resource --enable <id> <skill|prompt> <name>  Print a contributed resource from enabled extensions");
        terminal.println("\nInit options:");
        terminal.println("  --id <extension-id>       Required stable plugin id, for example weather-pack");
        terminal.println("  --package <java-package>  Required Java package, for example com.example.ai4j.weather");
        terminal.println("  --name <display-name>     Human-readable plugin name");
        terminal.println("  --group-id <group-id>     Maven groupId; defaults to --package");
        terminal.println("  --artifact-id <artifact>  Maven artifactId; defaults to --id");
        terminal.println("  --version <version>       Maven and manifest version; defaults to 1.0.0");
        terminal.println("  --class-name <class>      Extension class name; defaults from --id");
        terminal.println("  --vendor <vendor>         Manifest vendor; defaults to example");
        terminal.println("\nNotes:");
        terminal.println("  Discovery does not enable an extension.");
        terminal.println("  init writes only into a missing or empty directory; it does not install dependencies.");
        terminal.println("  Runtime inspection is temporary and does not expose tools to an agent.");
        terminal.println("  Running a command requires --enable so classpath discovery never executes commands implicitly.");
        terminal.println("  Reading a resource also requires --enable; the command prints raw UTF-8 classpath content.");
    }

    private boolean isHelp(String value) {
        return "help".equalsIgnoreCase(value) || "-h".equals(value) || "--help".equals(value);
    }

    private String valueOrDash(String value) {
        return isBlank(value) ? "-" : value.trim();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return isBlank(message) ? "unknown error" : message.trim();
    }

    private String normalizeCommandName(String value) {
        String normalized = ExtensionManifest.requireId(value, "command name");
        if (normalized.startsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String joinCommandArguments(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (value != null) {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String requireExtensionId(String extensionId, String resourceType) {
        if (isBlank(extensionId)) {
            throw new ExtensionException("extension " + resourceType + " resource is missing extension id");
        }
        return extensionId.trim();
    }

    private static final class InspectOptions {
        private final String extensionId;
        private final boolean runtime;

        private InspectOptions(String extensionId, boolean runtime) {
            this.extensionId = extensionId;
            this.runtime = runtime;
        }
    }

    private static final class ValidateOptions {
        private final boolean all;
        private final String extensionId;

        private ValidateOptions(boolean all, String extensionId) {
            this.all = all;
            this.extensionId = extensionId;
        }
    }

    private static final class RunOptions {
        private final List<String> enabledExtensionIds;
        private final String commandName;
        private final List<String> arguments;

        private RunOptions(List<String> enabledExtensionIds, String commandName, List<String> arguments) {
            this.enabledExtensionIds = enabledExtensionIds == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(enabledExtensionIds));
            this.commandName = commandName;
            this.arguments = arguments == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(arguments));
        }
    }

    private static final class ResourceOptions {
        private final List<String> enabledExtensionIds;
        private final String resourceType;
        private final String resourceName;

        private ResourceOptions(List<String> enabledExtensionIds, String resourceType, String resourceName) {
            this.enabledExtensionIds = enabledExtensionIds == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(enabledExtensionIds));
            this.resourceType = resourceType;
            this.resourceName = resourceName;
        }
    }
}
