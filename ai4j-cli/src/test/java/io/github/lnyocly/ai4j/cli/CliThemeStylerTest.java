package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.render.CliThemeStyler;
import io.github.lnyocly.ai4j.tui.TuiTheme;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CliThemeStylerTest {
    @Test
    public void styleTranscriptLineAddsAnsiForBulletBlocksWhenEnabled() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);

        String value = styler.styleTranscriptLine("• Error");

        assertTrue(value.contains("\u001b["));
        assertTrue(value.contains("Error"));
    }

    @Test
    public void buildPrimaryStatusLineStylesSpinnerAndDetail() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);

        String value = styler.buildPrimaryStatusLine("Thinking", true, "⠋", "Analyzing workspace");

        assertTrue(value.contains("\u001b["));
        assertTrue(value.contains("Thinking"));
        assertTrue(value.contains("Analyzing workspace"));
    }

    @Test
    public void ansiDisabledLeavesTranscriptPlain() {
        CliThemeStyler styler = new CliThemeStyler(theme(), false);

        String value = styler.styleTranscriptLine("• Approved");

        assertFalse(value.contains("\u001b["));
        assertTrue("• Approved".equals(value));
    }

    @Test
    public void styleThinkingTranscriptLineUsesAnsiWhenEnabled() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);

        String value = styler.styleTranscriptLine("Thinking: inspect request");

        assertTrue(value.contains("\u001b["));
        assertTrue(value.contains("Thinking: inspect request"));
    }

    @Test
    public void buildCompactStatusLineIncludesStatusAndContext() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);

        String value = styler.buildCompactStatusLine(
                "Thinking",
                true,
                "⠋",
                "Analyzing workspace",
                "glm-4.7",
                "ai4j-sdk",
                "Enter a prompt or /command"
        );

        assertTrue(value.contains("Thinking"));
        assertTrue(value.contains("glm-4.7"));
        assertTrue(value.contains("ai4j-sdk"));
        assertFalse(value.contains("Enter a prompt or /command"));
    }

    @Test
    public void styleCodeBlockLineUsesAnsiWhenEnabled() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);
        CliThemeStyler.TranscriptStyleState state = new CliThemeStyler.TranscriptStyleState();
        state.enterCodeBlock("java");
        String value = styler.styleTranscriptLine("    System.out.println(\"hi\");", state);

        assertTrue(value.contains("\u001b["));
        assertTrue(value.contains("System"));
        assertTrue(value.contains("println"));
        assertTrue(value.contains("\"hi\""));
    }

    @Test
    public void styleJavaCodeBlockUsesTranscriptStateForSyntaxHighlighting() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);
        CliThemeStyler.TranscriptStyleState state = new CliThemeStyler.TranscriptStyleState();
        state.enterCodeBlock("java");
        String body = styler.styleTranscriptLine("    public class Demo { String value = \"hi\"; // note", state);
        String close = styler.styleTranscriptLine("After");

        assertTrue(body.contains("\u001b["));
        assertTrue(body.contains("public"));
        assertTrue(body.contains("\"hi\""));
        assertTrue(body.contains("// note"));
        assertTrue(close.contains("\u001b["));
    }

    @Test
    public void styleJsonCodeBlockHighlightsKeysAndLiterals() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);
        CliThemeStyler.TranscriptStyleState state = new CliThemeStyler.TranscriptStyleState();
        state.enterCodeBlock("json");
        String value = styler.styleTranscriptLine("    {\"enabled\": true, \"count\": 2}", state);

        assertTrue(value.contains("\u001b["));
        assertTrue(value.contains("\"enabled\""));
        assertTrue(value.contains("true"));
        assertTrue(value.contains("2"));
    }

    @Test
    public void ansiDisabledStripsInlineMarkdownMarkers() {
        CliThemeStyler styler = new CliThemeStyler(theme(), false);

        String value = styler.styleTranscriptLine("Use `rg` and **fast path**");

        assertTrue("Use rg and fast path".equals(value));
    }

    @Test
    public void styleHeadingAndQuoteUseAnsiWhenEnabled() {
        CliThemeStyler styler = new CliThemeStyler(theme(), true);

        String heading = styler.styleTranscriptLine("## Files");
        String quote = styler.styleTranscriptLine("> note");

        assertTrue(heading.contains("\u001b["));
        assertTrue(heading.contains("## Files"));
        assertTrue(quote.contains("\u001b["));
        assertTrue(quote.contains("> note"));
    }

    private TuiTheme theme() {
        TuiTheme theme = new TuiTheme();
        theme.setBrand("#7cc6fe");
        theme.setAccent("#f5b14c");
        theme.setSuccess("#8fd694");
        theme.setWarning("#f4d35e");
        theme.setDanger("#ef6f6c");
        theme.setText("#f3f4f6");
        theme.setMuted("#9ca3af");
        return theme;
    }
}
