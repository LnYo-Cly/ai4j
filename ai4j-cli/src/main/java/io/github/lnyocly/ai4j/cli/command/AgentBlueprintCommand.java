package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintSchemas;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AgentBlueprintCommand {

    private final Path currentDirectory;

    public AgentBlueprintCommand(Path currentDirectory) {
        this.currentDirectory = currentDirectory == null ? Paths.get(".").toAbsolutePath().normalize() : currentDirectory;
    }

    public int run(List<String> args, TerminalIO terminal) {
        ParsedArguments parsed;
        try {
            parsed = parse(args);
        } catch (IllegalArgumentException ex) {
            terminal.errorln("Argument error: " + safeMessage(ex));
            printHelp(terminal);
            return 2;
        }
        if (parsed.help) {
            printHelp(terminal);
            return 0;
        }
        if ("schema".equals(parsed.action)) {
            return printOrWriteSchema(parsed, terminal);
        }
        terminal.errorln("Unknown blueprint action: " + parsed.action);
        printHelp(terminal);
        return 2;
    }

    private int printOrWriteSchema(ParsedArguments parsed, TerminalIO terminal) {
        try {
            if (parsed.outputPath == null) {
                terminal.println(AgentBlueprintSchemas.v1JsonSchema());
                return 0;
            }
            AgentBlueprintSchemas.writeV1JsonSchema(parsed.outputPath);
            terminal.println("Agent Blueprint JSON Schema written: " + parsed.outputPath);
            return 0;
        } catch (Exception ex) {
            terminal.errorln("Blueprint schema export failed: " + safeMessage(ex));
            return 1;
        }
    }

    private ParsedArguments parse(List<String> args) {
        ParsedArguments parsed = new ParsedArguments();
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
            if (arg == null || arg.trim().length() == 0) {
                continue;
            }
            if (!arg.startsWith("--")) {
                if (parsed.action != null) {
                    throw new IllegalArgumentException("unexpected argument: " + arg);
                }
                parsed.action = arg.trim().toLowerCase(java.util.Locale.ROOT);
                continue;
            }
            String option = arg.substring(2);
            String value = null;
            int equalsIndex = option.indexOf('=');
            if (equalsIndex >= 0) {
                value = option.substring(equalsIndex + 1);
                option = option.substring(0, equalsIndex);
            }
            String normalized = option.trim().toLowerCase(java.util.Locale.ROOT);
            if ("out".equals(normalized) || "output".equals(normalized) || "output-file".equals(normalized)) {
                if (value == null) {
                    if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                        throw new IllegalArgumentException("missing value for --" + option);
                    }
                    value = args.get(++i);
                }
                parsed.outputPath = resolvePath(value);
                continue;
            }
            throw new IllegalArgumentException("unsupported option: --" + option);
        }
        if (parsed.action == null) {
            parsed.help = true;
        }
        return parsed;
    }

    private Path resolvePath(String value) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("output path must not be blank");
        }
        Path path = Paths.get(value);
        if (!path.isAbsolute()) {
            path = currentDirectory.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private void printHelp(TerminalIO terminal) {
        terminal.println("ai4j-cli blueprint");
        terminal.println("  Inspect Agent Blueprint authoring helpers.\n");
        terminal.println("Usage:");
        terminal.println("  ai4j-cli blueprint schema");
        terminal.println("  ai4j-cli blueprint schema --out agent-blueprint.schema.json\n");
        terminal.println("Commands:");
        terminal.println("  schema    Print the bundled Agent Blueprint JSON Schema, or write it with --out\n");
        terminal.println("Notes:");
        terminal.println("  The schema is an authoring aid for IDE/YAML validation. Runtime validation still uses AgentBlueprintValidator.");
        terminal.println("  Do not store provider tokens, cookies, or local-only secrets in Blueprint YAML.");
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().length() == 0 ? "unknown" : message.trim();
    }

    private static final class ParsedArguments {
        private boolean help;
        private String action;
        private Path outputPath;
    }
}
