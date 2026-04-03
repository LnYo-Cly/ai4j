package io.github.lnyocly.ai4j.tui;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class AnsiTuiRuntimeTest {

    @Test
    public void shouldPrintFramesWithoutTrailingNewlineAndSkipDuplicateRenders() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        SequenceRenderer renderer = new SequenceRenderer("frame-1", "frame-1", "frame-2");
        AnsiTuiRuntime runtime = new AnsiTuiRuntime(terminal, renderer, false);

        runtime.render(TuiScreenModel.builder().build());
        runtime.render(TuiScreenModel.builder().build());
        runtime.render(TuiScreenModel.builder().build());

        Assert.assertEquals(0, terminal.clearCount);
        Assert.assertEquals(2, terminal.moveHomeCount);
        Assert.assertEquals(4, terminal.printCount);
        Assert.assertEquals(0, terminal.printlnCount);
        Assert.assertEquals("frame-1\u001b[Jframe-2\u001b[J", terminal.printed.toString());
    }

    private static final class SequenceRenderer implements TuiRenderer {

        private final String[] frames;
        private int index;

        private SequenceRenderer(String... frames) {
            this.frames = frames == null ? new String[0] : frames;
        }

        @Override
        public int getMaxEvents() {
            return 0;
        }

        @Override
        public String getThemeName() {
            return "test";
        }

        @Override
        public void updateTheme(TuiConfig config, TuiTheme theme) {
        }

        @Override
        public String render(TuiScreenModel screenModel) {
            if (frames.length == 0) {
                return "";
            }
            int current = Math.min(index, frames.length - 1);
            index++;
            return frames[current];
        }
    }

    private static final class RecordingTerminalIO implements TerminalIO {

        private final StringBuilder printed = new StringBuilder();
        private int printCount;
        private int printlnCount;
        private int clearCount;
        private int moveHomeCount;

        @Override
        public String readLine(String prompt) throws IOException {
            return null;
        }

        @Override
        public void print(String message) {
            printCount++;
            printed.append(message == null ? "" : message);
        }

        @Override
        public void println(String message) {
            printlnCount++;
            printed.append(message == null ? "" : message).append('\n');
        }

        @Override
        public void errorln(String message) {
        }

        @Override
        public void clearScreen() {
            clearCount++;
        }

        @Override
        public void moveCursorHome() {
            moveHomeCount++;
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }
    }
}
