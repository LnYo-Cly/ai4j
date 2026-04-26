package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.tui.io.DefaultStreamsTerminalIO;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class StreamsTerminalIO extends DefaultStreamsTerminalIO {

    public StreamsTerminalIO(InputStream in, OutputStream out, OutputStream err) {
        super(in, out, err);
    }

    StreamsTerminalIO(InputStream in, OutputStream out, OutputStream err, boolean ansiSupported) {
        super(in, out, err, ansiSupported);
    }

    StreamsTerminalIO(InputStream in,
                      OutputStream out,
                      OutputStream err,
                      Charset charset,
                      boolean ansiSupported) {
        super(in, out, err, charset, ansiSupported);
    }

    public static Charset resolveTerminalCharset() {
        return DefaultStreamsTerminalIO.resolveTerminalCharset();
    }
}
