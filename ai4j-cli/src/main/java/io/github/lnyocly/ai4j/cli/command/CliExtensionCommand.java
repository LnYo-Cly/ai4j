package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionInspectionSnapshot;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CliExtensionCommand {

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
        terminal.println("  ai4j-cli extension inspect <id> [--runtime]\n");
        terminal.println("Commands:");
        terminal.println("  list                 List discovered extension manifests");
        terminal.println("  inspect <id>         Show manifest, permissions, config prefix, and source class");
        terminal.println("  inspect <id> --runtime  Also list contributed tools, commands, skills, prompts, and guardrails");
        terminal.println("\nNotes:");
        terminal.println("  Discovery does not enable an extension.");
        terminal.println("  Runtime inspection is temporary and does not expose tools to an agent.");
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class InspectOptions {
        private final String extensionId;
        private final boolean runtime;

        private InspectOptions(String extensionId, boolean runtime) {
            this.extensionId = extensionId;
            this.runtime = runtime;
        }
    }
}
