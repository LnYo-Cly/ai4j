package io.github.lnyocly.ai4j.tui.runtime;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiAssistantPhase;
import io.github.lnyocly.ai4j.tui.TuiAssistantToolView;
import io.github.lnyocly.ai4j.tui.TuiAssistantViewModel;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import io.github.lnyocly.ai4j.tui.TuiKeyStroke;
import io.github.lnyocly.ai4j.tui.TuiPaletteItem;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiScreenModel;
import org.jline.utils.WCWidth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DefaultAppendOnlyTuiRuntime implements TuiRuntime {

    private static final String ESC = "\u001b[";
    private static final String RESET = "\u001b[0m";
    private static final String DIM = "\u001b[2m";
    private static final String CYAN = "\u001b[36m";
    private static final String GREEN = "\u001b[32m";
    private static final String YELLOW = "\u001b[33m";
    private static final String RED = "\u001b[31m";
    private static final String INVERSE = "\u001b[7m";
    private static final String BULLET = "\u2022 ";
    private static final String TREE = "  \u2514 ";
    private static final String INDENT = "    ";
    private static final String ELLIPSIS = "\u2026";
    private static final String INITIAL_HINT = "Ask AI4J to inspect this repository";
    private static final int MAX_TOOL_PREVIEW_LINES = 4;
    private static final int MAX_PALETTE_LINES = 8;
    private static final String[] SPINNER_FRAMES = new String[]{
            "\u280b", "\u2819", "\u2839", "\u2838", "\u283c",
            "\u2834", "\u2826", "\u2827", "\u2807", "\u280f"
    };

    private final TerminalIO terminal;
    private final Set<String> printedEventKeys = new LinkedHashSet<String>();

    private boolean entered;
    private boolean headerPrinted;
    private int footerLineCount;
    private String footerSignature;
    private String currentSessionId;
    private String lastAssistantOutput;
    private String liveReasoningPrinted = "";
    private String liveTextPrinted = "";
    private LiveBlock activeLiveBlock = LiveBlock.NONE;

    public DefaultAppendOnlyTuiRuntime(TerminalIO terminal) {
        this.terminal = terminal;
    }

    @Override
    public boolean supportsRawInput() {
        return terminal != null && terminal.supportsRawInput();
    }

    @Override
    public synchronized void enter() {
        entered = true;
        footerLineCount = 0;
        footerSignature = null;
        if (terminal != null) {
            terminal.showCursor();
        }
    }

    @Override
    public synchronized void exit() {
        clearFooter();
        if (terminal != null) {
            terminal.showCursor();
        }
        entered = false;
    }

    @Override
    public TuiKeyStroke readKeyStroke(long timeoutMs) throws IOException {
        return terminal == null ? null : terminal.readKeyStroke(timeoutMs);
    }

    @Override
    public synchronized void render(TuiScreenModel screenModel) {
        if (terminal == null || screenModel == null) {
            return;
        }
        if (!headerPrinted) {
            printSessionHeader(screenModel);
        } else {
            printSessionBoundaryIfNeeded(screenModel);
        }
        appendEventTranscript(screenModel);
        appendLiveAssistantTranscript(screenModel);
        appendAssistantOutput(screenModel);
        renderFooter(screenModel);
    }

    private void printSessionHeader(TuiScreenModel screenModel) {
        List<String> lines = new ArrayList<String>();
        String model = safeTrim(screenModel.getRenderContext() == null ? null : screenModel.getRenderContext().getModel());
        String workspace = safeTrim(screenModel.getRenderContext() == null ? null : screenModel.getRenderContext().getWorkspace());
        String sessionId = shortenSessionId(screenModel.getDescriptor() == null ? null : screenModel.getDescriptor().getSessionId());
        StringBuilder header = new StringBuilder();
        header.append(colorize(CYAN, "AI4J"))
                .append("  ")
                .append(firstNonBlank(model, "model"))
                .append("  ")
                .append(lastPathSegment(firstNonBlank(workspace, ".")));
        if (!isBlank(sessionId)) {
            header.append("  ").append(colorize(DIM, sessionId));
        }
        lines.add(header.toString());
        lines.add(colorize(DIM, BULLET + "Type / for commands, Enter to send"));
        lines.add("");
        printTranscriptLines(lines);
        headerPrinted = true;
        currentSessionId = screenModel.getDescriptor() == null ? null : safeTrim(screenModel.getDescriptor().getSessionId());
    }

    private void printSessionBoundaryIfNeeded(TuiScreenModel screenModel) {
        String nextSessionId = screenModel.getDescriptor() == null ? null : safeTrim(screenModel.getDescriptor().getSessionId());
        if (isBlank(nextSessionId) || equals(currentSessionId, nextSessionId)) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(colorize(DIM, BULLET + "Note: Switched session " + shortenSessionId(nextSessionId)));
        printTranscriptLines(lines);
        currentSessionId = nextSessionId;
    }

    private void appendEventTranscript(TuiScreenModel screenModel) {
        List<SessionEvent> events = screenModel.getCachedEvents();
        if (events == null || events.isEmpty()) {
            return;
        }
        for (SessionEvent event : events) {
            String key = eventKey(event);
            if (isBlank(key) || printedEventKeys.contains(key)) {
                continue;
            }
            if (event.getType() == SessionEventType.USER_MESSAGE) {
                resetLiveDedupState();
            }
            if (consumeLiveAssistantEvent(event)) {
                printedEventKeys.add(key);
                continue;
            }
            List<String> lines = formatEvent(event);
            printedEventKeys.add(key);
            if (lines.isEmpty()) {
                continue;
            }
            printTranscriptLines(lines);
        }
    }

    private boolean consumeLiveAssistantEvent(SessionEvent event) {
        if (event == null || event.getType() != SessionEventType.ASSISTANT_MESSAGE) {
            return false;
        }
        Map<String, Object> payload = event.getPayload();
        String output = firstNonBlank(payloadString(payload, "output"), event.getSummary());
        if (isBlank(output)) {
            return false;
        }
        boolean reasoning = "reasoning".equalsIgnoreCase(payloadString(payload, "kind"));
        String printed = reasoning ? liveReasoningPrinted : liveTextPrinted;
        if (isBlank(printed) || !output.startsWith(printed)) {
            return false;
        }
        String suffix = output.substring(printed.length());
        if (!isBlank(suffix)) {
            appendLiveSuffix(reasoning ? LiveBlock.REASONING : LiveBlock.TEXT, printed, suffix);
        }
        if (reasoning) {
            liveReasoningPrinted = "";
        } else {
            liveTextPrinted = "";
        }
        return true;
    }

    private void appendLiveAssistantTranscript(TuiScreenModel screenModel) {
        TuiAssistantViewModel assistant = screenModel == null ? null : screenModel.getAssistantViewModel();
        String reasoning = normalizeLiveBuffer(assistant == null ? null : assistant.getReasoningText());
        String text = normalizeLiveBuffer(assistant == null ? null : assistant.getText());

        if (!isBlank(reasoning)) {
            if (!reasoning.startsWith(liveReasoningPrinted)) {
                liveReasoningPrinted = "";
            }
            appendLiveSuffix(LiveBlock.REASONING, liveReasoningPrinted, reasoning.substring(liveReasoningPrinted.length()));
            liveReasoningPrinted = reasoning;
        }

        if (!isBlank(text)) {
            if (!text.startsWith(liveTextPrinted)) {
                liveTextPrinted = "";
            }
            appendLiveSuffix(LiveBlock.TEXT, liveTextPrinted, text.substring(liveTextPrinted.length()));
            liveTextPrinted = text;
        }

        if (isBlank(reasoning) && isBlank(text)) {
            closeActiveLiveBlock();
        }
    }

    private String normalizeLiveBuffer(String value) {
        return value == null ? "" : value.replace("\r", "");
    }

    private void appendLiveSuffix(LiveBlock mode, String alreadyPrinted, String suffix) {
        if (mode == null || mode == LiveBlock.NONE || suffix == null || suffix.isEmpty()) {
            return;
        }
        clearFooter();
        StringBuilder chunk = new StringBuilder();
        if (activeLiveBlock != mode) {
            if (activeLiveBlock != LiveBlock.NONE) {
                chunk.append("\r\n\r\n");
            }
            activeLiveBlock = mode;
        }
        boolean useFirstPrefix = isBlank(alreadyPrinted);
        boolean needsPrefix = useFirstPrefix || alreadyPrinted.endsWith("\n");
        String normalized = suffix.replace("\r", "");
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\n') {
                chunk.append("\r\n");
                needsPrefix = true;
                continue;
            }
            if (needsPrefix) {
                chunk.append(useFirstPrefix ? mode.firstPrefix() : mode.continuationPrefix());
                useFirstPrefix = false;
                needsPrefix = false;
            }
            chunk.append(ch);
        }
        String rendered = chunk.toString();
        if (rendered.isEmpty()) {
            return;
        }
        terminal.print(mode == LiveBlock.REASONING ? colorize(DIM, rendered) : rendered);
    }

    private void closeActiveLiveBlock() {
        if (activeLiveBlock == LiveBlock.NONE) {
            return;
        }
        clearFooter();
        terminal.print("\r\n\r\n");
        activeLiveBlock = LiveBlock.NONE;
    }

    private void resetLiveDedupState() {
        liveReasoningPrinted = "";
        liveTextPrinted = "";
    }

    private void appendAssistantOutput(TuiScreenModel screenModel) {
        String output = screenModel.getAssistantOutput();
        if (isBlank(output) || output.equals(lastAssistantOutput)) {
            return;
        }
        lastAssistantOutput = output;
        if (printedEventKeys.isEmpty() && output.startsWith(INITIAL_HINT)) {
            return;
        }
        List<String> lines = output.startsWith(INITIAL_HINT)
                ? formatInitialHintLines(output)
                : bulletBlock(splitLines(output), null);
        if (!lines.isEmpty()) {
            resetLiveDedupState();
            printTranscriptLines(lines);
        }
    }

    private void renderFooter(TuiScreenModel screenModel) {
        if (!entered) {
            clearFooter();
            return;
        }
        Footer footer = buildFooter(screenModel);
        if (footer == null || footer.lines.isEmpty()) {
            clearFooter();
            return;
        }
        String signature = buildFooterSignature(footer);
        if (signature.equals(footerSignature)) {
            return;
        }
        moveToFooterTop();
        clearFooterArea();
        printFooterLines(footer.lines);
        placeCursor(footer);
        footerLineCount = footer.lines.size();
        footerSignature = signature;
    }

    private Footer buildFooter(TuiScreenModel screenModel) {
        int width = resolveWidth(screenModel);
        List<FooterLine> lines = new ArrayList<FooterLine>();
        TuiAssistantViewModel assistant = screenModel.getAssistantViewModel();

        FooterLine statusLine = buildStatusLine(assistant, width);
        if (statusLine != null && !isBlank(statusLine.text)) {
            lines.add(statusLine);
        }

        List<FooterLine> previewLines = buildPreviewLines(screenModel, assistant, width);
        lines.addAll(previewLines);

        TuiInteractionState interaction = screenModel.getInteractionState();
        String input = interaction == null ? "" : firstNonBlank(interaction.getInputBuffer(), "");
        InputViewport viewport = cropInputForViewport(input, width - 2);
        int inputLineIndex = lines.size();
        lines.add(new FooterLine("> " + viewport.visibleText, null));

        if (interaction != null && interaction.isPaletteOpen()) {
            lines.addAll(buildPaletteLines(interaction, width));
        }

        int cursorColumn = 2 + viewport.cursorColumns;
        return new Footer(lines, inputLineIndex, cursorColumn);
    }

    private FooterLine buildStatusLine(TuiAssistantViewModel assistant, int width) {
        if (assistant == null || assistant.getPhase() == null || assistant.getPhase() == TuiAssistantPhase.IDLE) {
            return new FooterLine(crop(BULLET + "Ready", width), DIM);
        }
        if (assistant.getPhase() == TuiAssistantPhase.ERROR) {
            return new FooterLine(crop(BULLET + "Error", width), RED);
        }
        if (assistant.getPhase() == TuiAssistantPhase.COMPLETE) {
            return new FooterLine(crop(BULLET + "Done", width), GREEN);
        }
        TuiAssistantToolView activeTool = assistant.getPhase() == TuiAssistantPhase.WAITING_TOOL_RESULT
                ? firstPendingTool(assistant)
                : null;
        if (activeTool != null) {
            String working = animatedStatusPrefix("Working", assistant);
            return new FooterLine(crop(working + ": " + toolPrimaryLabel(activeTool, true), width), YELLOW);
        }
        String detail = safeTrim(assistant.getPhaseDetail());
        String prefix = statusPrefix(assistant);
        String normalizedDetail = normalizeStatusDetail(assistant.getPhase(), detail);
        String text = isBlank(normalizedDetail) ? prefix : prefix + ": " + normalizedDetail;
        return new FooterLine(crop(text, width), DIM);
    }

    private String statusPrefix(TuiAssistantViewModel assistant) {
        TuiAssistantPhase phase = assistant == null ? null : assistant.getPhase();
        if (phase == TuiAssistantPhase.THINKING) {
            return animatedStatusPrefix("Thinking", assistant);
        }
        if (phase == TuiAssistantPhase.GENERATING) {
            return animatedStatusPrefix("Responding", assistant);
        }
        if (phase == TuiAssistantPhase.WAITING_TOOL_RESULT) {
            return animatedStatusPrefix("Working", assistant);
        }
        String phaseName = phase == null ? "" : phase.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return BULLET + capitalize(phaseName);
    }

    private String animatedStatusPrefix(String label, TuiAssistantViewModel assistant) {
        return BULLET + label + " " + spinnerFrame(assistant == null ? 0 : assistant.getAnimationTick());
    }

    private String spinnerFrame(int animationTick) {
        int frameCount = SPINNER_FRAMES.length;
        if (frameCount == 0) {
            return "";
        }
        int index = animationTick % frameCount;
        if (index < 0) {
            index += frameCount;
        }
        return SPINNER_FRAMES[index];
    }

    private String normalizeStatusDetail(TuiAssistantPhase phase, String detail) {
        if (isBlank(detail)) {
            return "";
        }
        if (phase == TuiAssistantPhase.THINKING) {
            if ("Waiting for model output...".equalsIgnoreCase(detail)
                    || "Streaming reasoning...".equalsIgnoreCase(detail)
                    || "Preparing next step...".equalsIgnoreCase(detail)
                    || "Tool finished, continuing...".equalsIgnoreCase(detail)) {
                return "";
            }
            if (detail.regionMatches(true, 0, "Thinking about: ", 0, "Thinking about: ".length())) {
                return safeTrim(detail.substring("Thinking about: ".length()));
            }
        }
        if (phase == TuiAssistantPhase.GENERATING && "Streaming model output...".equalsIgnoreCase(detail)) {
            return "";
        }
        if (phase == TuiAssistantPhase.WAITING_TOOL_RESULT && "Waiting for tool result...".equalsIgnoreCase(detail)) {
            return "";
        }
        return detail;
    }

    private List<FooterLine> buildPreviewLines(TuiScreenModel screenModel,
                                               TuiAssistantViewModel assistant,
                                               int width) {
        List<FooterLine> lines = new ArrayList<FooterLine>();
        if (printedEventKeys.isEmpty() && shouldShowInitialHint(screenModel, assistant)) {
            List<String> hintLines = formatInitialHintLines(firstNonBlank(screenModel.getAssistantOutput(), ""));
            for (String hintLine : hintLines) {
                if (!isBlank(hintLine)) {
                    lines.add(new FooterLine(crop(hintLine, width), DIM));
                }
            }
        }
        return lines;
    }

    private boolean shouldShowInitialHint(TuiScreenModel screenModel, TuiAssistantViewModel assistant) {
        if (screenModel == null) {
            return false;
        }
        if (assistant != null && assistant.getPhase() != null && assistant.getPhase() != TuiAssistantPhase.IDLE) {
            return false;
        }
        String output = screenModel.getAssistantOutput();
        return !isBlank(output) && output.startsWith(INITIAL_HINT);
    }

    private List<FooterLine> buildPaletteLines(TuiInteractionState interaction, int width) {
        List<FooterLine> lines = new ArrayList<FooterLine>();
        lines.add(new FooterLine(crop(paletteHeader(interaction), width), DIM));
        List<TuiPaletteItem> items = interaction.getPaletteItems();
        if (items == null || items.isEmpty()) {
            lines.add(new FooterLine(crop(BULLET + "No commands", width), DIM));
            return lines;
        }
        int selected = Math.max(0, Math.min(interaction.getPaletteSelectedIndex(), items.size() - 1));
        int maxLines = Math.min(Math.max(2, MAX_PALETTE_LINES - 1), Math.max(2, resolvePaletteCapacity() - 1));
        int start = Math.max(0, selected - (maxLines / 2));
        int end = Math.min(items.size(), start + maxLines);
        if (end - start < maxLines) {
            start = Math.max(0, end - maxLines);
        }
        if (start > 0) {
            lines.add(new FooterLine(crop(BULLET + ELLIPSIS, width), DIM));
        }
        for (int i = start; i < end; i++) {
            TuiPaletteItem item = items.get(i);
            String label = firstNonBlank(item.getLabel(), item.getCommand());
            String detail = isBlank(item.getDetail()) ? "" : "  " + item.getDetail();
            String line = crop(BULLET + firstNonBlank(label, "") + detail, width);
            lines.add(new FooterLine(line, i == selected ? INVERSE : null));
        }
        if (end < items.size()) {
            lines.add(new FooterLine(crop(BULLET + ELLIPSIS, width), DIM));
        }
        return lines;
    }

    private String paletteHeader(TuiInteractionState interaction) {
        if (interaction == null) {
            return BULLET + "Commands";
        }
        String query = safeTrim(interaction.getPaletteQuery());
        if (interaction.getPaletteMode() == TuiInteractionState.PaletteMode.SLASH && !isBlank(query)) {
            return BULLET + "Commands: " + (query.startsWith("/") ? query : "/" + query);
        }
        return BULLET + "Commands";
    }

    private void printTranscriptLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        closeActiveLiveBlock();
        clearFooter();
        for (String line : lines) {
            terminal.println(line == null ? "" : line);
        }
        terminal.println("");
    }

    private void clearFooter() {
        if (footerLineCount <= 0) {
            footerSignature = null;
            return;
        }
        moveToFooterTop();
        clearFooterArea();
        footerLineCount = 0;
        footerSignature = null;
    }

    private void moveToFooterTop() {
        if (footerLineCount <= 0 || !terminal.supportsAnsi()) {
            return;
        }
        terminal.print("\r");
        if (footerLineCount > 1) {
            terminal.print(ESC + (footerLineCount - 1) + "A");
        }
    }

    private void clearFooterArea() {
        if (!terminal.supportsAnsi() || footerLineCount <= 0) {
            return;
        }
        for (int i = 0; i < footerLineCount; i++) {
            terminal.print("\r");
            terminal.print(ESC + "2K");
            if (i + 1 < footerLineCount) {
                terminal.print("\r\n");
            }
        }
        terminal.print("\r");
        if (footerLineCount > 1) {
            terminal.print(ESC + (footerLineCount - 1) + "A");
        }
    }

    private void printFooterLines(List<FooterLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                terminal.print("\r\n");
            }
            terminal.print("\r");
            terminal.print(ESC + "2K");
            FooterLine line = lines.get(i);
            terminal.print(line == null ? "" : line.render(this));
        }
    }

    private void placeCursor(Footer footer) {
        if (!terminal.supportsAnsi() || footer.lines.isEmpty()) {
            return;
        }
        terminal.print("\r");
        int moveUp = footer.lines.size() - 1 - footer.inputLineIndex;
        if (moveUp > 0) {
            terminal.print(ESC + moveUp + "A");
        }
        if (footer.cursorColumn > 0) {
            terminal.print(ESC + footer.cursorColumn + "C");
        }
    }

    private int resolveWidth(TuiScreenModel screenModel) {
        int width = screenModel.getRenderContext() == null ? 0 : screenModel.getRenderContext().getTerminalColumns();
        if (width <= 0 && terminal != null) {
            width = terminal.getTerminalColumns();
        }
        return Math.max(40, width <= 0 ? 120 : width);
    }

    private int resolvePaletteCapacity() {
        int rows = terminal == null ? 0 : terminal.getTerminalRows();
        if (rows <= 0) {
            return MAX_PALETTE_LINES;
        }
        return Math.max(3, Math.min(MAX_PALETTE_LINES, rows / 3));
    }

    private List<String> formatEvent(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return new ArrayList<String>();
        }
        SessionEventType type = event.getType();
        Map<String, Object> payload = event.getPayload();
        if (type == SessionEventType.USER_MESSAGE) {
            return bulletBlock(splitLines(firstNonBlank(payloadString(payload, "input"), event.getSummary())), null);
        }
        if (type == SessionEventType.ASSISTANT_MESSAGE) {
            String kind = payloadString(payload, "kind");
            List<String> content = splitLines(firstNonBlank(payloadString(payload, "output"), event.getSummary()));
            if ("reasoning".equalsIgnoreCase(kind)) {
                return formatReasoning(content);
            }
            return bulletBlock(content, null);
        }
        if (type == SessionEventType.TOOL_CALL) {
            return new ArrayList<String>();
        }
        if (type == SessionEventType.TOOL_RESULT) {
            return formatToolResult(payload);
        }
        if (type == SessionEventType.ERROR) {
            return bulletBlock(splitLines(firstNonBlank(payloadString(payload, "error"), event.getSummary())), "Error: ");
        }
        if (type == SessionEventType.COMPACT) {
            return bulletBlock(splitLines(firstNonBlank(payloadString(payload, "summary"), event.getSummary(), "context compacted")), "Note: ");
        }
        if (type == SessionEventType.AUTO_CONTINUE
                || type == SessionEventType.AUTO_STOP
                || type == SessionEventType.BLOCKED) {
            return bulletBlock(splitLines(firstNonBlank(event.getSummary(), type.name().toLowerCase(Locale.ROOT).replace('_', ' '))), "Note: ");
        }
        if (type == SessionEventType.SESSION_RESUMED || type == SessionEventType.SESSION_FORKED) {
            return bulletBlock(splitLines(firstNonBlank(event.getSummary(), type.name().toLowerCase(Locale.ROOT).replace('_', ' '))), "Note: ");
        }
        if (type == SessionEventType.TASK_CREATED || type == SessionEventType.TASK_UPDATED) {
            return formatTaskEvent(payload, event.getSummary());
        }
        if (type == SessionEventType.TEAM_MESSAGE) {
            return formatTeamMessageEvent(payload, event.getSummary());
        }
        if (type == SessionEventType.PROCESS_STARTED
                || type == SessionEventType.PROCESS_UPDATED
                || type == SessionEventType.PROCESS_STOPPED) {
            return formatProcessEvent(type, payload, event.getSummary());
        }
        return new ArrayList<String>();
    }

    private List<String> formatTaskEvent(Map<String, Object> payload, String summary) {
        List<String> lines = new ArrayList<String>();
        lines.add(BULLET + "Note: " + firstNonBlank(summary, payloadString(payload, "title"), "delegate task"));
        String detail = firstNonBlank(payloadString(payload, "detail"), payloadString(payload, "error"), payloadString(payload, "output"));
        if (!isBlank(detail)) {
            lines.add(TREE + detail);
        }
        String childSessionId = payloadString(payload, "childSessionId");
        if (!isBlank(childSessionId)) {
            lines.add(INDENT + "child session: " + childSessionId);
        }
        String status = payloadString(payload, "status");
        if (!isBlank(status)) {
            lines.add(INDENT + "status: " + status);
        }
        return lines;
    }

    private List<String> formatTeamMessageEvent(Map<String, Object> payload, String summary) {
        List<String> lines = new ArrayList<String>();
        lines.add(BULLET + "Note: " + firstNonBlank(summary, payloadString(payload, "title"), "team message"));
        String taskId = payloadString(payload, "taskId");
        if (!isBlank(taskId)) {
            lines.add(TREE + "task: " + taskId);
        }
        String detail = firstNonBlank(payloadString(payload, "content"), payloadString(payload, "detail"));
        if (!isBlank(detail)) {
            List<String> detailLines = splitLines(detail);
            for (int i = 0; i < detailLines.size(); i++) {
                lines.add((i == 0 ? INDENT : INDENT) + detailLines.get(i));
            }
        }
        return lines;
    }

    private List<String> formatReasoning(List<String> content) {
        List<String> lines = new ArrayList<String>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        String continuation = repeat(' ', (BULLET + "Thinking: ").length());
        for (int i = 0; i < content.size(); i++) {
            String prefix = i == 0 ? BULLET + "Thinking: " : continuation;
            lines.add(colorize(DIM, prefix + content.get(i)));
        }
        return lines;
    }

    private List<String> formatToolResult(Map<String, Object> payload) {
        List<String> lines = new ArrayList<String>();
        if (payload == null) {
            return lines;
        }
        String toolName = firstNonBlank(payloadString(payload, "tool"), "tool");
        String title = normalizeToolLabel(firstNonBlank(payloadString(payload, "title"), toolName));
        String detail = safeTrim(payloadString(payload, "detail"));
        String output = payloadString(payload, "output");
        boolean failed = looksLikeToolFailure(output, detail);

        if ("apply_patch".equals(toolName)) {
            lines.add(failed ? colorize(RED, BULLET + "Tool failed apply_patch") : colorize(YELLOW, BULLET + "Applied patch"));
        } else if ("write_file".equals(toolName)) {
            String label = crop(normalizeToolLabel(title), 108);
            lines.add(failed
                    ? colorize(RED, BULLET + "Tool failed write " + crop(label, 96))
                    : colorize(YELLOW, BULLET + "Wrote " + label));
        } else if (failed) {
            lines.add(colorize(RED, BULLET + "Tool failed " + crop(title, 96)));
        } else {
            lines.add(colorize(YELLOW, BULLET + "Ran " + crop(title, 108)));
        }

        if (!isBlank(detail)) {
            lines.add(TREE + detail);
        }
        List<String> previewLines = payloadLines(payload, "previewLines");
        int max = Math.min(MAX_TOOL_PREVIEW_LINES, previewLines.size());
        for (int i = 0; i < max; i++) {
            lines.add((i == 0 && isBlank(detail) ? TREE : INDENT) + previewLines.get(i));
        }
        if (previewLines.size() > max) {
            lines.add(colorize(DIM, INDENT + ELLIPSIS + " +" + (previewLines.size() - max) + " lines"));
        }
        return lines;
    }

    private List<String> formatProcessEvent(SessionEventType type, Map<String, Object> payload, String summary) {
        List<String> lines = new ArrayList<String>();
        String processId = firstNonBlank(payloadString(payload, "processId"), "unknown-process");
        String status = safeTrim(payloadString(payload, "status"));
        String command = safeTrim(payloadString(payload, "command"));
        String workingDirectory = safeTrim(payloadString(payload, "workingDirectory"));

        if (type == SessionEventType.PROCESS_STARTED) {
            lines.add(BULLET + "Process started: " + processId);
        } else if (type == SessionEventType.PROCESS_STOPPED) {
            lines.add(BULLET + "Process stopped: " + processId);
        } else {
            String label = BULLET + "Process: " + processId;
            if (!isBlank(status)) {
                label = label + " (" + status + ")";
            }
            lines.add(label);
        }

        if (!isBlank(command)) {
            lines.add(TREE + command);
        } else if (!isBlank(summary)) {
            lines.add(TREE + summary);
        }
        if (!isBlank(workingDirectory)) {
            lines.add(INDENT + "cwd " + workingDirectory);
        }
        return lines;
    }

    private InputViewport cropInputForViewport(String input, int availableColumns) {
        String safeInput = input == null ? "" : input;
        int width = Math.max(8, availableColumns);
        if (displayWidth(safeInput) <= width) {
            return new InputViewport(safeInput, displayWidth(safeInput));
        }
        int visibleWidth = 0;
        StringBuilder builder = new StringBuilder();
        for (int i = safeInput.length() - 1; i >= 0; i--) {
            char ch = safeInput.charAt(i);
            int charWidth = charWidth(ch);
            if (visibleWidth + charWidth > width - 3 && builder.length() > 0) {
                break;
            }
            builder.insert(0, ch);
            visibleWidth += charWidth;
        }
        String visible = "..." + builder.toString();
        return new InputViewport(visible, Math.min(width, displayWidth(visible)));
    }

    private List<String> bulletBlock(List<String> content, String label) {
        List<String> lines = new ArrayList<String>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        String firstPrefix = BULLET + firstNonBlank(label, "");
        String continuation = repeat(' ', displayWidth(firstPrefix));
        for (int i = 0; i < content.size(); i++) {
            String line = content.get(i) == null ? "" : content.get(i);
            lines.add((i == 0 ? firstPrefix : continuation) + line);
        }
        return lines;
    }

    private List<String> formatInitialHintLines(String output) {
        List<String> lines = new ArrayList<String>();
        List<String> rawLines = splitLines(output);
        for (String rawLine : rawLines) {
            if (!isBlank(rawLine)) {
                lines.add(BULLET + rawLine);
            }
        }
        return lines;
    }

    private List<String> appendLabelBlock(String label, List<String> content) {
        List<String> lines = new ArrayList<String>();
        if (!isBlank(label)) {
            lines.add(label);
        }
        if (content != null) {
            lines.addAll(content);
        }
        return lines;
    }

    private List<String> prefixBlock(String prefix, List<String> content) {
        List<String> lines = new ArrayList<String>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        String continuation = repeat(' ', prefix.length());
        for (int i = 0; i < content.size(); i++) {
            lines.add((i == 0 ? prefix : continuation) + content.get(i));
        }
        return lines;
    }

    private String buildFooterSignature(Footer footer) {
        StringBuilder builder = new StringBuilder();
        builder.append(footer.inputLineIndex).append('|').append(footer.cursorColumn).append('|');
        for (FooterLine line : footer.lines) {
            if (line == null) {
                builder.append('\n');
                continue;
            }
            builder.append(firstNonBlank(line.style, ""))
                    .append('|')
                    .append(firstNonBlank(line.text, ""))
                    .append('\n');
        }
        return builder.toString();
    }

    private String eventKey(SessionEvent event) {
        if (!isBlank(event.getEventId())) {
            return event.getEventId();
        }
        return firstNonBlank(event.getTurnId(), "session")
                + "|" + event.getTimestamp()
                + "|" + (event.getStep() == null ? "" : event.getStep().toString())
                + "|" + event.getType().name()
                + "|" + firstNonBlank(event.getSummary(), "");
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            return lines;
        }
        String[] raw = text.replace("\r", "").split("\n", -1);
        for (String line : raw) {
            lines.add(line == null ? "" : line);
        }
        return lines;
    }

    private List<String> payloadLines(Map<String, Object> payload, String key) {
        List<String> lines = new ArrayList<String>();
        if (payload == null || isBlank(key) || !payload.containsKey(key)) {
            return lines;
        }
        Object value = payload.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (item != null) {
                    lines.add(String.valueOf(item));
                }
            }
            return lines;
        }
        if (value != null) {
            lines.add(String.valueOf(value));
        }
        return lines;
    }

    private String payloadString(Map<String, Object> payload, String key) {
        if (payload == null || isBlank(key) || !payload.containsKey(key)) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private TuiAssistantToolView firstPendingTool(TuiAssistantViewModel assistant) {
        if (assistant == null || assistant.getTools() == null || assistant.getTools().isEmpty()) {
            return null;
        }
        for (TuiAssistantToolView tool : assistant.getTools()) {
            if (tool != null && "pending".equalsIgnoreCase(safeTrim(tool.getStatus()))) {
                return tool;
            }
        }
        return assistant.getTools().get(0);
    }

    private String toolPrimaryLabel(TuiAssistantToolView tool, boolean pending) {
        if (tool == null) {
            return pending ? "Thinking" : "Done";
        }
        String label = normalizeToolLabel(firstNonBlank(tool.getTitle(), tool.getToolName(), "tool"));
        if ("write_file".equals(tool.getToolName())) {
            return pending ? "Writing " + label : "Wrote " + label;
        }
        return pending ? "Running " + label : "Ran " + label;
    }

    private String normalizeToolLabel(String title) {
        String value = firstNonBlank(title, "tool").trim();
        String[] prefixes = new String[]{"$ ", "read ", "write ", "bash logs ", "bash status ", "bash write ", "bash stop "};
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length()).trim();
            }
        }
        return value;
    }

    private boolean looksLikeToolFailure(String output, String detail) {
        String combined = (firstNonBlank(output, "") + " " + firstNonBlank(detail, "")).toLowerCase(Locale.ROOT);
        return combined.contains("error")
                || combined.contains("failed")
                || combined.contains("exception")
                || combined.contains("unsupported patch line")
                || combined.contains("cannot invoke");
    }

    private String shortenSessionId(String sessionId) {
        String value = safeTrim(sessionId);
        if (isBlank(value)) {
            return null;
        }
        if (value.length() <= 12) {
            return value;
        }
        return value.substring(0, 12);
    }

    private String crop(String value, int width) {
        String safe = value == null ? "" : value;
        if (width <= 0 || displayWidth(safe) <= width) {
            return safe;
        }
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            int charWidth = charWidth(ch);
            if (used + charWidth > Math.max(0, width - 3)) {
                break;
            }
            builder.append(ch);
            used += charWidth;
        }
        return builder.append("...").toString();
    }

    private int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += charWidth(text.charAt(i));
        }
        return width;
    }

    private int charWidth(char ch) {
        int width = WCWidth.wcwidth(ch);
        return width <= 0 ? 1 : width;
    }

    private String trimBlankEdges(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
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
        return values.length == 0 ? null : values[values.length - 1];
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }

    private String lastPathSegment(String value) {
        if (isBlank(value)) {
            return ".";
        }
        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private String capitalize(String value) {
        if (isBlank(value)) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String colorize(String color, String text) {
        if (!terminal.supportsAnsi() || isBlank(color)) {
            return firstNonBlank(text, "");
        }
        return color + firstNonBlank(text, "") + RESET;
    }

    private static final class Footer {

        private final List<FooterLine> lines;
        private final int inputLineIndex;
        private final int cursorColumn;

        private Footer(List<FooterLine> lines, int inputLineIndex, int cursorColumn) {
            this.lines = lines;
            this.inputLineIndex = inputLineIndex;
            this.cursorColumn = cursorColumn;
        }
    }

    private static final class FooterLine {

        private final String text;
        private final String style;

        private FooterLine(String text, String style) {
            this.text = text == null ? "" : text;
            this.style = style;
        }

        private String render(DefaultAppendOnlyTuiRuntime runtime) {
            if (runtime == null || isBlank(style)) {
                return text;
            }
            return runtime.colorize(style, text);
        }

        private static boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    private static final class InputViewport {

        private final String visibleText;
        private final int cursorColumns;

        private InputViewport(String visibleText, int cursorColumns) {
            this.visibleText = visibleText == null ? "" : visibleText;
            this.cursorColumns = Math.max(0, cursorColumns);
        }
    }

    private enum LiveBlock {
        NONE,
        REASONING,
        TEXT;

        private String firstPrefix() {
            return this == REASONING ? BULLET + "Thinking: " : BULLET;
        }

        private String continuationPrefix() {
            int count = this == REASONING ? (BULLET + "Thinking: ").length() : BULLET.length();
            StringBuilder builder = new StringBuilder(Math.max(0, count));
            for (int i = 0; i < count; i++) {
                builder.append(' ');
            }
            return builder.toString();
        }
    }
}

