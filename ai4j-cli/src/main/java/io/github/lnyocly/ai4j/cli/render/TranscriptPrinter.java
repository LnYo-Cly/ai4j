package io.github.lnyocly.ai4j.cli.render;

import io.github.lnyocly.ai4j.cli.shell.JlineShellTerminalIO;
import io.github.lnyocly.ai4j.tui.TerminalIO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TranscriptPrinter {

    private final TerminalIO terminal;
    private boolean printedBlock;

    public TranscriptPrinter(TerminalIO terminal) {
        this.terminal = terminal;
    }

    public synchronized void printBlock(List<String> rawLines) {
        List<String> lines = trimBlankEdges(rawLines);
        if (lines.isEmpty()) {
            return;
        }
        separateFromPreviousBlockIfNeeded();
        if (terminal instanceof JlineShellTerminalIO) {
            ((JlineShellTerminalIO) terminal).printTranscriptBlock(lines);
            printedBlock = true;
            return;
        }
        for (String line : lines) {
            terminal.println(line == null ? "" : line);
        }
        printedBlock = true;
    }

    public synchronized void beginStreamingBlock() {
        printedBlock = true;
    }

    public synchronized void printSectionBreak() {
        if (!printedBlock) {
            return;
        }
        terminal.println("");
        printedBlock = false;
    }

    public synchronized void resetPrintedBlock() {
        printedBlock = false;
    }

    private List<String> trimBlankEdges(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = rawLines.size() - 1;
        while (start <= end && isBlank(rawLines.get(start))) {
            start++;
        }
        while (end >= start && isBlank(rawLines.get(end))) {
            end--;
        }
        if (start > end) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        for (int i = start; i <= end; i++) {
            lines.add(rawLines.get(i) == null ? "" : rawLines.get(i));
        }
        return lines;
    }

    private void separateFromPreviousBlockIfNeeded() {
        if (printedBlock) {
            terminal.println("");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

