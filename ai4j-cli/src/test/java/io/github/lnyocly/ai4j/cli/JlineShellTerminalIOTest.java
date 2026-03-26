package io.github.lnyocly.ai4j.cli;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Buffer;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Status;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JlineShellTerminalIOTest {

    @Test
    public void test_clear_assistant_block_resets_tracking_and_writes_erase_sequences() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("ai4j-cli-test")
                .build();
        Status status = Status.getStatus(terminal, false);
        if (status != null) {
            status.setBorder(false);
        }
        JlineShellContext context = newContext(terminal, lineReader, status);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.beginAssistantBlockTracking();
            terminalIO.printTranscriptLine("already rendered", true);
            Assert.assertTrue(terminalIO.assistantBlockRows() > 0);

            int beforeClear = output.size();
            terminalIO.clearAssistantBlock();

            String clearOutput = new String(output.toByteArray(), beforeClear, output.size() - beforeClear, StandardCharsets.UTF_8);
            Assert.assertEquals(0, terminalIO.assistantBlockRows());
            Assert.assertTrue(clearOutput.contains("\u001b[2K"));
            Assert.assertTrue(clearOutput.contains("\u001b[1A"));
        } finally {
            terminalIO.close();
            context.close();
        }
    }

    @Test
    public void test_forget_assistant_block_resets_tracking_without_rewriting_terminal_history() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("ai4j-cli-test")
                .build();
        Status status = Status.getStatus(terminal, false);
        if (status != null) {
            status.setBorder(false);
        }
        JlineShellContext context = newContext(terminal, lineReader, status);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.beginAssistantBlockTracking();
            terminalIO.printTranscriptLine("already rendered", true);
            Assert.assertTrue(terminalIO.assistantBlockRows() > 0);

            int beforeForget = output.size();
            terminalIO.forgetAssistantBlock();

            String forgetOutput = new String(output.toByteArray(), beforeForget, output.size() - beforeForget, StandardCharsets.UTF_8);
            Assert.assertEquals(0, terminalIO.assistantBlockRows());
            Assert.assertEquals("", forgetOutput);
        } finally {
            terminalIO.close();
            context.close();
        }
    }

    @Test
    public void test_blank_newline_uses_print_above_while_reading() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        RecordingLineReaderHandler handler = new RecordingLineReaderHandler();
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext context = newContext(terminal, lineReader, null);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.println("");

            Assert.assertEquals(1, handler.printAboveCalls().size());
            Assert.assertEquals(" ", handler.printAboveCalls().get(0));
            Assert.assertTrue(handler.widgetCalls().isEmpty());
            Assert.assertEquals("", new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            terminalIO.close();
            context.close();
        }
    }

    @Test
    public void test_multiline_transcript_block_uses_print_above_while_reading() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        RecordingLineReaderHandler handler = new RecordingLineReaderHandler();
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext context = newContext(terminal, lineReader, null);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.printTranscriptBlock(Arrays.asList("alpha", "", "beta"));

            Assert.assertEquals(1, handler.printAboveCalls().size());
            Assert.assertEquals("alpha\n \nbeta", handler.printAboveCalls().get(0));
            Assert.assertTrue(handler.widgetCalls().isEmpty());
            Assert.assertEquals("", new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            terminalIO.close();
            context.close();
        }
    }

    @Test
    public void test_assistant_markdown_block_writes_directly_when_not_reading() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        RecordingLineReaderHandler handler = new RecordingLineReaderHandler(false);
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext context = newContext(terminal, lineReader, null);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.printAssistantMarkdownBlock("Hello!\n\n- item");

            Assert.assertTrue(handler.printAboveCalls().isEmpty());
            String rendered = new String(output.toByteArray(), StandardCharsets.UTF_8);
            Assert.assertTrue(rendered.contains("Hello!"));
            Assert.assertTrue(rendered.contains("- item"));
        } finally {
            terminalIO.close();
            context.close();
        }
    }

    @Test
    public void test_direct_output_window_bypasses_print_above_even_if_line_reader_reports_reading() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        RecordingLineReaderHandler handler = new RecordingLineReaderHandler(true);
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext context = newContext(terminal, lineReader, null);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.beginDirectOutputWindow();
            terminalIO.printAssistantMarkdownBlock("Hello!\n\n- item");

            Assert.assertTrue(handler.printAboveCalls().isEmpty());
            String rendered = new String(output.toByteArray(), StandardCharsets.UTF_8);
            Assert.assertTrue(rendered.contains("Hello!"));
            Assert.assertTrue(rendered.contains("- item"));
        } finally {
            terminalIO.endDirectOutputWindow();
            terminalIO.close();
            context.close();
        }
    }

    @Test
    public void test_repeated_busy_status_does_not_redundantly_redraw_same_line() throws Exception {
        String previous = System.getProperty("ai4j.jline.status");
        System.setProperty("ai4j.jline.status", "true");
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("ai4j-cli-test")
                .build();
        CountingStatus status = new CountingStatus(terminal);
        status.setBorder(false);
        JlineShellContext context = newContext(terminal, lineReader, status);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.showResponding();
            terminalIO.showResponding();

            Assert.assertEquals(1, status.updateCount());
        } finally {
            terminalIO.close();
            context.close();
            restoreProperty("ai4j.jline.status", previous);
        }
    }

    @Test
    public void test_status_is_disabled_by_default() throws Exception {
        String previous = System.getProperty("ai4j.jline.status");
        System.clearProperty("ai4j.jline.status");
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("ai4j-cli-test")
                .build();
        CountingStatus status = new CountingStatus(terminal);
        status.setBorder(false);
        JlineShellContext context = newContext(terminal, lineReader, status);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, null);
        try {
            terminalIO.showResponding();

            Assert.assertEquals(0, status.updateCount());
        } finally {
            terminalIO.close();
            context.close();
            restoreProperty("ai4j.jline.status", previous);
        }
    }

    @Test
    public void test_inline_slash_palette_renders_when_status_component_is_unavailable() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-inline-slash");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new io.github.lnyocly.ai4j.tui.TuiConfigManager(workspace)
        );
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        PaletteBuffer buffer = new PaletteBuffer();
        PaletteLineReaderHandler handler = new PaletteLineReaderHandler(buffer);
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext context = newContext(terminal, lineReader, null);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(context, controller);
        try {
            controller.openSlashMenu(lineReader);

            Assert.assertEquals(1, handler.printAboveCalls().size());
            String rendered = handler.printAboveCalls().get(0);
            Assert.assertTrue(rendered.contains("commands"));
            Assert.assertTrue(rendered.contains("/help"));
            Assert.assertTrue(rendered.contains("/status"));
        } finally {
            terminalIO.close();
            context.close();
        }
    }

    private JlineShellContext newContext(Terminal terminal, LineReader lineReader, Status status) throws Exception {
        Constructor<JlineShellContext> constructor = JlineShellContext.class
                .getDeclaredConstructor(Terminal.class, LineReader.class, Status.class);
        constructor.setAccessible(true);
        return constructor.newInstance(terminal, lineReader, status);
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class RecordingLineReaderHandler implements InvocationHandler {

        private final List<String> printAboveCalls = new ArrayList<String>();
        private final List<String> widgetCalls = new ArrayList<String>();
        private final boolean reading;

        private RecordingLineReaderHandler() {
            this(true);
        }

        private RecordingLineReaderHandler(boolean reading) {
            this.reading = reading;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("isReading".equals(name)) {
                return reading;
            }
            if ("printAbove".equals(name)) {
                printAboveCalls.add(args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : null);
                return null;
            }
            if ("callWidget".equals(name)) {
                if (args != null && args.length > 0 && args[0] != null) {
                    widgetCalls.add(String.valueOf(args[0]));
                }
                return Boolean.TRUE;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == (args == null || args.length == 0 ? null : args[0]);
            }
            if ("toString".equals(name)) {
                return "RecordingLineReader";
            }
            return defaultValue(method.getReturnType());
        }

        private List<String> printAboveCalls() {
            return printAboveCalls;
        }

        private List<String> widgetCalls() {
            return widgetCalls;
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == null || Void.TYPE.equals(returnType)) {
                return null;
            }
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Character.TYPE.equals(returnType)) {
                return Character.valueOf('\0');
            }
            if (Byte.TYPE.equals(returnType)) {
                return Byte.valueOf((byte) 0);
            }
            if (Short.TYPE.equals(returnType)) {
                return Short.valueOf((short) 0);
            }
            if (Integer.TYPE.equals(returnType)) {
                return Integer.valueOf(0);
            }
            if (Long.TYPE.equals(returnType)) {
                return Long.valueOf(0L);
            }
            if (Float.TYPE.equals(returnType)) {
                return Float.valueOf(0F);
            }
            if (Double.TYPE.equals(returnType)) {
                return Double.valueOf(0D);
            }
            return null;
        }
    }

    private static final class CountingStatus extends Status {

        private int updateCount;

        private CountingStatus(Terminal terminal) {
            super(terminal);
        }

        @Override
        public void update(List<AttributedString> lines) {
            updateCount++;
        }

        private int updateCount() {
            return updateCount;
        }
    }

    private static final class PaletteLineReaderHandler implements InvocationHandler {

        private final PaletteBuffer buffer;
        private final List<String> printAboveCalls = new ArrayList<String>();

        private PaletteLineReaderHandler(PaletteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("isReading".equals(name)) {
                return true;
            }
            if ("getBuffer".equals(name)) {
                return buffer.proxy();
            }
            if ("printAbove".equals(name)) {
                printAboveCalls.add(args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : null);
                return null;
            }
            if ("callWidget".equals(name)) {
                return Boolean.TRUE;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == (args == null || args.length == 0 ? null : args[0]);
            }
            if ("toString".equals(name)) {
                return "PaletteLineReader";
            }
            return defaultValue(method.getReturnType());
        }

        private List<String> printAboveCalls() {
            return printAboveCalls;
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == null || Void.TYPE.equals(returnType)) {
                return null;
            }
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Integer.TYPE.equals(returnType) || Short.TYPE.equals(returnType) || Byte.TYPE.equals(returnType)) {
                return 0;
            }
            if (Long.TYPE.equals(returnType)) {
                return 0L;
            }
            if (Float.TYPE.equals(returnType)) {
                return 0F;
            }
            if (Double.TYPE.equals(returnType)) {
                return 0D;
            }
            if (Character.TYPE.equals(returnType)) {
                return '\0';
            }
            return null;
        }
    }

    private static final class PaletteBuffer {

        private final StringBuilder value = new StringBuilder();

        private Buffer proxy() {
            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return value.toString();
                    }
                    if ("write".equals(name)) {
                        if (args != null && args.length > 0 && args[0] != null) {
                            value.append(String.valueOf(args[0]));
                        }
                        return null;
                    }
                    if ("clear".equals(name)) {
                        value.setLength(0);
                        return null;
                    }
                    if ("cursor".equals(name) || "length".equals(name)) {
                        return value.length();
                    }
                    return defaultValue(method.getReturnType());
                }
            };
            return (Buffer) Proxy.newProxyInstance(
                    Buffer.class.getClassLoader(),
                    new Class<?>[]{Buffer.class},
                    handler
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == null || Void.TYPE.equals(returnType)) {
                return null;
            }
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Integer.TYPE.equals(returnType) || Short.TYPE.equals(returnType) || Byte.TYPE.equals(returnType)) {
                return 0;
            }
            if (Long.TYPE.equals(returnType)) {
                return 0L;
            }
            if (Float.TYPE.equals(returnType)) {
                return 0F;
            }
            if (Double.TYPE.equals(returnType)) {
                return 0D;
            }
            if (Character.TYPE.equals(returnType)) {
                return '\0';
            }
            return null;
        }
    }
}
