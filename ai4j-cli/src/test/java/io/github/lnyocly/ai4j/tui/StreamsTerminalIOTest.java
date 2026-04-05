package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.tui.io.DefaultStreamsTerminalIO;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StreamsTerminalIOTest {

    @Test
    public void shouldUseConfiguredCharsetForChineseInputAndOutput() throws Exception {
        String original = System.getProperty("ai4j.terminal.encoding");
        System.setProperty("ai4j.terminal.encoding", "UTF-8");
        try {
            ByteArrayInputStream in = new ByteArrayInputStream("中文输入\n".getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            StreamsTerminalIO terminal = new StreamsTerminalIO(in, out, err, false);

            String line = terminal.readLine("> ");
            terminal.println("中文输出");
            terminal.errorln("错误输出");

            String stdout = new String(out.toByteArray(), StandardCharsets.UTF_8);
            String stderr = new String(err.toByteArray(), StandardCharsets.UTF_8);

            Assert.assertEquals("中文输入", line);
            Assert.assertTrue(stdout.contains("> "));
            Assert.assertTrue(stdout.contains("中文输出"));
            Assert.assertTrue(stderr.contains("错误输出"));
        } finally {
            if (original == null) {
                System.clearProperty("ai4j.terminal.encoding");
            } else {
                System.setProperty("ai4j.terminal.encoding", original);
            }
        }
    }

    @Test
    public void shouldPreferUtf8HintsBeforeLegacyPlatformFallback() throws Exception {
        Method resolveCharset = DefaultStreamsTerminalIO.class.getDeclaredMethod(
                "resolveTerminalCharset",
                String[].class,
                String[].class,
                String[].class,
                boolean.class
        );
        resolveCharset.setAccessible(true);
        Charset charset = (Charset) resolveCharset.invoke(
                null,
                new Object[]{
                        new String[0],
                        new String[0],
                        new String[]{"GBK", "GBK"},
                        true
                }
        );

        Assert.assertEquals(StandardCharsets.UTF_8, charset);
    }

    @Test
    public void shouldDrainBufferedScriptedKeyStrokesWithTimeoutReads() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{16, 'e', 'x', 'i', 't', '\n'});
        StreamsTerminalIO terminal = new StreamsTerminalIO(
                in,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream(),
                false
        );

        Assert.assertEquals(TuiKeyType.CTRL_P, terminal.readKeyStroke(150L).getType());
        Assert.assertEquals("e", terminal.readKeyStroke(150L).getText());
        Assert.assertEquals("x", terminal.readKeyStroke(150L).getText());
        Assert.assertEquals("i", terminal.readKeyStroke(150L).getText());
        Assert.assertEquals("t", terminal.readKeyStroke(150L).getText());
        Assert.assertEquals(TuiKeyType.ENTER, terminal.readKeyStroke(150L).getType());
        Assert.assertNull(terminal.readKeyStroke(10L));
    }
}
