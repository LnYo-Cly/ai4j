package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.tui.TerminalIO;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TranscriptPrinterTest {

    @Test
    public void printsSingleBlankLineBetweenBlocks() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        TranscriptPrinter printer = new TranscriptPrinter(terminal);

        printer.printBlock(Arrays.asList("", "• Ran echo hello", "  └ hello", ""));
        printer.printBlock(Arrays.asList("• Applied patch"));

        assertEquals(Arrays.asList("• Ran echo hello", "  └ hello", "", "• Applied patch"), terminal.lines());
    }

    @Test
    public void sectionBreakResetsSpacingWithoutDoubleBlankLines() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        TranscriptPrinter printer = new TranscriptPrinter(terminal);

        printer.printBlock(Arrays.asList("• First block"));
        printer.printSectionBreak();
        printer.printBlock(Arrays.asList("• Second block"));

        assertEquals(Arrays.asList("• First block", "", "• Second block"), terminal.lines());
    }

    @Test
    public void streamingBlockUsesSingleSeparatorBeforeNextBlock() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        TranscriptPrinter printer = new TranscriptPrinter(terminal);

        printer.printBlock(Arrays.asList("• First block"));
        printer.beginStreamingBlock();
        terminal.print("chunked");
        terminal.println("");
        printer.printBlock(Arrays.asList("• Second block"));

        assertEquals(Arrays.asList("• First block", "chunked", "", "", "• Second block"), terminal.lines());
    }

    @Test
    public void resetPrintedBlockDropsSeparatorAfterClearedBlock() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        TranscriptPrinter printer = new TranscriptPrinter(terminal);

        printer.beginStreamingBlock();
        printer.resetPrintedBlock();
        printer.printBlock(Arrays.asList("• Tool block"));

        assertEquals(Arrays.asList("• Tool block"), terminal.lines());
    }

    private static final class RecordingTerminalIO implements TerminalIO {

        private final List<String> lines = new ArrayList<String>();

        @Override
        public String readLine(String prompt) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void print(String message) {
            lines.add(message == null ? "" : message);
        }

        @Override
        public void println(String message) {
            lines.add(message == null ? "" : message);
        }

        @Override
        public void errorln(String message) {
            lines.add(message == null ? "" : message);
        }

        private List<String> lines() {
            return lines;
        }
    }
}
