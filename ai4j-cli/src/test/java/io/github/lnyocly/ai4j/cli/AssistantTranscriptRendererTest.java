package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.render.AssistantTranscriptRenderer;
import io.github.lnyocly.ai4j.cli.render.CliThemeStyler;
import io.github.lnyocly.ai4j.tui.TuiTheme;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AssistantTranscriptRendererTest {

    @Test
    public void renderTurnsMarkdownCodeBlocksIntoTranscriptLines() {
        AssistantTranscriptRenderer renderer = new AssistantTranscriptRenderer();

        List<String> lines = renderer.plainLines("`hello.py` 文件已经存在了，内容如下：\n\n```python\n# Python Hello World\n\nprint(\"Hello, World!\")\n```\n\n你可以运行它：\n\n```bash\npython hello.py\n```");

        assertEquals(Arrays.asList(
                "`hello.py` 文件已经存在了，内容如下：",
                "",
                "    # Python Hello World",
                "    ",
                "    print(\"Hello, World!\")",
                "",
                "你可以运行它：",
                "",
                "    python hello.py"
        ), lines);
    }

    @Test
    public void styleBlockHighlightsCodeWithoutLeakingFences() {
        AssistantTranscriptRenderer renderer = new AssistantTranscriptRenderer();
        CliThemeStyler styler = new CliThemeStyler(new TuiTheme(), true);

        String styled = renderer.styleBlock("```python\nprint(\"Hello\")\n```", styler);

        assertTrue(styled.replaceAll("\\u001B\\[[;\\d]*m", "").contains("print(\"Hello\")"));
        assertFalse(styled.contains("```"));
        assertTrue(styled.contains("\u001b["));
    }
}
