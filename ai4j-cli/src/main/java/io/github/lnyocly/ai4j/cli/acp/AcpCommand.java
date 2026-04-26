package io.github.lnyocly.ai4j.cli.acp;

import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptionsParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class AcpCommand {

    private final Map<String, String> env;
    private final Properties properties;
    private final Path currentDirectory;
    private final CodeCommandOptionsParser parser = new CodeCommandOptionsParser();

    public AcpCommand(Map<String, String> env, Properties properties, Path currentDirectory) {
        this.env = env;
        this.properties = properties;
        this.currentDirectory = currentDirectory;
    }

    public int run(List<String> args, InputStream in, OutputStream out, OutputStream err) {
        try {
            CodeCommandOptions options = parser.parse(args, env, properties, currentDirectory);
            if (options.isHelp()) {
                printHelp(err);
                return 0;
            }
            return new AcpJsonRpcServer(in, out, err, options).run();
        } catch (IllegalArgumentException ex) {
            writeLine(err, "Argument error: " + ex.getMessage());
            printHelp(err);
            return 2;
        } catch (Exception ex) {
            writeLine(err, "ACP failed: " + safeMessage(ex));
            return 1;
        }
    }

    private void printHelp(OutputStream err) {
        writeLine(err, "ai4j-cli acp");
        writeLine(err, "  Start the coding agent as an ACP stdio server.");
        writeLine(err, "");
        writeLine(err, "Usage:");
        writeLine(err, "  ai4j-cli acp --provider <name> --model <model> [options]");
        writeLine(err, "");
        writeLine(err, "Notes:");
        writeLine(err, "  ACP mode uses newline-delimited JSON-RPC on stdin/stdout.");
        writeLine(err, "  All logs and warnings are written to stderr.");
        writeLine(err, "  Provider/model/api-key options follow the same parsing rules as `ai4j-cli code`.");
    }

    private void writeLine(OutputStream stream, String line) {
        try {
            stream.write((line + System.lineSeparator()).getBytes("UTF-8"));
            stream.flush();
        } catch (Exception ignored) {
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? (throwable == null ? "unknown error" : throwable.getClass().getSimpleName())
                : message.trim();
    }
}
