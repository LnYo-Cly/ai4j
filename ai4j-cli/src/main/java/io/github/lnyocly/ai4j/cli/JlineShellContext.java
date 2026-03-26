package io.github.lnyocly.ai4j.cli;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Status;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class JlineShellContext implements AutoCloseable {

    private final Terminal terminal;
    private final LineReader lineReader;
    private final Status status;

    private JlineShellContext(Terminal terminal, LineReader lineReader, Status status) {
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.status = status;
    }

    static JlineShellContext openSystem(SlashCommandController slashCommandController) throws IOException {
        TerminalBuilder builder = TerminalBuilder.builder()
                .system(true)
                .encoding(resolveCharset())
                .nativeSignals(true);
        if (isWindows()) {
            builder.provider("jni");
        }
        Terminal terminal = builder.build();
        DefaultParser parser = new DefaultParser();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("ai4j-cli")
                .parser(parser)
                .completer(slashCommandController)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
        if (slashCommandController != null) {
            slashCommandController.configure(lineReader);
        }
        Status status = Status.getStatus(terminal, false);
        if (status != null) {
            status.setBorder(false);
        }
        return new JlineShellContext(terminal, lineReader, status);
    }

    Terminal terminal() {
        return terminal;
    }

    LineReader lineReader() {
        return lineReader;
    }

    Status status() {
        return status;
    }

    @Override
    public void close() throws IOException {
        if (status != null) {
            status.close();
        }
        if (terminal != null) {
            terminal.close();
        }
    }

    private static Charset resolveCharset() {
        String[] candidates = new String[]{
                System.getProperty("stdin.encoding"),
                System.getProperty("sun.stdin.encoding"),
                System.getProperty("stdout.encoding"),
                System.getProperty("sun.stdout.encoding"),
                System.getProperty("native.encoding"),
                System.getProperty("sun.jnu.encoding"),
                System.getProperty("file.encoding")
        };
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            try {
                return Charset.forName(candidate.trim());
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }
}
