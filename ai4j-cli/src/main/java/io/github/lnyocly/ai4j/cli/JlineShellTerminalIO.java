package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiTheme;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.Status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class JlineShellTerminalIO implements TerminalIO {

    private static final String[] SPINNER = new String[]{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private final JlineShellContext shellContext;
    private final SlashCommandController slashCommandController;
    private final Object statusLock = new Object();
    private final Object outputLock = new Object();
    private final Thread spinnerThread;
    private final CliThemeStyler themeStyler;
    private final AssistantTranscriptRenderer assistantTranscriptRenderer = new AssistantTranscriptRenderer();
    private final CliThemeStyler.TranscriptStyleState transcriptStyleState = new CliThemeStyler.TranscriptStyleState();
    private final boolean statusComponentEnabled;
    private final boolean statusAnimationEnabled;
    private boolean inputClosed;
    private volatile boolean closed;
    private String statusLabel = "Idle";
    private String statusDetail = "Type /help for commands";
    private String sessionId = "(new)";
    private String model = "(unknown)";
    private String workspace = ".";
    private String hint = "Enter a prompt or /command";
    private boolean spinnerActive;
    private int spinnerIndex;
    private int outputColumn;
    private boolean trackingAssistantBlock;
    private int trackedAssistantRows;
    private int trackedInlineStatusRows;
    private String lastRenderedStatusLine;
    private boolean forceDirectOutput;

    JlineShellTerminalIO(JlineShellContext shellContext, SlashCommandController slashCommandController) {
        this.shellContext = shellContext;
        this.slashCommandController = slashCommandController;
        this.themeStyler = new CliThemeStyler(new TuiTheme(), supportsAnsi());
        this.statusComponentEnabled = resolveStatusComponentEnabled();
        this.statusAnimationEnabled = statusComponentEnabled && !isJetBrainsTerminal();
        if (this.slashCommandController != null) {
            this.slashCommandController.setStatusRefresh(new Runnable() {
                @Override
                public void run() {
                    redrawStatus();
                }
            });
        }
        if (statusComponentEnabled) {
            this.spinnerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    runSpinnerLoop();
                }
            }, "ai4j-jline-status-spinner");
            this.spinnerThread.setDaemon(true);
            this.spinnerThread.start();
        } else {
            this.spinnerThread = null;
        }
    }

    @Override
    public String readLine(String prompt) throws IOException {
        try {
            redrawStatus();
            return lineReader().readLine(prompt == null ? "" : prompt);
        } catch (EndOfFileException ex) {
            inputClosed = true;
            return null;
        } catch (UserInterruptException ex) {
            inputClosed = true;
            return null;
        } finally {
            redrawStatus();
        }
    }

    @Override
    public void print(String message) {
        writeOutput(message, false);
    }

    @Override
    public void println(String message) {
        writeOutput(message, true);
    }

    @Override
    public void errorln(String message) {
        writeOutput(message, true);
    }

    @Override
    public boolean supportsAnsi() {
        return terminal() != null && !"dumb".equalsIgnoreCase(terminal().getType());
    }

    @Override
    public boolean supportsRawInput() {
        return false;
    }

    @Override
    public boolean isInputClosed() {
        return inputClosed;
    }

    @Override
    public int getTerminalRows() {
        return terminal() == null ? 0 : Math.max(0, terminal().getHeight());
    }

    @Override
    public int getTerminalColumns() {
        return terminal() == null ? 0 : Math.max(0, terminal().getWidth());
    }

    void updateSessionContext(String sessionId, String model, String workspace) {
        boolean changed = false;
        synchronized (statusLock) {
            if (!isBlank(sessionId)) {
                changed = changed || !sameText(this.sessionId, sessionId);
                this.sessionId = sessionId;
            }
            if (!isBlank(model)) {
                changed = changed || !sameText(this.model, model);
                this.model = model;
            }
            if (!isBlank(workspace)) {
                changed = changed || !sameText(this.workspace, workspace);
                this.workspace = workspace;
            }
        }
        if (changed) {
            redrawStatus();
        }
    }

    void updateTheme(TuiTheme theme) {
        themeStyler.updateTheme(theme);
        synchronized (statusLock) {
            lastRenderedStatusLine = null;
        }
        redrawStatus();
    }

    void showIdle(String hint) {
        boolean changed;
        synchronized (statusLock) {
            changed = !sameText(statusLabel, "Idle")
                    || !sameText(statusDetail, "Ready")
                    || !sameText(this.hint, firstNonBlank(hint, "Enter a prompt or /command"))
                    || spinnerActive;
            statusLabel = "Idle";
            statusDetail = "Ready";
            this.hint = firstNonBlank(hint, "Enter a prompt or /command");
            spinnerActive = false;
        }
        if (changed) {
            redrawStatus();
        }
    }

    void beginTurn(String input) {
        boolean changed;
        synchronized (statusLock) {
            transcriptStyleState.reset();
            String nextDetail = isBlank(input)
                    ? "Analyzing workspace and tool context"
                    : "Analyzing: " + clip(input, 72);
            changed = !sameText(statusLabel, "Thinking")
                    || !sameText(statusDetail, nextDetail)
                    || !sameText(hint, "Working on the current request")
                    || !spinnerActive;
            statusLabel = "Thinking";
            statusDetail = nextDetail;
            hint = "Working on the current request";
            spinnerActive = true;
            spinnerIndex = 0;
        }
        if (changed) {
            redrawStatus();
        }
    }

    void showThinking() {
        updateBusyStatus("Thinking", "Analyzing workspace and tool context");
    }

    void showResponding() {
        updateBusyStatus("Responding", "Streaming model output");
    }

    void showWorking(String detail) {
        updateBusyStatus("Working", firstNonBlank(detail, "Running tool"));
    }

    void clearTransient() {
        transcriptStyleState.reset();
        showIdle("Enter a prompt or /command");
    }

    void finishTurn() {
        transcriptStyleState.reset();
        showIdle("Enter a prompt or /command");
    }

    void printAssistantFragment(String message) {
        writeStyledOutput(themeStyler.styleAssistantFragment(message == null ? "" : message), false);
    }

    void printReasoningFragment(String message) {
        writeStyledOutput(themeStyler.styleReasoningFragment(message == null ? "" : message), false);
    }

    void printTranscriptLine(String line, boolean newline) {
        writeOutput(line == null ? "" : line, newline, true);
    }

    void printTranscriptBlock(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index) == null ? "" : lines.get(index));
        }
        writeOutput(builder.toString(), true, false);
    }

    void printAssistantMarkdownBlock(String markdown) {
        String styled = assistantTranscriptRenderer.styleBlock(markdown, themeStyler);
        if (styled == null || styled.isEmpty()) {
            return;
        }
        writeStyledOutput(styled, true, true);
    }

    void enterTranscriptCodeBlock(String language) {
        transcriptStyleState.enterCodeBlock(language);
    }

    void exitTranscriptCodeBlock() {
        transcriptStyleState.exitCodeBlock();
    }

    void beginAssistantBlockTracking() {
        synchronized (outputLock) {
            trackingAssistantBlock = true;
            trackedAssistantRows = 0;
        }
    }

    void beginDirectOutputWindow() {
        synchronized (outputLock) {
            forceDirectOutput = true;
        }
    }

    void endDirectOutputWindow() {
        synchronized (outputLock) {
            forceDirectOutput = false;
        }
    }

    int assistantBlockRows() {
        synchronized (outputLock) {
            return trackedAssistantRows;
        }
    }

    void clearAssistantBlock() {
        synchronized (outputLock) {
            clearAssistantBlockDirect(trackedAssistantRows, lineReader() != null && lineReader().isReading());
            trackedAssistantRows = 0;
            trackingAssistantBlock = false;
            transcriptStyleState.reset();
            outputColumn = 0;
        }
    }

    void forgetAssistantBlock() {
        synchronized (outputLock) {
            trackedAssistantRows = 0;
            trackingAssistantBlock = false;
            transcriptStyleState.reset();
        }
    }

    boolean rewriteAssistantBlock(int previousRows, String replacementMarkdown) {
        String replacement = assistantTranscriptRenderer.styleBlock(replacementMarkdown, themeStyler);
        if (previousRows <= 0 || isBlank(replacement)) {
            return false;
        }
        synchronized (outputLock) {
            return rewriteAssistantBlockDirect(previousRows, replacement, lineReader() != null && lineReader().isReading());
        }
    }

    @Override
    public void close() {
        closed = true;
        if (spinnerThread != null) {
            spinnerThread.interrupt();
        }
    }

    private LineReader lineReader() {
        return shellContext.lineReader();
    }

    private Terminal terminal() {
        return shellContext.terminal();
    }

    private Status status() {
        return shellContext.status();
    }

    private void runSpinnerLoop() {
        while (!closed) {
            try {
                Thread.sleep(120L);
            } catch (InterruptedException ex) {
                if (closed) {
                    return;
                }
                Thread.currentThread().interrupt();
                return;
            }
            boolean shouldRedraw = false;
            synchronized (statusLock) {
                if (spinnerActive && statusAnimationEnabled) {
                    spinnerIndex = (spinnerIndex + 1) % SPINNER.length;
                    shouldRedraw = true;
                }
            }
            if (shouldRedraw) {
                redrawStatus();
            }
        }
    }

    private void updateBusyStatus(String label, String detail) {
        boolean changed;
        synchronized (statusLock) {
            String nextLabel = firstNonBlank(label, "Working");
            String nextDetail = firstNonBlank(detail, "Working");
            changed = !sameText(statusLabel, nextLabel)
                    || !sameText(statusDetail, nextDetail)
                    || !sameText(hint, "Wait for the current task to finish")
                    || !spinnerActive;
            statusLabel = nextLabel;
            statusDetail = nextDetail;
            hint = "Wait for the current task to finish";
            spinnerActive = true;
        }
        if (changed) {
            redrawStatus();
        }
    }

    private void redrawStatus() {
        synchronized (outputLock) {
            if (closed) {
                return;
            }
            List<AttributedString> lines;
            String statusPayload;
            synchronized (statusLock) {
                lines = buildStatusLines();
                statusPayload = joinStatusPayload(lines);
                if (lines.isEmpty() && isBlank(lastRenderedStatusLine)) {
                    return;
                }
                if (sameText(lastRenderedStatusLine, statusPayload)) {
                    return;
                }
            }
            Status status = status();
            if (status == null) {
                redrawInlineStatus(statusPayload);
                return;
            }
            try {
                status.update(lines);
                synchronized (statusLock) {
                    lastRenderedStatusLine = statusPayload;
                }
                Terminal terminal = terminal();
                if (terminal != null) {
                    terminal.flush();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void redrawInlineStatus(String statusPayload) {
        LineReader lineReader = lineReader();
        boolean reading = lineReader != null && lineReader.isReading();
        String nextPayload = statusPayload == null ? "" : statusPayload;
        String previousPayload;
        synchronized (statusLock) {
            previousPayload = lastRenderedStatusLine;
            lastRenderedStatusLine = nextPayload;
        }
        if (isBlank(nextPayload)) {
            if (trackedInlineStatusRows > 0) {
                if (clearAssistantBlockDirect(trackedInlineStatusRows, reading)) {
                    trackedInlineStatusRows = 0;
                } else {
                    synchronized (statusLock) {
                        lastRenderedStatusLine = previousPayload;
                    }
                }
            }
            return;
        }
        if (!reading) {
            synchronized (statusLock) {
                lastRenderedStatusLine = previousPayload;
            }
            return;
        }
        if (trackedInlineStatusRows > 0 && !clearAssistantBlockDirect(trackedInlineStatusRows, true)) {
            synchronized (statusLock) {
                lastRenderedStatusLine = previousPayload;
            }
            return;
        }
        try {
            String rendered = normalizeReadingPrintAboveMessage(nextPayload);
            lineReader.printAbove(rendered);
            trackedInlineStatusRows = countWrappedRows(rendered, 0);
        } catch (Exception ignored) {
            synchronized (statusLock) {
                lastRenderedStatusLine = previousPayload;
            }
        }
    }

    private String buildPrimaryStatusLine() {
        return themeStyler.buildPrimaryStatusLine(
                statusLabel,
                spinnerActive,
                spinnerActive ? SPINNER[Math.floorMod(spinnerIndex, SPINNER.length)] : null,
                statusDetail
        );
    }

    private List<AttributedString> buildStatusLines() {
        SlashCommandController.PaletteSnapshot paletteSnapshot = slashCommandController == null
                ? SlashCommandController.PaletteSnapshot.closed()
                : slashCommandController.getPaletteSnapshot();
        List<String> ansiLines = new ArrayList<String>();
        if (statusComponentEnabled) {
            ansiLines.add(buildStatusLine());
        }
        if (paletteSnapshot.isOpen()) {
            ansiLines.addAll(buildSlashPaletteLines(paletteSnapshot));
        }
        if (ansiLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<AttributedString> lines = new ArrayList<AttributedString>(ansiLines.size());
        for (String ansiLine : ansiLines) {
            lines.add(AttributedString.fromAnsi(ansiLine, terminal()));
        }
        return lines;
    }

    private List<String> buildSlashPaletteLines(SlashCommandController.PaletteSnapshot snapshot) {
        if (snapshot == null || !snapshot.isOpen()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        List<SlashCommandController.PaletteItemSnapshot> items = snapshot.getItems();
        String query = firstNonBlank(snapshot.getQuery(), "/");
        String matchSummary = items.isEmpty() ? "no matches" : items.size() + " matches";
        lines.add(
                themeStyler.styleMutedFragment("commands")
                        + " "
                        + themeStyler.styleAssistantFragment(query)
                        + "  "
                        + themeStyler.styleMutedFragment(matchSummary + "  ↑↓ move  Tab apply  Enter run")
        );
        if (items.isEmpty()) {
            lines.add(themeStyler.styleReasoningFragment("  No matching slash commands"));
            return lines;
        }
        int maxItems = 6;
        int selectedIndex = snapshot.getSelectedIndex();
        int start = Math.max(0, selectedIndex - (maxItems / 2));
        if (start + maxItems > items.size()) {
            start = Math.max(0, items.size() - maxItems);
        }
        int end = Math.min(items.size(), start + maxItems);
        for (int index = start; index < end; index++) {
            SlashCommandController.PaletteItemSnapshot item = items.get(index);
            boolean selected = index == selectedIndex;
            String prefix = selected
                    ? themeStyler.styleAssistantFragment("›")
                    : themeStyler.styleMutedFragment(" ");
            String command = selected
                    ? themeStyler.styleAssistantFragment(item.getDisplay())
                    : themeStyler.styleMutedFragment(item.getDisplay());
            String description = item.getDescription();
            if (isBlank(description)) {
                lines.add(prefix + " " + command);
            } else {
                lines.add(prefix + " " + command + "  " + themeStyler.styleReasoningFragment(clip(description, 72)));
            }
        }
        return lines;
    }

    private String buildStatusLine() {
        return themeStyler.buildCompactStatusLine(
                statusLabel,
                spinnerActive,
                spinnerActive ? SPINNER[Math.floorMod(spinnerIndex, SPINNER.length)] : null,
                clip(statusDetail, 64),
                clip(firstNonBlank(model, "(unknown)"), 20),
                clip(lastPathSegment(firstNonBlank(workspace, ".")), 24),
                clip(firstNonBlank(hint, "Enter a prompt or /command"), 40)
        );
    }

    private String buildSessionLine() {
        return themeStyler.buildSessionLine(
                clip(firstNonBlank(sessionId, "(new)"), 18),
                clip(firstNonBlank(model, "(unknown)"), 24),
                clip(lastPathSegment(firstNonBlank(workspace, ".")), 24)
        );
    }

    private String buildHintLine() {
        return themeStyler.buildHintLine(firstNonBlank(hint, "Enter a prompt or /command"));
    }

    private String joinStatusPayload(List<AttributedString> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i).toAnsi(terminal()));
        }
        return builder.toString();
    }

    private String stylePrintedMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String[] rawLines = message.replace("\r", "").split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rawLines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(themeStyler.styleTranscriptLine(rawLines[i], transcriptStyleState));
        }
        return builder.toString();
    }

    private void writeOutput(String message, boolean newline) {
        writeOutput(message, newline, false);
    }

    private void writeOutput(String message, boolean newline, boolean trackAssistantBlock) {
        String value = message == null ? "" : message;
        if (!newline && value.isEmpty()) {
            return;
        }
        writeStyledOutput(stylePrintedMessage(value), newline, trackAssistantBlock);
    }

    private void writeStyledOutput(String message, boolean newline) {
        writeStyledOutput(message, newline, false);
    }

    private void writeStyledOutput(String message, boolean newline, boolean trackAssistantBlock) {
        synchronized (outputLock) {
            LineReader lineReader = lineReader();
            boolean reading = !forceDirectOutput && lineReader != null && lineReader.isReading();
            if (trackAssistantBlock && trackingAssistantBlock) {
                trackedAssistantRows += countOutputRows(message, newline, reading, outputColumn);
            }
            if (reading) {
                String readingMessage = normalizeReadingPrintAboveMessage(message == null ? "" : message);
                CliDisplayWidth.WrappedAnsi wrapped = CliDisplayWidth.wrapAnsi(
                        readingMessage,
                        getTerminalColumns(),
                        newline ? 0 : outputColumn
                );
                String wrappedText = wrapped.text();
                if (!wrappedText.isEmpty()) {
                    lineReader.printAbove(wrappedText);
                    outputColumn = newline ? 0 : wrapped.endColumn();
                } else if (newline) {
                    outputColumn = 0;
                }
                redrawStatus();
                return;
            }
            Status status = status();
            boolean suspended = false;
            try {
                if (status != null) {
                    status.suspend();
                    suspended = true;
                }
                Terminal terminal = terminal();
                if (terminal != null) {
                    CliDisplayWidth.WrappedAnsi wrapped = CliDisplayWidth.wrapAnsi(
                            message == null ? "" : message,
                            getTerminalColumns(),
                            outputColumn
                    );
                    terminal.writer().print(wrapped.text());
                    outputColumn = wrapped.endColumn();
                    if (newline) {
                        terminal.writer().println();
                        outputColumn = 0;
                    }
                    terminal.writer().flush();
                    terminal.flush();
                }
            } catch (Exception ignored) {
            } finally {
                if (suspended) {
                    try {
                        status.restore();
                    } catch (Exception ignored) {
                    }
                }
                redrawStatus();
            }
        }
    }

    private boolean rewriteAssistantBlockDirect(int previousRows, String replacement, boolean reading) {
        Terminal terminal = terminal();
        if (terminal == null) {
            return false;
        }
        Status status = status();
        boolean suspended = false;
        try {
            if (status != null) {
                status.suspend();
                suspended = true;
            }
            StringBuilder builder = new StringBuilder();
            builder.append('\r');
            if (reading) {
                // Clear the prompt line first so the replacement can be redrawn
                // independently from JLine's input buffer redisplay.
                builder.append("\u001b[2K");
            }
            for (int row = 0; row < previousRows; row++) {
                builder.append("\u001b[1A");
                builder.append('\r');
                builder.append("\u001b[2K");
            }
            builder.append('\r');
            builder.append(replacement);
            builder.append('\n');
            terminal.writer().print(builder.toString());
            terminal.writer().flush();
            terminal.flush();
            if (reading) {
                redrawInputLine();
            }
            if (trackingAssistantBlock) {
                trackedAssistantRows = countWrappedRows(replacement, 0);
            }
            outputColumn = 0;
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (suspended) {
                try {
                    status.restore();
                } catch (Exception ignored) {
                }
            }
            redrawStatus();
        }
    }

    private boolean clearAssistantBlockDirect(int previousRows, boolean reading) {
        if (previousRows <= 0) {
            return false;
        }
        Terminal terminal = terminal();
        if (terminal == null) {
            return false;
        }
        Status status = status();
        boolean suspended = false;
        try {
            if (status != null) {
                status.suspend();
                suspended = true;
            }
            StringBuilder builder = new StringBuilder();
            builder.append('\r');
            if (reading) {
                builder.append("\u001b[2K");
            }
            for (int row = 0; row < previousRows; row++) {
                builder.append("\u001b[1A");
                builder.append('\r');
                builder.append("\u001b[2K");
            }
            terminal.writer().print(builder.toString());
            terminal.writer().flush();
            terminal.flush();
            if (reading) {
                redrawInputLine();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (suspended) {
                try {
                    status.restore();
                } catch (Exception ignored) {
                }
            }
            redrawStatus();
        }
    }

    private void redrawInputLine() {
        LineReader lineReader = lineReader();
        if (lineReader == null) {
            return;
        }
        try {
            lineReader.callWidget(LineReader.REDRAW_LINE);
        } catch (Exception ignored) {
        }
        try {
            lineReader.callWidget(LineReader.REDISPLAY);
        } catch (Exception ignored) {
        }
    }

    private int countOutputRows(String message, boolean newline, boolean reading, int startColumn) {
        String safe = message == null ? "" : message;
        if (reading) {
            if (safe.isEmpty()) {
                return newline ? 1 : 0;
            }
            return countWrappedRows(safe, 0);
        }
        if (safe.isEmpty()) {
            if (!newline) {
                return 0;
            }
            return startColumn == 0 ? 1 : 0;
        }
        return countWrappedRows(safe, startColumn);
    }

    private int countWrappedRows(String message, int startColumn) {
        String safe = message == null ? "" : message;
        if (safe.isEmpty()) {
            return 0;
        }
        String wrapped = CliDisplayWidth.wrapAnsi(safe, getTerminalColumns(), startColumn).text();
        if (wrapped.isEmpty()) {
            return 0;
        }
        return Math.max(1, wrapped.split("\n", -1).length);
    }

    private String normalizeReadingPrintAboveMessage(String message) {
        String safe = message == null ? "" : message.replace("\r", "");
        if (safe.isEmpty()) {
            return " ";
        }
        String[] rawLines = safe.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < rawLines.length; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            String rawLine = rawLines[index];
            builder.append(rawLine.isEmpty() ? " " : rawLine);
        }
        return builder.toString();
    }

    private String lastPathSegment(String value) {
        if (isBlank(value)) {
            return ".";
        }
        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 && index + 1 < normalized.length() ? normalized.substring(index + 1) : normalized;
    }

    private String clip(String value, int maxChars) {
        return CliDisplayWidth.clip(value, maxChars);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean sameText(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean isJetBrainsTerminal() {
        String[] candidates = new String[]{
                System.getenv("TERMINAL_EMULATOR"),
                System.getenv("TERM_PROGRAM"),
                System.getProperty("terminal.emulator"),
                terminal() == null ? null : terminal().getName(),
                terminal() == null ? null : terminal().getType()
        };
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String normalized = candidate.toLowerCase();
            if (normalized.contains("jetbrains") || normalized.contains("jediterm")) {
                return true;
            }
        }
        return false;
    }

    private boolean resolveStatusComponentEnabled() {
        String property = System.getProperty("ai4j.jline.status");
        if (!isBlank(property)) {
            return "true".equalsIgnoreCase(property.trim());
        }
        String environment = System.getenv("AI4J_JLINE_STATUS");
        if (!isBlank(environment)) {
            return "true".equalsIgnoreCase(environment.trim());
        }
        // Disable the JLine footer status by default. In JetBrains terminals it
        // can drift the scroll region and create the blank streaming area the
        // user is seeing; callers can explicitly re-enable it when needed.
        return false;
    }
}
