package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.cli.render.CodexStyleBlockFormatter;
import io.github.lnyocly.ai4j.tui.TuiAssistantToolView;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodexStyleBlockFormatterTest {

    private final CodexStyleBlockFormatter formatter = new CodexStyleBlockFormatter(80, 4);

    @Test
    public void formatAssistantTrimsBlankEdgesButKeepsInnerSpacing() {
        List<String> lines = formatter.formatAssistant("\n\nhello\n\nworld\n\n");

        assertEquals(Arrays.asList("hello", "", "world"), lines);
    }

    @Test
    public void formatAssistantRendersFencedCodeBlocks() {
        List<String> lines = formatter.formatAssistant("Before\n```java\nSystem.out.println(\"hi\");\n```\nAfter");

        assertEquals(Arrays.asList(
                "Before",
                "    System.out.println(\"hi\");",
                "After"
        ), lines);
    }

    @Test
    public void formatRunningStatusRemovesBlockBulletPrefix() {
        TuiAssistantToolView toolView = TuiAssistantToolView.builder()
                .toolName("bash")
                .status("pending")
                .title("$ echo hello")
                .build();

        assertEquals("Running echo hello", formatter.formatRunningStatus(toolView));
    }

    @Test
    public void formatToolBuildsCodexLikeTranscriptBlock() {
        TuiAssistantToolView toolView = TuiAssistantToolView.builder()
                .toolName("bash")
                .status("done")
                .title("$ echo hello")
                .previewLines(Arrays.asList("stdout> hello", "stdout> world"))
                .build();

        List<String> lines = formatter.formatTool(toolView);

        assertEquals("• Ran echo hello", lines.get(0));
        assertEquals("  └ hello", lines.get(1));
        assertEquals("    world", lines.get(2));
    }

    @Test
    public void formatToolKeepsQualifiedMcpToolLabel() {
        TuiAssistantToolView toolView = TuiAssistantToolView.builder()
                .toolName("fetch")
                .status("done")
                .title("fetch.fetch(url=\"https://zjuers.com/\")")
                .previewLines(Arrays.asList("out> Contents of https://zjuers.com/"))
                .build();

        List<String> lines = formatter.formatTool(toolView);

        assertEquals("• Ran fetch.fetch(url=\"https://zjuers.com/\")", lines.get(0));
        assertEquals("  └ Contents of https://zjuers.com/", lines.get(1));
    }

    @Test
    public void formatCompactBuildsCompactBlock() {
        CodingSessionCompactResult result = CodingSessionCompactResult.builder()
                .automatic(true)
                .beforeItemCount(12)
                .afterItemCount(7)
                .estimatedTokensBefore(3200)
                .estimatedTokensAfter(1900)
                .summary("Dropped stale history and kept the latest checkpoint.")
                .build();

        List<String> lines = formatter.formatCompact(result);

        assertEquals("• Auto-compacted session context", lines.get(0));
        assertTrue(lines.get(1).contains("tokens 3200->1900"));
        assertTrue(lines.get(1).contains("items 12->7"));
        assertTrue(lines.get(2).contains("Dropped stale history"));
    }

    @Test
    public void formatOutputParsesStructuredSingleLineStatus() {
        List<String> lines = formatter.formatOutput("process stopped: proc-7 status=stopped");

        assertEquals(Arrays.asList(
                "• Process stopped",
                "  └ proc-7 status=stopped"
        ), lines);
    }

    @Test
    public void formatInfoBlockKeepsInnerSeparators() {
        List<String> lines = formatter.formatInfoBlock("Replay", Arrays.asList("first", "", "second"));

        assertEquals(Arrays.asList(
                "• Replay",
                "  └ first",
                "",
                "    second"
        ), lines);
    }
}
