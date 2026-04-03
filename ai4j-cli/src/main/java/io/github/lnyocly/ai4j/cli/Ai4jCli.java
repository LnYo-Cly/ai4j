package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.acp.AcpCommand;
import io.github.lnyocly.ai4j.cli.command.CodeCommand;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.DefaultCodingCliAgentFactory;

import io.github.lnyocly.ai4j.tui.JlineTerminalIO;
import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Ai4jCli {

    private final CodingCliAgentFactory agentFactory;
    private final Path currentDirectory;

    public Ai4jCli() {
        this(new DefaultCodingCliAgentFactory(), Paths.get(".").toAbsolutePath().normalize());
    }

    Ai4jCli(CodingCliAgentFactory agentFactory, Path currentDirectory) {
        this.agentFactory = agentFactory;
        this.currentDirectory = currentDirectory;
    }

    public int run(String[] args, InputStream in, OutputStream out, OutputStream err) {
        return run(args, in, out, err, System.getenv(), System.getProperties());
    }

    int run(String[] args,
            InputStream in,
            OutputStream out,
            OutputStream err,
            Map<String, String> env,
            Properties properties) {
        TerminalIO terminal = createTerminal(in, out, err);
        try {
            List<String> arguments = args == null ? Collections.<String>emptyList() : Arrays.asList(args);
            CodeCommand codeCommand = new CodeCommand(
                    agentFactory,
                    env,
                    properties,
                    currentDirectory
            );
            AcpCommand acpCommand = new AcpCommand(env, properties, currentDirectory);

            if (arguments.isEmpty()) {
                return codeCommand.run(Collections.<String>emptyList(), terminal);
            }

            String first = arguments.get(0);
            if ("help".equalsIgnoreCase(first) || "-h".equals(first) || "--help".equals(first)) {
                printHelp(terminal);
                return 0;
            }
            if ("code".equalsIgnoreCase(first)) {
                return codeCommand.run(arguments.subList(1, arguments.size()), terminal);
            }
            if ("tui".equalsIgnoreCase(first)) {
                return codeCommand.run(asTuiArguments(arguments.subList(1, arguments.size())), terminal);
            }
            if ("acp".equalsIgnoreCase(first)) {
                closeQuietly(terminal);
                return acpCommand.run(arguments.subList(1, arguments.size()), in, out, err);
            }
            if (first.startsWith("--")) {
                return codeCommand.run(arguments, terminal);
            }

            terminal.errorln("Unknown command: " + first);
            printHelp(terminal);
            return 2;
        } finally {
            closeQuietly(terminal);
        }
    }

    private void printHelp(TerminalIO terminal) {
        terminal.println("ai4j-cli");
        terminal.println("  Minimal coding-agent CLI entry. The first release focuses on the code command.\n");
        terminal.println("Usage:");
        terminal.println("  ai4j-cli code --model <model> [options]");
        terminal.println("  ai4j-cli tui --model <model> [options]");
        terminal.println("  ai4j-cli acp --model <model> [options]");
        terminal.println("  ai4j-cli --model <model> [options]   # handled as the code command by default\n");
        terminal.println("Commands:");
        terminal.println("  code      Start a coding session in one-shot or interactive REPL mode\n");
        terminal.println("  tui       Start the same coding session with a richer text UI shell\n");
        terminal.println("  acp       Start the coding session as an ACP stdio server\n");
        terminal.println("Examples:");
        terminal.println("  ai4j-cli code --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace .");
        terminal.println("  ai4j-cli tui --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace .");
        terminal.println("  ai4j-cli acp --provider openai --protocol responses --model gpt-5-mini --workspace .");
        terminal.println("  ai4j-cli code --provider openai --protocol responses --model gpt-5-mini --prompt \"Investigate why tests fail in this workspace\"");
        terminal.println("  ai4j-cli code --provider openai --base-url https://api.deepseek.com --protocol chat --model deepseek-chat\n");
        terminal.println("Tip:");
        terminal.println("  Run `ai4j-cli code --help` for the full code command reference.");
    }

    private List<String> asTuiArguments(List<String> arguments) {
        List<String> tuiArguments = new ArrayList<String>();
        tuiArguments.add("--ui");
        tuiArguments.add(CliUiMode.TUI.getValue());
        if (arguments != null) {
            tuiArguments.addAll(arguments);
        }
        return tuiArguments;
    }

    private TerminalIO createTerminal(InputStream in, OutputStream out, OutputStream err) {
        if (in == System.in && out == System.out && err == System.err) {
            try {
                return JlineTerminalIO.openSystem(err);
            } catch (Exception ignored) {
            }
        }
        return new StreamsTerminalIO(in, out, err);
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
}
