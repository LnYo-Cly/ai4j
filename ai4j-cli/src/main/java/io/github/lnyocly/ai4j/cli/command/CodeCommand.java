package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.cli.render.CliAnsi;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.SlashCommandController;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.CodingCliTuiFactory;
import io.github.lnyocly.ai4j.cli.factory.DefaultCodingCliTuiFactory;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.runtime.CodingCliSessionRunner;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.FileCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.FileSessionEventStore;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.cli.shell.JlineCodeCommandRunner;
import io.github.lnyocly.ai4j.cli.shell.JlineShellContext;
import io.github.lnyocly.ai4j.cli.shell.JlineShellTerminalIO;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.tui.JlineTerminalIO;
import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CodeCommand {

    private enum InteractiveBackend {
        LEGACY,
        JLINE
    }

    private final CodingCliAgentFactory agentFactory;
    private final CodingCliTuiFactory tuiFactory;
    private final Map<String, String> env;
    private final Properties properties;
    private final Path currentDirectory;
    private final CodeCommandOptionsParser parser = new CodeCommandOptionsParser();

    public CodeCommand(CodingCliAgentFactory agentFactory,
                       Map<String, String> env,
                       Properties properties,
                       Path currentDirectory) {
        this(agentFactory, new DefaultCodingCliTuiFactory(), env, properties, currentDirectory);
    }

    public CodeCommand(CodingCliAgentFactory agentFactory,
                       CodingCliTuiFactory tuiFactory,
                       Map<String, String> env,
                       Properties properties,
                       Path currentDirectory) {
        this.agentFactory = agentFactory;
        this.tuiFactory = tuiFactory == null ? new DefaultCodingCliTuiFactory() : tuiFactory;
        this.env = env;
        this.properties = properties;
        this.currentDirectory = currentDirectory;
    }

    public int run(List<String> args, TerminalIO terminal) {
        CodeCommandOptions options = null;
        TerminalIO runtimeTerminal = terminal;
        JlineShellContext shellContext = null;
        SlashCommandController slashCommandController = null;
        try {
            options = parser.parse(args, env, properties, currentDirectory);
            if (options.isHelp()) {
                printHelp(terminal);
                return 0;
            }
            CodingSessionManager sessionManager = createSessionManager(options);
            InteractiveBackend interactiveBackend = resolveInteractiveBackend(options, terminal);
            if (interactiveBackend == InteractiveBackend.JLINE) {
                closeQuietly(terminal);
                slashCommandController = new SlashCommandController(
                        new CustomCommandRegistry(Paths.get(options.getWorkspace())),
                        new io.github.lnyocly.ai4j.tui.TuiConfigManager(Paths.get(options.getWorkspace()))
                );
                shellContext = JlineShellContext.openSystem(slashCommandController);
                runtimeTerminal = new JlineShellTerminalIO(shellContext, slashCommandController);
            }
            TuiInteractionState interactionState = new TuiInteractionState();
            CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(
                    options,
                    runtimeTerminal,
                    interactionState,
                    java.util.Collections.<String>emptySet()
            );
            printMcpStartupWarnings(runtimeTerminal, prepared.getMcpRuntimeManager());
            CodingAgent agent = prepared.getAgent();
            if (interactiveBackend == InteractiveBackend.JLINE) {
                JlineCodeCommandRunner runner = new JlineCodeCommandRunner(
                        agent,
                        prepared.getProtocol(),
                        prepared.getMcpRuntimeManager(),
                        options,
                        runtimeTerminal,
                        sessionManager,
                        interactionState,
                        shellContext,
                        slashCommandController,
                        agentFactory,
                        env,
                        properties
                );
                shellContext = null;
                return runner.runCommand();
            }
            CodingCliSessionRunner runner = new CodingCliSessionRunner(agent,
                    prepared.getProtocol(),
                    options,
                    runtimeTerminal,
                    sessionManager,
                    interactionState,
                    tuiFactory,
                    null,
                    agentFactory,
                    env,
                    properties);
            runner.setMcpRuntimeManager(prepared.getMcpRuntimeManager());
            return runner.run();
        } catch (IllegalArgumentException ex) {
            terminal.errorln("Argument error: " + ex.getMessage());
            printHelp(terminal);
            return 2;
        } catch (Exception ex) {
            terminal.errorln("CLI failed: " + safeMessage(ex));
            if (options != null && options.isVerbose()) {
                terminal.errorln(stackTraceOf(ex));
            }
            return 1;
        } finally {
            if (shellContext != null) {
                try {
                    shellContext.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void printHelp(TerminalIO terminal) {
        terminal.println("ai4j-cli code");
        terminal.println("  Start a minimal coding session in one-shot or interactive REPL mode.\n");
        terminal.println("Usage:");
        terminal.println("  ai4j-cli code --model <model> [options]");
        terminal.println("  ai4j-cli code --model <model> --prompt \"Fix the failing tests in this workspace\"\n");
        terminal.println("Core options:");
        terminal.println("  --ui <cli|tui>                     Interaction mode, default: cli");
        terminal.println("  --provider <name>                  Provider name, default: openai");
        terminal.println("  --protocol <chat|responses>       Protocol family; provider default is used when omitted");
        terminal.println("  --model <name>                     Model name, required unless config provides one");
        terminal.println("  --api-key <key>                    API key, or use env/config provider profiles");
        terminal.println("  --base-url <url>                   Custom baseUrl for OpenAI-compatible providers");
        terminal.println("  --workspace <path>                 Workspace root, default: current directory");
        terminal.println("  --workspace-description <text>     Extra workspace description");
        terminal.println("  --prompt <text>                    One-shot mode; omit to enter interactive mode");
        terminal.println("  --system <text>                    Additional system prompt");
        terminal.println("  --instructions <text>              Additional instructions");
        terminal.println("  --theme <name>                     TUI theme name or override");
        terminal.println("  --approval <auto|safe|manual>     Tool approval strategy, default: auto");
        terminal.println("  --session-id <id>                  Use a fixed session id for a new session");
        terminal.println("  --resume <id>                      Resume a saved session");
        terminal.println("  --fork <id>                        Fork a saved session into a new branch");
        terminal.println("  --no-session                       Keep session state in memory only");
        terminal.println("  --session-dir <path>               Session store directory, default: <workspace>/.ai4j/sessions\n");
        terminal.println("Advanced options:");
        terminal.println("  --max-steps <n>                    Default: unlimited (0)");
        terminal.println("  --temperature <n>");
        terminal.println("  --top-p <n>");
        terminal.println("  --max-output-tokens <n>");
        terminal.println("  --parallel-tool-calls <bool>       Default: false");
        terminal.println("  --auto-save-session <bool>         Default: true");
        terminal.println("  --auto-compact <bool>              Default: true");
        terminal.println("  --compact-context-window-tokens <n>  Default: 128000");
        terminal.println("  --compact-reserve-tokens <n>       Default: 16384");
        terminal.println("  --compact-keep-recent-tokens <n>   Default: 20000");
        terminal.println("  --compact-summary-max-output-tokens <n>  Default: 400");
        terminal.println("  --allow-outside-workspace          Allow explicit paths outside the workspace");
        terminal.println("  --verbose                          Enable detailed CLI logs\n");
        terminal.println("Environment variables:");
        terminal.println("  AI4J_UI, AI4J_PROVIDER, AI4J_PROTOCOL, AI4J_MODEL, AI4J_API_KEY, AI4J_BASE_URL");
        terminal.println("  AI4J_WORKSPACE, AI4J_SYSTEM_PROMPT, AI4J_INSTRUCTIONS, AI4J_PROMPT");
        terminal.println("  AI4J_SESSION_ID, AI4J_RESUME_SESSION, AI4J_FORK_SESSION, AI4J_NO_SESSION, AI4J_SESSION_DIR, AI4J_THEME, AI4J_APPROVAL, AI4J_AUTO_SAVE_SESSION");
        terminal.println("  AI4J_AUTO_COMPACT, AI4J_COMPACT_CONTEXT_WINDOW_TOKENS, AI4J_COMPACT_RESERVE_TOKENS");
        terminal.println("  AI4J_COMPACT_KEEP_RECENT_TOKENS, AI4J_COMPACT_SUMMARY_MAX_OUTPUT_TOKENS");
        terminal.println("  Provider-specific keys also work, for example ZHIPU_API_KEY / OPENAI_API_KEY\n");
        terminal.println("Interactive commands:");
        terminal.println("  /help    Show in-session help");
        terminal.println("  /status  Show current session status");
        terminal.println("  /session Show current session metadata");
        terminal.println("  /theme [name]  Show or switch the active TUI theme");
        terminal.println("  /save    Persist the current session state");
        terminal.println("  /providers  List saved provider profiles");
        terminal.println("  /provider  Show current provider/profile state");
        terminal.println("  /provider use <name>  Switch workspace to a saved provider profile");
        terminal.println("  /provider save <name>  Save the current runtime as a provider profile");
        terminal.println("  /provider add <name> [options]  Create a provider profile from explicit fields");
        terminal.println("  /provider edit <name> [options]  Update a saved provider profile");
        terminal.println("  /provider default <name|clear>  Set or clear the global default profile");
        terminal.println("  /provider remove <name>  Delete a saved provider profile");
        terminal.println("  /model  Show current model/profile state");
        terminal.println("  /model <name>  Save a workspace model override");
        terminal.println("  /model reset  Clear the workspace model override");
        terminal.println("  /skills [name]  List discovered coding skills or inspect one skill");
        terminal.println("  /commands  List custom command templates");
        terminal.println("  /palette  Alias of /commands");
        terminal.println("  /cmd <name> [args]  Run a custom command template");
        terminal.println("  /sessions  List saved sessions in the current store");
        terminal.println("  /history [id]  Show session lineage from root to target");
        terminal.println("  /tree [id]     Show the saved session tree");
        terminal.println("  /events [n]  Show the latest session ledger events");
        terminal.println("  /replay [n]  Replay recent turns from the event ledger");
        terminal.println("  /team  Show the current agent team board by member lane");
        terminal.println("  /stream [on|off]  Show or switch model request streaming");
        terminal.println("  /processes  List active and restored process metadata");
        terminal.println("  /process status <id>  Show metadata for one process");
        terminal.println("  /process follow <id> [limit]  Show metadata with buffered logs");
        terminal.println("  /process logs <id> [limit]  Read buffered process logs");
        terminal.println("  /process write <id> <text>  Write to process stdin");
        terminal.println("  /process stop <id>  Stop a live process");
        terminal.println("  /checkpoint  Show the current structured checkpoint summary");
        terminal.println("  /resume <id>  Resume a saved session");
        terminal.println("  /load <id>    Alias of /resume");
        terminal.println("  /fork [new-id] or /fork <source-id> <new-id>  Fork a session branch");
        terminal.println("  /compact [summary]  Compact current session memory");
        terminal.println("  /clear   Print a new screen section");
        terminal.println("  /exit    Exit the session");
        terminal.println("  /quit    Exit the session");
        terminal.println("  TUI keys: / opens command list, Tab accepts completion, Ctrl+P palette, Ctrl+R replay, /team opens the team board, Enter submit, Esc interrupts an active raw-TUI turn or clears input\n");
        terminal.println("Examples:");
        terminal.println("  ai4j-cli code --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace .");
        terminal.println("  ai4j-cli code --ui tui --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace .");
        terminal.println("  ai4j-cli code --provider openai --protocol responses --model gpt-5-mini --prompt \"Read README and summarize the project structure\"");
        terminal.println("  ai4j-cli code --provider openai --base-url https://api.deepseek.com --protocol chat --model deepseek-chat");
    }

    private void printMcpStartupWarnings(TerminalIO terminal, CliMcpRuntimeManager runtimeManager) {
        if (terminal == null || runtimeManager == null) {
            return;
        }
        List<String> warnings = runtimeManager.buildStartupWarnings();
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        boolean ansi = terminal.supportsAnsi();
        for (String warning : warnings) {
            terminal.println(CliAnsi.warning("Warning: " + safeMessage(warning), ansi));
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private String safeMessage(String value) {
        return value == null || value.trim().isEmpty() ? "unknown warning" : value.trim();
    }

    private String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }

    private CodingSessionManager createSessionManager(CodeCommandOptions options) {
        if (options.isNoSession()) {
            Path directory = Paths.get(options.getWorkspace()).resolve(".ai4j").resolve("memory-sessions");
            return new DefaultCodingSessionManager(
                    new InMemoryCodingSessionStore(directory),
                    new InMemorySessionEventStore()
            );
        }
        Path sessionDirectory = Paths.get(options.getSessionStoreDir());
        return new DefaultCodingSessionManager(
                new FileCodingSessionStore(sessionDirectory),
                new FileSessionEventStore(sessionDirectory.resolve("events"))
        );
    }

    private InteractiveBackend resolveInteractiveBackend(CodeCommandOptions options, TerminalIO terminal) {
        if (options == null || terminal == null) {
            return InteractiveBackend.LEGACY;
        }
        if (options.getUiMode() != CliUiMode.TUI || !isBlank(options.getPrompt())) {
            return InteractiveBackend.LEGACY;
        }
        if (!(terminal instanceof JlineTerminalIO)) {
            return InteractiveBackend.LEGACY;
        }
        String backend = firstNonBlank(
                System.getProperty("ai4j.tui.backend"),
                env == null ? null : env.get("AI4J_TUI_BACKEND")
        );
        if (isBlank(backend)) {
            return InteractiveBackend.JLINE;
        }
        String normalized = backend.trim().toLowerCase();
        if ("legacy".equals(normalized) || "append-only".equals(normalized)) {
            return InteractiveBackend.LEGACY;
        }
        return InteractiveBackend.JLINE;
    }

    private void closeQuietly(TerminalIO terminal) {
        if (terminal == null) {
            return;
        }
        try {
            terminal.close();
        } catch (Exception ignored) {
        }
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
}
