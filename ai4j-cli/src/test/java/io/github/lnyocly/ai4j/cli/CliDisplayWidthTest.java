package io.github.lnyocly.ai4j.cli;

import org.jline.utils.AttributedString;
import org.junit.Assert;
import org.junit.Test;

public class CliDisplayWidthTest {

    @Test
    public void clipUsesDisplayWidthForChineseText() {
        String value = CliDisplayWidth.clip("然后说：你好世界", 9);

        Assert.assertEquals("然后说...", value);
        Assert.assertTrue(CliDisplayWidth.displayWidth(value) <= 9);
    }

    @Test
    public void wrapAnsiBreaksBeforeWideTextPushesLastSymbolPastEdge() {
        CliDisplayWidth.WrappedAnsi wrapped = CliDisplayWidth.wrapAnsi(
                "\u001b[90mab你好\"\u001b[0m",
                6,
                0
        );

        Assert.assertEquals("ab你好\n\"", AttributedString.fromAnsi(wrapped.text()).toString());
        Assert.assertEquals(1, wrapped.endColumn());
    }

    @Test
    public void wrapAnsiHonorsExistingColumnOffset() {
        CliDisplayWidth.WrappedAnsi wrapped = CliDisplayWidth.wrapAnsi(
                "\u001b[90m你好\"\u001b[0m",
                6,
                2
        );

        Assert.assertEquals("你好\n\"", AttributedString.fromAnsi(wrapped.text()).toString());
        Assert.assertEquals(1, wrapped.endColumn());
    }

    @Test
    public void wrapAnsiDoesNotLeaveChinesePeriodAloneAtLineStart() {
        CliDisplayWidth.WrappedAnsi wrapped = CliDisplayWidth.wrapAnsi(
                "\u001b[90mab你好。\u001b[0m",
                6,
                0
        );

        Assert.assertEquals("ab你\n好。", AttributedString.fromAnsi(wrapped.text()).toString());
        Assert.assertEquals(4, wrapped.endColumn());
    }

    @Test
    public void wrapAnsiDoesNotLeaveChineseColonAloneAtLineStart() {
        CliDisplayWidth.WrappedAnsi wrapped = CliDisplayWidth.wrapAnsi(
                "\u001b[90m例如：\u001b[0m",
                4,
                0
        );

        Assert.assertEquals("例\n如：", AttributedString.fromAnsi(wrapped.text()).toString());
        Assert.assertEquals(4, wrapped.endColumn());
    }
}
