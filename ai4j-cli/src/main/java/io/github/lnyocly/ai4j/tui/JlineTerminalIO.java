package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.tui.io.DefaultJlineTerminalIO;
import io.github.lnyocly.ai4j.tui.io.DefaultStreamsTerminalIO;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class JlineTerminalIO extends DefaultJlineTerminalIO {

    private JlineTerminalIO(Terminal terminal, OutputStream errStream) {
        super(terminal, errStream);
    }

    public static JlineTerminalIO openSystem(OutputStream errStream) throws IOException {
        Charset charset = DefaultStreamsTerminalIO.resolveTerminalCharset();
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .encoding(charset)
                .build();
        return new JlineTerminalIO(terminal, errStream);
    }
}
