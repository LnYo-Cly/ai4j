package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.cli.hook.TrustedDirsStore;
import io.github.lnyocly.ai4j.cli.hook.WorkspaceTrustGate;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Manages trusted workspace directories for {@code .ai4j/workspace.json} hooks.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code ai4j-cli trust --dir <path>} — trust a workspace so its hooks load without prompting</li>
 *   <li>{@code ai4j-cli trust --revoke <path>} — revoke trust so hooks are skipped</li>
 *   <li>{@code ai4j-cli trust --list} — list all trusted directories</li>
 * </ul>
 */
public class TrustCommand {

    private final WorkspaceTrustGate trustGate;
    private final TrustedDirsStore store;

    public TrustCommand() {
        this(new TrustedDirsStore());
    }

    public TrustCommand(TrustedDirsStore store) {
        this.store = store;
        this.trustGate = new WorkspaceTrustGate(store);
    }

    public int run(List<String> args, TerminalIO terminal) {
        if (args == null || args.isEmpty()) {
            printHelp(terminal);
            return 0;
        }

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            String option = arg;
            String inlineValue = null;
            int eq = arg.indexOf('=');
            if (eq >= 0) {
                option = arg.substring(0, eq);
                inlineValue = arg.substring(eq + 1);
            }

            if ("-h".equals(option) || "--help".equals(option)) {
                printHelp(terminal);
                return 0;
            }
            if ("--list".equals(option)) {
                return listTrusted(terminal);
            }
            if ("--dir".equals(option) || "--add".equals(option)) {
                String dir = inlineValue != null ? inlineValue
                        : (i + 1 < args.size() ? args.get(++i) : null);
                if (dir == null) {
                    terminal.errorln("--dir requires a path argument");
                    return 2;
                }
                return trustDir(dir, terminal);
            }
            if ("--revoke".equals(option) || "--remove".equals(option)) {
                String dir = inlineValue != null ? inlineValue
                        : (i + 1 < args.size() ? args.get(++i) : null);
                if (dir == null) {
                    terminal.errorln("--revoke requires a path argument");
                    return 2;
                }
                return revokeDir(dir, terminal);
            }

            terminal.errorln("Unknown option: " + arg);
            printHelp(terminal);
            return 2;
        }

        printHelp(terminal);
        return 0;
    }

    private int listTrusted(TerminalIO terminal) {
        List<String> dirs = store.getTrustedDirs();
        if (dirs.isEmpty()) {
            terminal.println("No trusted workspace directories.");
            return 0;
        }
        terminal.println("Trusted workspace directories (" + dirs.size() + "):");
        for (String dir : dirs) {
            terminal.println("  " + dir);
        }
        return 0;
    }

    private int trustDir(String dir, TerminalIO terminal) {
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        try {
            trustGate.trustDirectory(path);
            terminal.println("Trusted: " + path);
            return 0;
        } catch (Exception e) {
            terminal.errorln("Failed to trust directory: " + e.getMessage());
            return 1;
        }
    }

    private int revokeDir(String dir, TerminalIO terminal) {
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        try {
            boolean removed = trustGate.revokeDirectory(path);
            if (removed) {
                terminal.println("Revoked: " + path);
            } else {
                terminal.println("Directory was not trusted: " + path);
            }
            return 0;
        } catch (Exception e) {
            terminal.errorln("Failed to revoke directory: " + e.getMessage());
            return 1;
        }
    }

    private void printHelp(TerminalIO terminal) {
        terminal.println("ai4j-cli trust");
        terminal.println("  Manage trusted workspace directories for .ai4j/workspace.json hooks.\n");
        terminal.println("Usage:");
        terminal.println("  ai4j-cli trust --dir <path>     Trust a workspace directory");
        terminal.println("  ai4j-cli trust --revoke <path>  Revoke trust for a workspace directory");
        terminal.println("  ai4j-cli trust --list           List all trusted directories\n");
        terminal.println("Trusted directories are stored in ~/.ai4j/trusted-dirs.txt.");
    }
}
