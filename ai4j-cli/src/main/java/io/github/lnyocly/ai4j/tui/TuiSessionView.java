package io.github.lnyocly.ai4j.tui;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TuiSessionView implements TuiRenderer {

    private static final String ASSISTANT_PREFIX = "\u0000assistant>";
    private static final String ASSISTANT_CONTINUATION_PREFIX = "\u0000assistant+>";
    private static final String REASONING_PREFIX = "\u0000thinking>";
    private static final String REASONING_CONTINUATION_PREFIX = "\u0000thinking+>";
    private static final String CODE_LINE_PREFIX = "\u0000code>";
    private static final String BULLET_PREFIX = "\u2022 ";
    private static final String BULLET_CONTINUATION = "  ";
    private static final String THINKING_LABEL = "\u2022 Thinking: ";
    private static final String NOTE_LABEL = "\u2022 Note: ";
    private static final String ERROR_LABEL = "\u2022 Error: ";
    private static final String PROCESS_LABEL = "\u2022 Process: ";
    private static final String STARTUP_LABEL = "\u2022 ";
    private static final String[] STATUS_FRAMES = new String[]{"-", "\\", "|", "/"};
    private static final int TRANSCRIPT_VIEWPORT_LINES = 24;
    private static final int MAX_REPLAY_LINES = 18;
    private static final int MAX_TOOL_PREVIEW_LINES = 4;
    private static final int MAX_PROCESS_LOG_LINES = 8;
    private static final int MAX_OVERLAY_ITEMS = 8;
    private static final Set<String> CODE_KEYWORDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "abstract", "async", "await", "boolean", "break", "byte", "case", "catch", "cd",
            "char", "class", "const", "continue", "default", "delete", "do", "docker", "done",
            "double", "echo", "elif", "else", "enum", "export", "extends", "false", "fi",
            "final", "finally", "float", "for", "function", "git", "if", "implements", "import",
            "in", "int", "interface", "java", "kubectl", "let", "long", "mvn", "new", "null",
            "npm", "package", "pnpm", "private", "protected", "public", "return", "rg", "select",
            "set", "short", "static", "switch", "then", "this", "throw", "throws", "true", "try",
            "typeof", "unset", "var", "void", "while"
    )));

    private final boolean ansi;
    private TuiConfig config;
    private TuiTheme theme;
    private List<CodingSessionDescriptor> cachedSessions = Collections.emptyList();
    private List<String> cachedHistory = Collections.emptyList();
    private List<String> cachedTree = Collections.emptyList();
    private List<String> cachedCommands = Collections.emptyList();
    private List<SessionEvent> cachedEvents = Collections.emptyList();
    private List<String> cachedReplay = Collections.emptyList();
    private List<String> cachedTeamBoard = Collections.emptyList();
    private BashProcessInfo inspectedProcess;
    private BashProcessLogChunk inspectedProcessLogs;
    private String assistantOutput;
    private TuiAssistantViewModel assistantViewModel;
    private TuiInteractionState currentInteractionState;

    public TuiSessionView(TuiConfig config, TuiTheme theme, boolean ansi) {
        this.config = config == null ? new TuiConfig() : config;
        this.theme = theme == null ? new TuiTheme() : theme;
        this.ansi = ansi;
    }

    public void updateTheme(TuiConfig config, TuiTheme theme) {
        this.config = config == null ? new TuiConfig() : config;
        this.theme = theme == null ? new TuiTheme() : theme;
    }

    public int getMaxEvents() { return config == null || config.getMaxEvents() <= 0 ? 10 : config.getMaxEvents(); }
    public String getThemeName() { return theme == null ? "default" : theme.getName(); }
    public void setCachedSessions(List<CodingSessionDescriptor> sessions) { this.cachedSessions = trimObjects(sessions, 12); }
    public void setCachedEvents(List<SessionEvent> events) { this.cachedEvents = events == null ? Collections.<SessionEvent>emptyList() : new ArrayList<SessionEvent>(events); }
    public void setCachedHistory(List<String> historyLines) { this.cachedHistory = copyLines(historyLines, 12); }
    public void setCachedTree(List<String> treeLines) { this.cachedTree = copyLines(treeLines, 12); }
    public void setCachedCommands(List<String> commandLines) { this.cachedCommands = copyLines(commandLines, 20); }
    public void setAssistantOutput(String assistantOutput) { this.assistantOutput = assistantOutput; }
    public void setAssistantViewModel(TuiAssistantViewModel assistantViewModel) { this.assistantViewModel = assistantViewModel; }
    public void setCachedReplay(List<String> replayLines) { this.cachedReplay = replayLines == null ? Collections.<String>emptyList() : new ArrayList<String>(replayLines); }
    public void setCachedTeamBoard(List<String> teamBoardLines) { this.cachedTeamBoard = teamBoardLines == null ? Collections.<String>emptyList() : new ArrayList<String>(teamBoardLines); }
    public void setProcessInspector(BashProcessInfo process, BashProcessLogChunk logs) { this.inspectedProcess = process; this.inspectedProcessLogs = logs; }

    @Override
    public String render(TuiScreenModel screenModel) {
        TuiScreenModel model = screenModel == null ? TuiScreenModel.builder().build() : screenModel;
        applyModel(model);
        String header = renderHeader(model.getDescriptor(), model.getRenderContext());
        String overlay = renderOverlay(model.getInteractionState());
        String composer = renderComposer(model.getInteractionState());
        String composerAddon = renderComposerAddon(model.getInteractionState());
        int transcriptViewport = resolveTranscriptViewport(model.getRenderContext(), overlay, composerAddon);
        StringBuilder out = new StringBuilder();
        out.append(header);

        List<String> feedLines = buildFeedLines(transcriptViewport);
        if (!feedLines.isEmpty()) {
            out.append('\n').append('\n');
            appendLines(out, feedLines);
        }

        if (!isBlank(overlay)) {
            out.append('\n').append('\n').append(overlay);
        }

        out.append('\n').append('\n').append(composer);

        if (!isBlank(composerAddon)) {
            out.append('\n').append('\n').append(composerAddon);
        }
        return out.toString();
    }

    public String render(ManagedCodingSession session, TuiRenderContext context, TuiInteractionState interactionState) {
        return render(TuiScreenModel.builder()
                .config(config).theme(theme)
                .descriptor(session == null ? null : session.toDescriptor())
                .snapshot(session == null || session.getSession() == null ? null : session.getSession().snapshot())
                .checkpoint(session == null || session.getSession() == null ? null : session.getSession().exportState().getCheckpoint())
                .renderContext(context).interactionState(interactionState)
                .cachedSessions(cachedSessions).cachedHistory(cachedHistory).cachedTree(cachedTree)
                .cachedCommands(cachedCommands).cachedEvents(cachedEvents).cachedReplay(cachedReplay)
                .cachedTeamBoard(cachedTeamBoard)
                .inspectedProcess(inspectedProcess).inspectedProcessLogs(inspectedProcessLogs)
                .assistantOutput(assistantOutput).assistantViewModel(assistantViewModel)
                .build());
    }

    private void applyModel(TuiScreenModel screenModel) {
        if (screenModel == null) {
            return;
        }
        if (screenModel.getConfig() != null || screenModel.getTheme() != null) {
            updateTheme(screenModel.getConfig(), screenModel.getTheme());
        }
        currentInteractionState = screenModel.getInteractionState();
        setCachedSessions(screenModel.getCachedSessions());
        setCachedHistory(screenModel.getCachedHistory());
        setCachedTree(screenModel.getCachedTree());
        setCachedCommands(screenModel.getCachedCommands());
        setCachedEvents(screenModel.getCachedEvents());
        setCachedReplay(screenModel.getCachedReplay());
        setCachedTeamBoard(screenModel.getCachedTeamBoard());
        setProcessInspector(screenModel.getInspectedProcess(), screenModel.getInspectedProcessLogs());
        setAssistantOutput(screenModel.getAssistantOutput());
        setAssistantViewModel(screenModel.getAssistantViewModel());
    }

    private String renderHeader(CodingSessionDescriptor descriptor, TuiRenderContext context) {
        String model = clip(firstNonBlank(
                descriptor == null ? null : descriptor.getModel(),
                context == null ? null : context.getModel(),
                "model"
        ), 28);
        String workspace = clip(lastPathSegment(firstNonBlank(
                descriptor == null ? null : descriptor.getWorkspace(),
                context == null ? null : context.getWorkspace(),
                "."
        )), 32);
        String sessionId = shortenSessionId(descriptor == null ? null : descriptor.getSessionId());
        StringBuilder line = new StringBuilder();
        line.append(TuiAnsi.bold(TuiAnsi.fg("AI4J", theme.getBrand(), ansi), ansi));
        line.append("  ");
        line.append(TuiAnsi.fg(model, theme.getSuccess(), ansi));
        line.append("  ");
        line.append(TuiAnsi.fg(workspace, theme.getMuted(), ansi));
        if (!isBlank(sessionId)) {
            line.append("  ");
            line.append(TuiAnsi.fg(sessionId, theme.getMuted(), ansi));
        }
        return line.toString();
    }

    private List<String> buildFeedLines(int transcriptViewport) {
        List<String> transcriptLines = new ArrayList<String>();
        appendEventFeed(transcriptLines);
        appendLiveAssistantFeed(transcriptLines);
        appendAssistantNote(transcriptLines);
        if (transcriptLines.isEmpty()) {
            appendStartupTips(transcriptLines);
        }
        trimTrailingBlankLines(transcriptLines);
        return sliceTranscriptLines(transcriptLines, transcriptViewport);
    }

    private void appendEventFeed(List<String> lines) {
        if (cachedEvents == null || cachedEvents.isEmpty()) {
            return;
        }
        Set<String> completedToolKeys = buildCompletedToolKeys(cachedEvents);
        for (int i = 0; i < cachedEvents.size(); i++) {
            SessionEvent event = cachedEvents.get(i);
            if (event == null || event.getType() == null) {
                continue;
            }
            SessionEventType type = event.getType();
            if (type == SessionEventType.USER_MESSAGE) {
                appendWrappedText(lines, BULLET_PREFIX, firstNonBlank(payloadString(event.getPayload(), "input"), event.getSummary()),
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                lines.add("");
                continue;
            }
            if (type == SessionEventType.ASSISTANT_MESSAGE) {
                String output = firstNonBlank(payloadString(event.getPayload(), "output"), event.getSummary());
                if ("reasoning".equalsIgnoreCase(payloadString(event.getPayload(), "kind"))) {
                    appendReasoningMarkdown(lines, output, Integer.MAX_VALUE, Integer.MAX_VALUE);
                } else {
                    appendAssistantMarkdown(lines, output, Integer.MAX_VALUE, Integer.MAX_VALUE);
                }
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.TOOL_CALL || type == SessionEventType.TOOL_RESULT) {
                if (type == SessionEventType.TOOL_CALL
                        && completedToolKeys.contains(buildToolIdentity(event.getPayload()))) {
                    continue;
                }
                appendToolEventLines(lines, event);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.ERROR) {
                appendWrappedText(lines, ERROR_LABEL, firstNonBlank(payloadString(event.getPayload(), "error"), event.getSummary()),
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.SESSION_RESUMED) {
                appendWrappedText(lines, NOTE_LABEL, firstNonBlank(event.getSummary(), "session resumed"),
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.SESSION_FORKED) {
                appendWrappedText(lines, NOTE_LABEL, firstNonBlank(event.getSummary(), "session forked"),
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.TASK_CREATED || type == SessionEventType.TASK_UPDATED) {
                appendTaskEventLines(lines, event);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.TEAM_MESSAGE) {
                appendTeamMessageLines(lines, event);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.AUTO_CONTINUE
                    || type == SessionEventType.AUTO_STOP
                    || type == SessionEventType.BLOCKED) {
                appendWrappedText(lines, NOTE_LABEL, firstNonBlank(event.getSummary(), type.name().toLowerCase().replace('_', ' ')),
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                appendBlankLineIfNeeded(lines);
                continue;
            }
            if (type == SessionEventType.COMPACT) {
                appendWrappedText(lines, NOTE_LABEL,
                        firstNonBlank(event.getSummary(), payloadString(event.getPayload(), "summary"), "context compacted"),
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                appendBlankLineIfNeeded(lines);
            }
        }
    }

    private void appendLiveAssistantFeed(List<String> lines) {
        TuiAssistantViewModel viewModel = assistantViewModel;
        if (viewModel == null) {
            return;
        }

        String statusLine = buildAssistantStatusLine(viewModel);
        if (!isBlank(statusLine)) {
            lines.add(statusLine);
        }

        appendToolLines(lines, filterUnpersistedLiveTools(viewModel.getTools()));

        String reasoningText = trimToNull(viewModel.getReasoningText());
        if (!isBlank(reasoningText)) {
            appendBlankLineIfNeeded(lines);
            appendReasoningMarkdown(lines, reasoningText, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        String assistantText = trimToNull(viewModel.getText());
        if (!isBlank(assistantText)
                && !(viewModel.getPhase() == TuiAssistantPhase.COMPLETE && assistantText.equals(lastAssistantMessage()))) {
            appendBlankLineIfNeeded(lines);
            appendAssistantMarkdown(lines, assistantText, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    private void appendTaskEventLines(List<String> lines, SessionEvent event) {
        if (lines == null || event == null) {
            return;
        }
        Map<String, Object> payload = event.getPayload();
        appendWrappedText(lines, NOTE_LABEL,
                firstNonBlank(event.getSummary(), payloadString(payload, "title"), "delegate task"),
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        String detail = firstNonBlank(payloadString(payload, "detail"), payloadString(payload, "error"), payloadString(payload, "output"));
        if (!isBlank(detail)) {
            appendWrappedText(lines, BULLET_CONTINUATION, detail, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String member = firstNonBlank(payloadString(payload, "memberName"), payloadString(payload, "memberId"));
        if (!isBlank(member)) {
            appendWrappedText(lines, BULLET_CONTINUATION, "member: " + member, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String childSessionId = payloadString(payload, "childSessionId");
        if (!isBlank(childSessionId)) {
            appendWrappedText(lines, BULLET_CONTINUATION, "child session: " + childSessionId, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String status = payloadString(payload, "status");
        String phase = payloadString(payload, "phase");
        String percent = payloadString(payload, "percent");
        if (!isBlank(status) || !isBlank(phase) || !isBlank(percent)) {
            StringBuilder stateLine = new StringBuilder();
            if (!isBlank(status)) {
                stateLine.append("status: ").append(status);
            }
            if (!isBlank(phase)) {
                if (stateLine.length() > 0) {
                    stateLine.append(" | ");
                }
                stateLine.append("phase: ").append(phase);
            }
            if (!isBlank(percent)) {
                if (stateLine.length() > 0) {
                    stateLine.append(" | ");
                }
                stateLine.append("progress: ").append(percent).append('%');
            }
            appendWrappedText(lines, BULLET_CONTINUATION, stateLine.toString(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String heartbeatCount = payloadString(payload, "heartbeatCount");
        if (!isBlank(heartbeatCount) && !"0".equals(heartbeatCount)) {
            appendWrappedText(lines, BULLET_CONTINUATION, "heartbeats: " + heartbeatCount, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    private void appendTeamMessageLines(List<String> lines, SessionEvent event) {
        if (lines == null || event == null) {
            return;
        }
        Map<String, Object> payload = event.getPayload();
        appendWrappedText(lines, NOTE_LABEL,
                firstNonBlank(event.getSummary(), payloadString(payload, "title"), "team message"),
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        String taskId = payloadString(payload, "taskId");
        if (!isBlank(taskId)) {
            appendWrappedText(lines, BULLET_CONTINUATION, "task: " + taskId, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String detail = firstNonBlank(payloadString(payload, "content"), payloadString(payload, "detail"));
        if (!isBlank(detail)) {
            appendWrappedText(lines, BULLET_CONTINUATION, detail, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    private void appendAssistantNote(List<String> lines) {
        String note = trimToNull(assistantOutput);
        if (isBlank(note)) {
            return;
        }
        String assistantText = assistantViewModel == null ? null : trimToNull(assistantViewModel.getText());
        if (note.equals(assistantText)) {
            return;
        }
        appendBlankLineIfNeeded(lines);
        appendWrappedText(lines, NOTE_LABEL, note, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private int resolveTranscriptViewport(TuiRenderContext context, String overlay, String composerAddon) {
        int terminalRows = context == null ? 0 : context.getTerminalRows();
        if (terminalRows <= 0) {
            return Math.max(8, TRANSCRIPT_VIEWPORT_LINES);
        }
        int reservedLines = 1; // header
        reservedLines += 2; // gap before transcript
        reservedLines += 2; // gap before composer
        reservedLines += 1; // composer
        if (!isBlank(overlay)) {
            reservedLines += 2 + countRenderedLines(overlay);
        }
        if (!isBlank(composerAddon)) {
            reservedLines += 2 + countRenderedLines(composerAddon);
        }
        return Math.max(4, terminalRows - reservedLines);
    }

    private List<String> sliceTranscriptLines(List<String> transcriptLines, int viewportHint) {
        if (transcriptLines == null || transcriptLines.isEmpty()) {
            return Collections.emptyList();
        }
        int viewport = viewportHint > 0 ? viewportHint : Math.max(8, TRANSCRIPT_VIEWPORT_LINES);
        int offset = currentInteractionState == null ? 0 : Math.max(0, currentInteractionState.getTranscriptScrollOffset());
        if (transcriptLines.size() <= viewport) {
            return new ArrayList<String>(transcriptLines);
        }
        int maxOffset = Math.max(0, transcriptLines.size() - viewport);
        int clampedOffset = Math.min(offset, maxOffset);
        int to = transcriptLines.size() - clampedOffset;
        int from = Math.max(0, to - viewport);
        return new ArrayList<String>(transcriptLines.subList(from, to));
    }

    private int countRenderedLines(String value) {
        if (isBlank(value)) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private void trimTrailingBlankLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        while (!lines.isEmpty() && isBlank(lines.get(lines.size() - 1))) {
            lines.remove(lines.size() - 1);
        }
    }

    private List<TuiAssistantToolView> filterUnpersistedLiveTools(List<TuiAssistantToolView> tools) {
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> persistedKeys = buildPersistedToolKeys();
        if (persistedKeys.isEmpty()) {
            return new ArrayList<TuiAssistantToolView>(tools);
        }
        List<TuiAssistantToolView> visibleTools = new ArrayList<TuiAssistantToolView>();
        for (TuiAssistantToolView tool : tools) {
            if (tool == null) {
                continue;
            }
            if (!persistedKeys.contains(buildToolIdentity(tool))) {
                visibleTools.add(tool);
            }
        }
        return visibleTools;
    }

    private Set<String> buildPersistedToolKeys() {
        if (cachedEvents == null || cachedEvents.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> keys = new HashSet<String>();
        for (SessionEvent event : cachedEvents) {
            if (event == null || event.getType() == null) {
                continue;
            }
            if (event.getType() != SessionEventType.TOOL_CALL && event.getType() != SessionEventType.TOOL_RESULT) {
                continue;
            }
            String key = buildToolIdentity(event.getPayload());
            if (!isBlank(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private Set<String> buildCompletedToolKeys(List<SessionEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> keys = new HashSet<String>();
        for (SessionEvent event : events) {
            if (event == null || event.getType() != SessionEventType.TOOL_RESULT) {
                continue;
            }
            String key = buildToolIdentity(event.getPayload());
            if (!isBlank(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String buildToolIdentity(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String callId = trimToNull(payloadString(payload, "callId"));
        if (!isBlank(callId)) {
            return callId;
        }
        String toolName = payloadString(payload, "tool");
        JSONObject arguments = parseObject(payloadString(payload, "arguments"));
        return firstNonBlank(toolName, "tool") + "|"
                + firstNonBlank(trimToNull(payloadString(payload, "title")), buildToolTitle(toolName, arguments));
    }

    private String buildToolIdentity(TuiAssistantToolView tool) {
        if (tool == null) {
            return null;
        }
        String callId = trimToNull(tool.getCallId());
        if (!isBlank(callId)) {
            return callId;
        }
        return firstNonBlank(tool.getToolName(), "tool") + "|"
                + firstNonBlank(trimToNull(tool.getTitle()), extractToolLabel(tool), "tool");
    }

    private void appendAssistantMarkdown(List<String> lines, String rawText, int maxLines, int maxChars) {
        appendMarkdownBlock(lines, rawText, ASSISTANT_PREFIX, ASSISTANT_CONTINUATION_PREFIX, maxLines, maxChars);
    }

    private void appendReasoningMarkdown(List<String> lines, String rawText, int maxLines, int maxChars) {
        if (lines == null || isBlank(rawText) || maxLines <= 0) {
            return;
        }
        String[] rawLines = rawText.replace("\r", "").split("\n");
        boolean inCodeBlock = false;
        int count = 0;
        for (String rawLine : rawLines) {
            if (count >= maxLines) {
                lines.add(REASONING_CONTINUATION_PREFIX + (inCodeBlock ? CODE_LINE_PREFIX + "..." : "..."));
                return;
            }
            String trimmed = rawLine == null ? "" : rawLine.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }

            String renderedLine = inCodeBlock
                    ? CODE_LINE_PREFIX + clipCodeLine(rawLine, Math.max(0, maxChars))
                    : clip(rawLine, maxChars);
            lines.add((count == 0 ? REASONING_PREFIX : REASONING_CONTINUATION_PREFIX) + renderedLine);
            count++;
        }
    }

    private void appendMarkdownBlock(List<String> lines,
                                     String rawText,
                                     String firstPrefix,
                                     String continuationPrefix,
                                     int maxLines,
                                     int maxChars) {
        if (lines == null || isBlank(rawText) || maxLines <= 0) {
            return;
        }
        String[] rawLines = rawText.replace("\r", "").split("\n");
        boolean inCodeBlock = false;
        int count = 0;
        for (String rawLine : rawLines) {
            if (count >= maxLines) {
                lines.add(continuationPrefix + (inCodeBlock ? CODE_LINE_PREFIX + "..." : "..."));
                return;
            }
            String trimmed = rawLine == null ? "" : rawLine.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }

            String renderedLine = inCodeBlock
                    ? CODE_LINE_PREFIX + clipCodeLine(rawLine, Math.max(0, maxChars))
                    : clip(rawLine, maxChars);
            lines.add((count == 0 ? firstPrefix : continuationPrefix) + renderedLine);
            count++;
        }
    }

    private void appendStartupTips(List<String> lines) {
        String tips = trimToNull(assistantOutput);
        if (isBlank(tips)) {
            lines.add(STARTUP_LABEL + "Ask AI4J to inspect this repository");
            lines.add(STARTUP_LABEL + "Type `/` for commands and prompt templates");
            lines.add(STARTUP_LABEL + "Use `Ctrl+R` for replay, `/team` for team board, and `Tab` to accept slash-command completion");
            appendRecentSessionHints(lines);
            return;
        }
        appendWrappedText(lines, STARTUP_LABEL, tips, Integer.MAX_VALUE, 108);
    }

    private String buildAssistantStatusLine(TuiAssistantViewModel viewModel) {
        if (viewModel == null || viewModel.getPhase() == null || viewModel.getPhase() == TuiAssistantPhase.IDLE) {
            return null;
        }
        if (viewModel.getPhase() == TuiAssistantPhase.COMPLETE && !isBlank(viewModel.getText())) {
            return null;
        }
        String phrase = normalizeStatusPhrase(viewModel.getPhaseDetail(), viewModel.getPhase());
        if (isBlank(phrase)) {
            return null;
        }
        return statusPrefix(viewModel) + " " + phrase;
    }

    private void appendToolLines(List<String> lines, List<TuiAssistantToolView> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        int from = Math.max(0, tools.size() - 4);
        for (int i = from; i < tools.size(); i++) {
            TuiAssistantToolView tool = tools.get(i);
            if (tool == null) {
                continue;
            }
            appendBlankLineIfNeeded(lines);
            List<String> primaryLines = formatToolPrimaryLines(tool);
            for (String primaryLine : primaryLines) {
                if (!isBlank(primaryLine)) {
                    lines.add(primaryLine);
                }
            }
            String detail = formatToolDetail(tool);
            if (!isBlank(detail)) {
                lines.addAll(wrapPrefixedText("  └ ", "    ", detail, 108));
            }
            List<String> previewLines = formatToolPreviewLines(tool);
            for (String previewLine : previewLines) {
                lines.add(previewLine);
            }
        }
    }

    private void appendToolEventLines(List<String> lines, SessionEvent event) {
        if (lines == null || event == null || event.getType() == null) {
            return;
        }
        TuiAssistantToolView toolView = buildToolView(event);
        if (toolView == null) {
            return;
        }
        appendToolLines(lines, Collections.singletonList(toolView));
    }

    private TuiAssistantToolView buildToolView(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return null;
        }
        Map<String, Object> payload = event.getPayload();
        String toolName = payloadString(payload, "tool");
        if (isBlank(toolName)) {
            return null;
        }
        JSONObject arguments = parseObject(payloadString(payload, "arguments"));
        String title = firstNonBlank(trimToNull(payloadString(payload, "title")), buildToolTitle(toolName, arguments));
        List<String> previewLines = payloadLines(payload, "previewLines");
        if (event.getType() == SessionEventType.TOOL_CALL) {
            if (previewLines.isEmpty()) {
                previewLines = buildPendingToolPreviewLines(toolName, arguments);
            }
            return TuiAssistantToolView.builder()
                    .callId(payloadString(payload, "callId"))
                    .toolName(toolName)
                    .status("pending")
                    .title(title)
                    .detail(firstNonBlank(trimToNull(payloadString(payload, "detail")), buildPendingToolDetail(toolName, arguments)))
                    .previewLines(previewLines)
                    .build();
        }

        String rawOutput = payloadString(payload, "output");
        JSONObject output = parseObject(rawOutput);
        if (previewLines.isEmpty()) {
            previewLines = buildToolPreviewLines(toolName, arguments, output, rawOutput);
        }
        return TuiAssistantToolView.builder()
                .callId(payloadString(payload, "callId"))
                .toolName(toolName)
                .status(isToolError(output, rawOutput) ? "error" : "done")
                .title(title)
                .detail(firstNonBlank(trimToNull(payloadString(payload, "detail")),
                        buildCompletedToolDetail(toolName, arguments, output, rawOutput)))
                .previewLines(previewLines)
                .build();
    }

    private List<String> formatToolPrimaryLines(TuiAssistantToolView tool) {
        List<String> lines = new ArrayList<String>();
        String toolName = firstNonBlank(tool.getToolName(), "tool");
        String status = firstNonBlank(tool.getStatus(), "pending").toLowerCase(Locale.ROOT);
        String title = firstNonBlank(trimToNull(tool.getTitle()), toolName);
        String label = normalizeToolPrimaryLabel(title);
        if ("error".equals(status)) {
            if ("bash".equals(toolName)) {
                return wrapPrefixedText("\u2022 Command failed ", "  \u2502 ", label, 108);
            }
            lines.add("\u2022 Tool failed " + clip(label, 92));
            return lines;
        }
        if ("apply_patch".equals(toolName)) {
            lines.add("pending".equals(status) ? "\u2022 Applying patch" : "\u2022 Applied patch");
            return lines;
        }
        return wrapPrefixedText(resolveToolPrimaryPrefix(toolName, title, status), "  \u2502 ", label, 108);
    }

    private String resolveToolPrimaryPrefix(String toolName, String title, String status) {
        String normalizedTool = firstNonBlank(toolName, "tool");
        String normalizedTitle = firstNonBlank(title, normalizedTool);
        boolean pending = "pending".equalsIgnoreCase(status);
        if ("read_file".equals(normalizedTool)) {
            return pending ? "\u2022 Reading " : "\u2022 Read ";
        }
        if ("bash".equals(normalizedTool)) {
            if (normalizedTitle.startsWith("bash logs ")) {
                return pending ? "\u2022 Reading logs " : "\u2022 Read logs ";
            }
            if (normalizedTitle.startsWith("bash status ")) {
                return pending ? "\u2022 Checking " : "\u2022 Checked ";
            }
            if (normalizedTitle.startsWith("bash write ")) {
                return pending ? "\u2022 Writing to " : "\u2022 Wrote to ";
            }
            if (normalizedTitle.startsWith("bash stop ")) {
                return pending ? "\u2022 Stopping " : "\u2022 Stopped ";
            }
        }
        return pending ? "\u2022 Running " : "\u2022 Ran ";
    }

    private String normalizeToolPrimaryLabel(String title) {
        String normalizedTitle = firstNonBlank(title, "tool").trim();
        if (normalizedTitle.startsWith("$ ")) {
            return normalizedTitle.substring(2).trim();
        }
        if (normalizedTitle.startsWith("read ")) {
            return normalizedTitle.substring(5).trim();
        }
        if (normalizedTitle.startsWith("bash logs ")) {
            return normalizedTitle.substring("bash logs ".length()).trim();
        }
        if (normalizedTitle.startsWith("bash status ")) {
            return normalizedTitle.substring("bash status ".length()).trim();
        }
        if (normalizedTitle.startsWith("bash write ")) {
            return normalizedTitle.substring("bash write ".length()).trim();
        }
        if (normalizedTitle.startsWith("bash stop ")) {
            return normalizedTitle.substring("bash stop ".length()).trim();
        }
        return normalizedTitle;
    }

    private String formatToolDetail(TuiAssistantToolView tool) {
        if (tool == null || isBlank(tool.getDetail())) {
            return null;
        }
        String detail = tool.getDetail().trim();
        if ("bash".equals(firstNonBlank(tool.getToolName(), "tool"))
                && !"error".equalsIgnoreCase(firstNonBlank(tool.getStatus(), ""))
                && !"timed out".equalsIgnoreCase(detail)) {
            return null;
        }
        String normalized = detail.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("running command")
                || normalized.startsWith("reading process logs")
                || normalized.startsWith("checking process status")
                || normalized.startsWith("writing to process")
                || normalized.startsWith("stopping process")
                || normalized.startsWith("running tool")
                || normalized.startsWith("fetching buffered logs")
                || normalized.startsWith("refreshing process metadata")
                || normalized.startsWith("writing to process stdin")
                || normalized.startsWith("executing bash tool")
                || normalized.startsWith("reading file content")
                || normalized.startsWith("applying workspace patch")
                || normalized.startsWith("waiting for tool result")) {
            return null;
        }
        return detail;
    }

    private List<String> formatToolPreviewLines(TuiAssistantToolView tool) {
        List<String> rawPreviewLines = tool == null || tool.getPreviewLines() == null
                ? Collections.<String>emptyList()
                : tool.getPreviewLines();
        List<String> normalizedPreviewLines = normalizeToolPreviewLines(tool, rawPreviewLines);
        if (normalizedPreviewLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> formatted = new ArrayList<String>();
        int max = Math.min(normalizedPreviewLines.size(), MAX_TOOL_PREVIEW_LINES);
        for (int i = 0; i < max; i++) {
            String prefix = i == 0 ? "  \u2514 " : "    ";
            formatted.addAll(wrapPrefixedText(prefix, "    ", normalizedPreviewLines.get(i), 108));
        }
        if (normalizedPreviewLines.size() > max) {
            formatted.add("    \u2026 +" + (normalizedPreviewLines.size() - max) + " lines");
        }
        return formatted;
    }

    private List<String> normalizeToolPreviewLines(TuiAssistantToolView tool, List<String> previewLines) {
        if (previewLines == null || previewLines.isEmpty()) {
            return Collections.emptyList();
        }
        String toolName = firstNonBlank(tool == null ? null : tool.getToolName(), "tool");
        String status = firstNonBlank(tool == null ? null : tool.getStatus(), "pending").toLowerCase(Locale.ROOT);
        List<String> normalized = new ArrayList<String>();
        for (String previewLine : previewLines) {
            String candidate = trimToNull(stripPreviewLabel(previewLine));
            if (isBlank(candidate)) {
                continue;
            }
            if ("bash".equals(toolName)) {
                if ("pending".equals(status)) {
                    continue;
                }
                if ("(no command output)".equalsIgnoreCase(candidate)) {
                    continue;
                }
            }
            if ("apply_patch".equals(toolName) && "(no changed files)".equalsIgnoreCase(candidate)) {
                continue;
            }
            normalized.add(candidate);
        }
        return normalized;
    }

    private String stripPreviewLabel(String previewLine) {
        String value = trimToNull(previewLine);
        if (isBlank(value)) {
            return value;
        }
        int separator = value.indexOf("> ");
        if (separator > 0) {
            String prefix = value.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if ("stdout".equals(prefix)
                    || "stderr".equals(prefix)
                    || "log".equals(prefix)
                    || "file".equals(prefix)
                    || "path".equals(prefix)
                    || "cwd".equals(prefix)
                    || "timeout".equals(prefix)
                    || "process".equals(prefix)
                    || "status".equals(prefix)
                    || "command".equals(prefix)
                    || "stdin".equals(prefix)
                    || "meta".equals(prefix)
                    || "out".equals(prefix)) {
                return value.substring(separator + 2).trim();
            }
        }
        return value;
    }

    private String extractToolLabel(TuiAssistantToolView tool) {
        if (tool == null) {
            return "tool";
        }
        String title = trimToNull(tool.getTitle());
        if (!isBlank(title)) {
            return normalizeToolPrimaryLabel(title);
        }
        return firstNonBlank(tool.getToolName(), "tool");
    }

    private JSONObject parseObject(String rawJson) {
        if (isBlank(rawJson)) {
            return null;
        }
        try {
            return JSON.parseObject(rawJson);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isToolError(JSONObject output, String rawOutput) {
        return !isBlank(extractToolError(output, rawOutput));
    }

    private String extractToolError(JSONObject output, String rawOutput) {
        if (!isBlank(rawOutput) && rawOutput.startsWith("TOOL_ERROR:")) {
            JSONObject errorPayload = parseObject(rawOutput.substring("TOOL_ERROR:".length()).trim());
            if (errorPayload != null && !isBlank(errorPayload.getString("error"))) {
                return errorPayload.getString("error");
            }
            return rawOutput.substring("TOOL_ERROR:".length()).trim();
        }
        if (!isBlank(rawOutput) && rawOutput.startsWith("CODE_ERROR:")) {
            return rawOutput.substring("CODE_ERROR:".length()).trim();
        }
        return output == null ? null : trimToNull(output.getString("error"));
    }

    private String buildToolTitle(String toolName, JSONObject arguments) {
        if ("bash".equals(toolName)) {
            String action = firstNonBlank(arguments == null ? null : arguments.getString("action"), "exec");
            if ("exec".equals(action) || "start".equals(action)) {
                String command = firstNonBlank(arguments == null ? null : arguments.getString("command"), null);
                return isBlank(command) ? "bash " + action : "$ " + command;
            }
            if ("write".equals(action)) {
                return "bash write " + firstNonBlank(arguments == null ? null : arguments.getString("processId"), "(process)");
            }
            if ("logs".equals(action) || "status".equals(action) || "stop".equals(action)) {
                return "bash " + action + " " + firstNonBlank(arguments == null ? null : arguments.getString("processId"), "(process)");
            }
            return "bash " + action;
        }
        if ("read_file".equals(toolName)) {
            String path = arguments == null ? null : arguments.getString("path");
            Integer startLine = arguments == null ? null : arguments.getInteger("startLine");
            Integer endLine = arguments == null ? null : arguments.getInteger("endLine");
            String range = startLine == null ? "" : ":" + startLine + (endLine == null ? "" : "-" + endLine);
            return "read " + firstNonBlank(path, "(path)") + range;
        }
        if ("apply_patch".equals(toolName)) {
            return "apply_patch";
        }
        return firstNonBlank(toolName, "tool");
    }

    private String buildPendingToolDetail(String toolName, JSONObject arguments) {
        if ("bash".equals(toolName)) {
            String action = firstNonBlank(arguments == null ? null : arguments.getString("action"), "exec");
            if ("exec".equals(action) || "start".equals(action)) {
                return "Running command...";
            }
            if ("logs".equals(action)) {
                return "Reading process logs...";
            }
            if ("status".equals(action)) {
                return "Checking process status...";
            }
            if ("write".equals(action)) {
                return "Writing to process...";
            }
            if ("stop".equals(action)) {
                return "Stopping process...";
            }
            return "Running tool...";
        }
        if ("read_file".equals(toolName)) {
            return "Reading file content...";
        }
        if ("apply_patch".equals(toolName)) {
            return "Applying workspace patch...";
        }
        return "Running tool...";
    }

    private List<String> buildPendingToolPreviewLines(String toolName, JSONObject arguments) {
        return new ArrayList<String>();
    }

    private String buildCompletedToolDetail(String toolName,
                                            JSONObject arguments,
                                            JSONObject output,
                                            String rawOutput) {
        String toolError = extractToolError(output, rawOutput);
        if (!isBlank(toolError)) {
            return clip(toolError, 96);
        }
        if ("bash".equals(toolName)) {
            String action = firstNonBlank(arguments == null ? null : arguments.getString("action"), "exec");
            if ("exec".equals(action) && output != null) {
                if (output.getBooleanValue("timedOut")) {
                    return "timed out";
                }
                return null;
            }
            if (("start".equals(action) || "status".equals(action) || "stop".equals(action)) && output != null) {
                String processId = firstNonBlank(output.getString("processId"), "process");
                String status = trimToNull(output.getString("status"));
                return isBlank(status) ? processId : processId + " | " + status.toLowerCase(Locale.ROOT);
            }
            if ("write".equals(action) && output != null) {
                return output.getIntValue("bytesWritten") + " bytes written";
            }
            if ("logs".equals(action) && output != null) {
                return null;
            }
            return clip(rawOutput, 96);
        }
        if ("read_file".equals(toolName) && output != null) {
            return firstNonBlank(output.getString("path"), firstNonBlank(arguments == null ? null : arguments.getString("path"), "(path)"))
                    + ":" + output.getIntValue("startLine") + "-" + output.getIntValue("endLine")
                    + (output.getBooleanValue("truncated") ? " | truncated" : "");
        }
        if ("apply_patch".equals(toolName) && output != null) {
            int filesChanged = output.getIntValue("filesChanged");
            if (filesChanged <= 0) {
                return null;
            }
            return filesChanged == 1 ? "1 file changed" : filesChanged + " files changed";
        }
        return clip(rawOutput, 96);
    }

    private List<String> buildToolPreviewLines(String toolName,
                                               JSONObject arguments,
                                               JSONObject output,
                                               String rawOutput) {
        List<String> previewLines = new ArrayList<String>();
        if (!isBlank(extractToolError(output, rawOutput))) {
            return previewLines;
        }
        if ("bash".equals(toolName)) {
            String action = firstNonBlank(arguments == null ? null : arguments.getString("action"), "exec");
            if ("exec".equals(action) && output != null) {
                addCommandPreviewLines(previewLines, output.getString("stdout"), output.getString("stderr"));
                return previewLines;
            }
            if ("logs".equals(action) && output != null) {
                addPlainPreviewLines(previewLines, output.getString("content"));
                return previewLines;
            }
            if (("start".equals(action) || "status".equals(action) || "stop".equals(action)) && output != null) {
                addPreviewLine(previewLines, "command", output.getString("command"));
                return previewLines;
            }
            if ("write".equals(action) && output != null) {
                return previewLines;
            }
            return previewLines;
        }
        if ("read_file".equals(toolName) && output != null) {
            addPreviewLines(previewLines, "file", output.getString("content"), 4);
            if (previewLines.isEmpty()) {
                previewLines.add("file> (empty file)");
            }
            return previewLines;
        }
        if ("apply_patch".equals(toolName) && output != null) {
            JSONArray changedFiles = output.getJSONArray("changedFiles");
            if (changedFiles != null) {
                for (int i = 0; i < changedFiles.size() && i < 4; i++) {
                    previewLines.add("file> " + String.valueOf(changedFiles.get(i)));
                }
            }
            return previewLines;
        }
        addPreviewLines(previewLines, "out", rawOutput, 4);
        return previewLines;
    }

    private void addPreviewLines(List<String> target, String label, String raw, int maxLines) {
        if (target == null || isBlank(raw) || maxLines <= 0) {
            return;
        }
        String[] lines = raw.replace("\r", "").split("\n");
        int count = 0;
        for (String line : lines) {
            if (isBlank(line)) {
                continue;
            }
            target.add(firstNonBlank(label, "out") + "> " + clip(line, 92));
            count++;
            if (count >= maxLines) {
                break;
            }
        }
    }

    private void addCommandPreviewLines(List<String> target, String stdout, String stderr) {
        if (target == null) {
            return;
        }
        List<String> lines = collectNonBlankLines(stdout);
        if (lines.isEmpty()) {
            lines = collectNonBlankLines(stderr);
        } else {
            List<String> stderrLines = collectNonBlankLines(stderr);
            for (String stderrLine : stderrLines) {
                lines.add("stderr: " + stderrLine);
            }
        }
        addSummarizedPreview(target, lines);
    }

    private void addPlainPreviewLines(List<String> target, String raw) {
        if (target == null) {
            return;
        }
        addSummarizedPreview(target, collectNonBlankLines(raw));
    }

    private void addSummarizedPreview(List<String> target, List<String> lines) {
        if (target == null || lines == null || lines.isEmpty()) {
            return;
        }
        if (lines.size() <= 3) {
            for (String line : lines) {
                target.add(clip(line, 92));
            }
            return;
        }
        target.add(clip(lines.get(0), 92));
        target.add("\u2026 +" + (lines.size() - 2) + " lines");
        target.add(clip(lines.get(lines.size() - 1), 92));
    }

    private List<String> collectNonBlankLines(String raw) {
        if (isBlank(raw)) {
            return Collections.emptyList();
        }
        String[] rawLines = raw.replace("\r", "").split("\n");
        List<String> lines = new ArrayList<String>();
        for (String rawLine : rawLines) {
            if (!isBlank(rawLine)) {
                lines.add(rawLine.trim());
            }
        }
        return lines;
    }

    private void addPreviewLine(List<String> target, String label, String value) {
        if (target == null || isBlank(value)) {
            return;
        }
        target.add(firstNonBlank(label, "meta") + "> " + clip(value, 92));
    }

    private String renderOverlay(TuiInteractionState state) {
        if (hasPendingApproval(state)) {
            return renderModal("Approve command", buildApprovalLines(state));
        }
        if (state != null && state.isProcessInspectorOpen()) {
            return renderModal(buildProcessOverlayTitle(state), buildProcessInspectorLines(state));
        }
        if (state != null && state.isReplayViewerOpen()) {
            return renderModal("History", buildReplayViewerLines(state));
        }
        if (state != null && state.isTeamBoardOpen()) {
            return renderModal("Team Board", buildTeamBoardViewerLines(state));
        }
        if (state != null && state.isPaletteOpen() && state.getPaletteMode() != TuiInteractionState.PaletteMode.SLASH) {
            return renderModal("Commands", buildPaletteLines(state));
        }
        return null;
    }

    private String renderComposerAddon(TuiInteractionState state) {
        if (state != null && state.isPaletteOpen() && state.getPaletteMode() == TuiInteractionState.PaletteMode.SLASH) {
            return renderInlinePalette(buildPaletteLines(state));
        }
        return null;
    }

    private String renderModal(String title, List<String> lines) {
        StringBuilder out = new StringBuilder();
        out.append(TuiAnsi.bold(TuiAnsi.fg(BULLET_PREFIX + firstNonBlank(title, "dialog"), theme.getMuted(), ansi), ansi));
        if (lines == null || lines.isEmpty()) {
            out.append('\n').append(TuiAnsi.fg("  (none)", theme.getMuted(), ansi));
        } else {
            for (String line : lines) {
                out.append('\n').append(renderModalLine(line));
            }
        }
        return out.toString();
    }

    private String renderInlinePalette(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return TuiAnsi.fg("  (no matches)", theme.getMuted(), ansi);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(styleLine(lines.get(i)));
        }
        return out.toString();
    }

    private List<String> buildApprovalLines(TuiInteractionState state) {
        TuiInteractionState.ApprovalSnapshot snapshot = state == null ? null : state.getApprovalSnapshot();
        if (snapshot == null) {
            return Collections.singletonList("(none)");
        }
        List<String> lines = new ArrayList<String>();
        String command = extractApprovalCommand(snapshot.getSummary());
        if (!isBlank(command)) {
            lines.add("`" + clip(command, 108) + "`");
        } else if (!isBlank(snapshot.getSummary())) {
            lines.add(clip(snapshot.getSummary(), 108));
        }
        lines.add("Y approve  N reject  Esc close");
        return lines;
    }

    private String buildProcessOverlayTitle(TuiInteractionState state) {
        String processId = firstNonBlank(state == null ? null : state.getSelectedProcessId(), inspectedProcess == null ? null : inspectedProcess.getProcessId(), "process");
        return "Process " + clip(processId, 32);
    }

    private List<String> buildProcessInspectorLines(TuiInteractionState state) {
        if (inspectedProcess == null) {
            return Collections.singletonList("(no process selected)");
        }
        List<String> lines = new ArrayList<String>();
        lines.add("status " + firstNonBlank(inspectedProcess.getStatus() == null ? null : inspectedProcess.getStatus().name(), "unknown").toLowerCase(Locale.ROOT));
        if (!isBlank(inspectedProcess.getWorkingDirectory())) {
            lines.add("cwd " + clip(inspectedProcess.getWorkingDirectory(), 104));
        }
        if (!isBlank(inspectedProcess.getCommand())) {
            lines.add("cmd> " + clip(inspectedProcess.getCommand(), 103));
        }
        if (inspectedProcessLogs != null && !isBlank(inspectedProcessLogs.getContent())) {
            appendMultiline(lines, inspectedProcessLogs.getContent(), MAX_PROCESS_LOG_LINES, 108);
        }
        if (state != null && inspectedProcess.isControlAvailable()) {
            lines.add("stdin> " + clip(state.getProcessInputBuffer(), 101));
        }
        return lines;
    }

    private List<String> buildReplayViewerLines(TuiInteractionState state) {
        if (cachedReplay == null || cachedReplay.isEmpty()) {
            return Collections.singletonList("(none)");
        }
        int offset = state == null ? 0 : Math.max(0, state.getReplayScrollOffset());
        int from = Math.min(offset, Math.max(0, cachedReplay.size() - 1));
        int to = Math.min(cachedReplay.size(), from + MAX_REPLAY_LINES);
        List<String> lines = new ArrayList<String>();
        if (from > 0) {
            lines.add("...");
        }
        for (int i = from; i < to; i++) {
            lines.add(clip(normalizeLegacyTranscriptLine(cachedReplay.get(i)), 108));
        }
        if (to < cachedReplay.size()) {
            lines.add("...");
        }
        lines.add("\u2191/\u2193 scroll  Esc close");
        return lines;
    }

    private List<String> buildTeamBoardViewerLines(TuiInteractionState state) {
        if (cachedTeamBoard == null || cachedTeamBoard.isEmpty()) {
            return Collections.singletonList("(none)");
        }
        int offset = state == null ? 0 : Math.max(0, state.getTeamBoardScrollOffset());
        int from = Math.min(offset, Math.max(0, cachedTeamBoard.size() - 1));
        int to = Math.min(cachedTeamBoard.size(), from + MAX_REPLAY_LINES);
        List<String> lines = new ArrayList<String>();
        if (from > 0) {
            lines.add("...");
        }
        for (int i = from; i < to; i++) {
            lines.add(clip(cachedTeamBoard.get(i), 108));
        }
        if (to < cachedTeamBoard.size()) {
            lines.add("...");
        }
        lines.add("\u2191/\u2193 scroll  Esc close");
        return lines;
    }

    private List<String> buildPaletteLines(TuiInteractionState state) {
        List<TuiPaletteItem> items = state == null ? Collections.<TuiPaletteItem>emptyList() : state.getPaletteItems();
        if (items == null || items.isEmpty()) {
            return Collections.singletonList("(no matches)");
        }
        List<String> lines = new ArrayList<String>();
        boolean slashMode = state != null && state.getPaletteMode() == TuiInteractionState.PaletteMode.SLASH;
        int selectedIndex = state == null ? 0 : Math.max(0, Math.min(state.getPaletteSelectedIndex(), items.size() - 1));
        int windowSize = Math.max(1, Math.min(items.size(), MAX_OVERLAY_ITEMS));
        int from = Math.max(0, Math.min(selectedIndex - (windowSize / 2), items.size() - windowSize));
        int to = Math.min(items.size(), from + windowSize);
        if (from > 0) {
            lines.add("...");
        }
        for (int i = from; i < to; i++) {
            TuiPaletteItem item = items.get(i);
            String prefix = i == selectedIndex ? "> " : "  ";
            String label = slashMode
                    ? firstNonBlank(item.getCommand(), item.getLabel())
                    : firstNonBlank(item.getCommand(), item.getLabel());
            String detail = clip(firstNonBlank(item.getDetail(), ""), 72);
            String value = prefix + clip(label, 32);
            if (!isBlank(detail)) {
                value = clip(value + "  " + detail, 108);
            }
            lines.add(value);
        }
        if (to < items.size()) {
            lines.add("...");
        }
        return lines;
    }

    private String renderComposer(TuiInteractionState state) {
        String prompt = state != null && state.isProcessInspectorOpen() ? "stdin> " : "> ";
        String buffer = resolveActiveInputBuffer(state);
        if (isBlank(buffer) && (state == null || !state.isProcessInspectorOpen())) {
            StringBuilder line = new StringBuilder();
            line.append(TuiAnsi.bold(TuiAnsi.fg(prompt, theme.getText(), ansi), ansi));
            line.append(TuiAnsi.fg("Type a request or `/` for commands", theme.getMuted(), ansi));
            return line.toString();
        }
        return TuiAnsi.bold(TuiAnsi.fg(clip(prompt + defaultText(buffer, ""), 120), theme.getText(), ansi), ansi);
    }

    private String renderModalLine(String line) {
        String normalized = normalizeLegacyTranscriptLine(line);
        if (isBlank(normalized)) {
            return "";
        }
        if (normalized.startsWith("> ")) {
            return styleLine(normalized);
        }
        if ("...".equals(normalized) || normalized.startsWith("\u2191/\u2193 ")) {
            return TuiAnsi.fg("  " + normalized, theme.getMuted(), ansi);
        }
        return styleLine("  " + normalized);
    }

    private void appendLines(StringBuilder out, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(styleLine(lines.get(i)));
        }
    }

    private String styleLine(String line) {
        if (isBlank(line)) {
            return "";
        }
        String normalizedLine = stripStatusPrefix(line);
        String reasoningBody = extractReasoningBody(line);
        if (reasoningBody != null) {
            return renderReasoningLine(line, reasoningBody);
        }
        String assistantBody = extractAssistantBody(line);
        if (assistantBody != null) {
            return renderAssistantLine(line, assistantBody);
        }
        if (line.startsWith(ERROR_LABEL)
                || normalizedLine.startsWith("\u2022 Command failed ")
                || normalizedLine.startsWith("\u2022 Tool failed ")
                || normalizedLine.startsWith("command failed")
                || normalizedLine.startsWith("tool failed")
                || normalizedLine.startsWith("turn failed")) {
            return TuiAnsi.fg(line, theme.getDanger(), ansi);
        }
        String lowerLine = normalizedLine.toLowerCase(Locale.ROOT);
        if (normalizedLine.startsWith("\u2022 Running ")
                || normalizedLine.startsWith("\u2022 Reading ")
                || normalizedLine.startsWith("\u2022 Applying")
                || normalizedLine.startsWith("\u2022 Writing to process")
                || normalizedLine.startsWith("\u2022 Stopping process")
                || normalizedLine.startsWith("thinking")
                || normalizedLine.startsWith("planning")
                || normalizedLine.startsWith("reading")
                || normalizedLine.startsWith("running")
                || normalizedLine.startsWith("applying")
                || normalizedLine.startsWith("writing to process")
                || normalizedLine.startsWith("stopping process")
                || normalizedLine.startsWith("working")
                || normalizedLine.startsWith("$ ")) {
            return TuiAnsi.fg(line, theme.getAccent(), ansi);
        }
        if (normalizedLine.startsWith("\u2022 Ran ")
                || normalizedLine.startsWith("\u2022 Read ")
                || normalizedLine.startsWith("\u2022 Applied patch")
                || lowerLine.startsWith("ran ")
                || lowerLine.startsWith("read `")
                || lowerLine.startsWith("applied patch")) {
            return TuiAnsi.fg(line, theme.getSuccess(), ansi);
        }
        if (line.startsWith(NOTE_LABEL) || line.startsWith(PROCESS_LABEL)) {
            return TuiAnsi.fg(line, theme.getMuted(), ansi);
        }
        if (line.startsWith("> ")) {
            return TuiAnsi.bold(TuiAnsi.fg(line, theme.getBrand(), ansi), ansi);
        }
        if (line.startsWith("  \u2502 ")
                || line.startsWith("  \u2514 ")
                || line.startsWith("    ")) {
            return TuiAnsi.fg(line, theme.getMuted(), ansi);
        }
        return TuiAnsi.fg(line, theme.getText(), ansi);
    }

    private String normalizeStatusPhrase(String detail, TuiAssistantPhase phase) {
        String normalized = trimToNull(detail);
        if (!isBlank(normalized)) {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.startsWith("thinking about:")
                    || lower.startsWith("waiting for model output")
                    || lower.startsWith("streaming model output")
                    || lower.startsWith("tool finished, continuing")) {
                return "thinking...";
            }
            if (lower.startsWith("preparing next step")) {
                return "planning...";
            }
            if (lower.startsWith("running command")) {
                return "running command...";
            }
            if (lower.startsWith("reading file content")) {
                return "reading files...";
            }
            if (lower.startsWith("applying workspace patch")) {
                return "applying patch...";
            }
            if (lower.startsWith("fetching buffered logs")) {
                return "reading logs...";
            }
            if (lower.startsWith("refreshing process metadata")) {
                return "reading process state...";
            }
            if (lower.startsWith("writing to process stdin")) {
                return "writing to process...";
            }
            if (lower.startsWith("stopping process")) {
                return "stopping process...";
            }
            if (lower.startsWith("turn failed") || lower.startsWith("agent run failed")) {
                return clip(lower, 96);
            }
            if (lower.startsWith("turn complete")) {
                return null;
            }
            return clip(lower, 96);
        }
        if (phase == TuiAssistantPhase.ERROR) {
            return "turn failed.";
        }
        if (phase == TuiAssistantPhase.WAITING_TOOL_RESULT) {
            return "working...";
        }
        if (phase == TuiAssistantPhase.COMPLETE) {
            return null;
        }
        return "thinking...";
    }

    private String extractApprovalCommand(String summary) {
        if (isBlank(summary)) {
            return null;
        }
        int commandIndex = summary.indexOf("command=");
        if (commandIndex >= 0) {
            int start = commandIndex + "command=".length();
            int end = summary.indexOf(", processId=", start);
            if (end < 0) {
                end = summary.length();
            }
            return summary.substring(start, end).trim();
        }
        int patchIndex = summary.indexOf("patch=");
        if (patchIndex >= 0) {
            return summary.substring(patchIndex + "patch=".length()).trim();
        }
        return null;
    }

    private String statusPrefix(TuiAssistantViewModel viewModel) {
        if (viewModel == null || viewModel.getPhase() == null) {
            return "-";
        }
        if (viewModel.getPhase() == TuiAssistantPhase.ERROR) {
            return "!";
        }
        if (viewModel.getPhase() == TuiAssistantPhase.COMPLETE) {
            return "*";
        }
        long tick = System.currentTimeMillis() / 160L;
        int index = (int) (tick % STATUS_FRAMES.length);
        return STATUS_FRAMES[index];
    }

    private String stripStatusPrefix(String line) {
        if (line == null || line.length() < 3) {
            return defaultText(line, "");
        }
        char first = line.charAt(0);
        if ((first == '-' || first == '\\' || first == '|' || first == '/' || first == '!' || first == '*')
                && line.charAt(1) == ' ') {
            return line.substring(2);
        }
        return line;
    }

    private String renderAssistantLine(String line, String assistantBody) {
        String trimmed = assistantBody.trim();
        if (assistantBody.isEmpty()) {
            return "";
        }
        boolean continuation = line.startsWith(ASSISTANT_CONTINUATION_PREFIX);
        String visiblePrefix = continuation ? BULLET_CONTINUATION : BULLET_PREFIX;
        if (assistantBody.startsWith(CODE_LINE_PREFIX)) {
            return renderInlineMarkdown(visiblePrefix, theme.getText()) + renderCodeBlockContent(assistantBody.substring(CODE_LINE_PREFIX.length()));
        }
        if (trimmed.startsWith("#")) {
            return renderInlineMarkdown(visiblePrefix, theme.getBrand())
                    + TuiAnsi.bold(renderInlineMarkdown(assistantBody, theme.getBrand()), ansi);
        }
        if (trimmed.startsWith(">")) {
            return renderInlineMarkdown(visiblePrefix, theme.getMuted()) + renderInlineMarkdown(assistantBody, theme.getMuted());
        }
        return renderInlineMarkdown(visiblePrefix, theme.getText()) + renderInlineMarkdown(assistantBody, theme.getText());
    }

    private String renderReasoningLine(String line, String reasoningBody) {
        String trimmed = reasoningBody.trim();
        if (reasoningBody.isEmpty()) {
            return "";
        }
        boolean continuation = line.startsWith(REASONING_CONTINUATION_PREFIX);
        String visiblePrefix = continuation ? repeat(' ', THINKING_LABEL.length()) : THINKING_LABEL;
        if (reasoningBody.startsWith(CODE_LINE_PREFIX)) {
            return renderInlineMarkdown(visiblePrefix, theme.getMuted())
                    + renderCodeBlockContent(reasoningBody.substring(CODE_LINE_PREFIX.length()));
        }
        return renderInlineMarkdown(visiblePrefix, theme.getMuted()) + renderInlineMarkdown(reasoningBody, theme.getMuted());
    }

    private String renderInlineMarkdown(String text, String baseColor) {
        if (text == null) {
            return "";
        }
        if (!ansi) {
            return stripInlineMarkdown(text);
        }
        StringBuilder out = new StringBuilder();
        StringBuilder buffer = new StringBuilder();
        boolean bold = false;
        boolean code = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                appendStyledSegment(out, buffer.toString(), baseColor, bold, code);
                buffer.setLength(0);
                bold = !bold;
                i++;
                continue;
            }
            if (current == '`') {
                appendStyledSegment(out, buffer.toString(), baseColor, bold, code);
                buffer.setLength(0);
                code = !code;
                continue;
            }
            buffer.append(current);
        }
        appendStyledSegment(out, buffer.toString(), baseColor, bold, code);
        return out.toString();
    }

    private void appendStyledSegment(StringBuilder out, String segment, String baseColor, boolean bold, boolean code) {
        if (out == null || segment == null || segment.isEmpty()) {
            return;
        }
        String color = code ? theme.getAccent() : baseColor;
        String rendered = TuiAnsi.style(segment, color, null, ansi, bold || code);
        out.append(rendered);
    }

    private String renderCodeBlockContent(String content) {
        if (!ansi) {
            return content == null ? "" : content;
        }
        return renderCodeTokens(content);
    }

    private String renderCodeTokens(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < content.length()) {
            char current = content.charAt(index);
            if (Character.isWhitespace(current)) {
                int end = index + 1;
                while (end < content.length() && Character.isWhitespace(content.charAt(end))) {
                    end++;
                }
                appendCodeToken(out, content.substring(index, end), codeTextColor(), false);
                index = end;
                continue;
            }
            if (current == '/' && index + 1 < content.length() && content.charAt(index + 1) == '/') {
                appendCodeToken(out, content.substring(index), codeCommentColor(), false);
                break;
            }
            if (current == '#' && isHashCommentStart(content, index)) {
                appendCodeToken(out, content.substring(index), codeCommentColor(), false);
                break;
            }
            if (current == '"' || current == '\'') {
                int end = findStringEnd(content, index);
                appendCodeToken(out, content.substring(index, end), codeStringColor(), false);
                index = end;
                continue;
            }
            if (isNumberStart(content, index)) {
                int end = findNumberEnd(content, index);
                appendCodeToken(out, content.substring(index, end), codeNumberColor(), false);
                index = end;
                continue;
            }
            if (isWordStart(current)) {
                int end = index + 1;
                while (end < content.length() && isWordPart(content.charAt(end))) {
                    end++;
                }
                String token = content.substring(index, end);
                boolean keyword = isCodeKeyword(token);
                appendCodeToken(out, token, keyword ? codeKeywordColor() : codeTextColor(), keyword);
                index = end;
                continue;
            }
            appendCodeToken(out, String.valueOf(current), codeTextColor(), false);
            index++;
        }
        return out.toString();
    }

    private void appendCodeToken(StringBuilder out, String token, String foreground, boolean bold) {
        if (out == null || token == null || token.isEmpty()) {
            return;
        }
        out.append(TuiAnsi.style(token, foreground, codeBackgroundColor(), ansi, bold));
    }

    private boolean isCodeKeyword(String token) {
        if (isBlank(token)) {
            return false;
        }
        return CODE_KEYWORDS.contains(token) || CODE_KEYWORDS.contains(token.toLowerCase(Locale.ROOT));
    }

    private boolean isHashCommentStart(String content, int index) {
        return index == 0 || Character.isWhitespace(content.charAt(index - 1));
    }

    private int findStringEnd(String content, int start) {
        char quote = content.charAt(start);
        int index = start + 1;
        boolean escaped = false;
        while (index < content.length()) {
            char current = content.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == quote) {
                return index + 1;
            }
            index++;
        }
        return content.length();
    }

    private boolean isNumberStart(String content, int index) {
        char current = content.charAt(index);
        if (Character.isDigit(current)) {
            return true;
        }
        return current == '-'
                && index + 1 < content.length()
                && Character.isDigit(content.charAt(index + 1));
    }

    private int findNumberEnd(String content, int start) {
        int index = start;
        if (content.charAt(index) == '-') {
            index++;
        }
        if (index + 1 < content.length()
                && content.charAt(index) == '0'
                && (content.charAt(index + 1) == 'x' || content.charAt(index + 1) == 'X')) {
            index += 2;
            while (index < content.length() && isHexDigit(content.charAt(index))) {
                index++;
            }
            return index;
        }
        while (index < content.length()) {
            char current = content.charAt(index);
            if (Character.isDigit(current)
                    || current == '_'
                    || current == '.'
                    || current == 'e'
                    || current == 'E'
                    || current == '+'
                    || current == '-'
                    || current == 'f'
                    || current == 'F'
                    || current == 'd'
                    || current == 'D'
                    || current == 'l'
                    || current == 'L') {
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private boolean isHexDigit(char value) {
        return Character.isDigit(value)
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F')
                || value == '_';
    }

    private boolean isWordStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private boolean isWordPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private String codeBackgroundColor() {
        return firstNonBlank(theme == null ? null : theme.getCodeBackground(), "#111827");
    }

    private String codeTextColor() {
        return firstNonBlank(theme == null ? null : theme.getCodeText(),
                theme == null ? null : theme.getText(), "#f3f4f6");
    }

    private String codeKeywordColor() {
        return firstNonBlank(theme == null ? null : theme.getCodeKeyword(),
                theme == null ? null : theme.getBrand(), "#7cc6fe");
    }

    private String codeStringColor() {
        return firstNonBlank(theme == null ? null : theme.getCodeString(),
                theme == null ? null : theme.getSuccess(), "#8fd694");
    }

    private String codeCommentColor() {
        return firstNonBlank(theme == null ? null : theme.getCodeComment(),
                theme == null ? null : theme.getMuted(), "#9ca3af");
    }

    private String codeNumberColor() {
        return firstNonBlank(theme == null ? null : theme.getCodeNumber(),
                theme == null ? null : theme.getAccent(), "#f5b14c");
    }

    private String stripInlineMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("**", "").replace("`", "");
    }

    private String normalizeLegacyTranscriptLine(String line) {
        if (line == null) {
            return null;
        }
        if (line.startsWith("you> ")) {
            return BULLET_PREFIX + line.substring("you> ".length());
        }
        if (line.startsWith("assistant> ")) {
            return BULLET_PREFIX + line.substring("assistant> ".length());
        }
        if (line.startsWith("thinking> ")) {
            return THINKING_LABEL + line.substring("thinking> ".length());
        }
        return line;
    }

    private String extractAssistantBody(String line) {
        if (line == null) {
            return null;
        }
        if (line.startsWith(ASSISTANT_PREFIX)) {
            return line.substring(ASSISTANT_PREFIX.length());
        }
        if (line.startsWith(ASSISTANT_CONTINUATION_PREFIX)) {
            return line.substring(ASSISTANT_CONTINUATION_PREFIX.length());
        }
        return null;
    }

    private String extractReasoningBody(String line) {
        if (line == null) {
            return null;
        }
        if (line.startsWith(REASONING_PREFIX)) {
            return line.substring(REASONING_PREFIX.length());
        }
        if (line.startsWith(REASONING_CONTINUATION_PREFIX)) {
            return line.substring(REASONING_CONTINUATION_PREFIX.length());
        }
        return null;
    }

    private String resolveActiveInputBuffer(TuiInteractionState state) {
        if (state == null) {
            return "";
        }
        if (state.isProcessInspectorOpen()) {
            return state.getProcessInputBuffer();
        }
        return state.getInputBuffer();
    }

    private boolean hasPendingApproval(TuiInteractionState state) {
        TuiInteractionState.ApprovalSnapshot snapshot = state == null ? null : state.getApprovalSnapshot();
        return snapshot != null && snapshot.isPending();
    }

    private String lastAssistantMessage() {
        if (cachedEvents == null || cachedEvents.isEmpty()) {
            return null;
        }
        for (int i = cachedEvents.size() - 1; i >= 0; i--) {
            SessionEvent event = cachedEvents.get(i);
            if (event != null && event.getType() == SessionEventType.ASSISTANT_MESSAGE) {
                return trimToNull(firstNonBlank(payloadString(event.getPayload(), "output"), event.getSummary()));
            }
        }
        return null;
    }

    private void appendRecentSessionHints(List<String> lines) {
        if (lines == null || cachedSessions == null || cachedSessions.isEmpty()) {
            return;
        }
        int limit = Math.min(2, cachedSessions.size());
        for (int i = 0; i < limit; i++) {
            CodingSessionDescriptor session = cachedSessions.get(i);
            if (session == null) {
                continue;
            }
            String model = trimToNull(session.getModel());
            String workspace = trimToNull(lastPathSegment(session.getWorkspace()));
            String sessionId = shortenSessionId(session.getSessionId());
            StringBuilder line = new StringBuilder(STARTUP_LABEL).append("Recent ");
            if (!isBlank(sessionId)) {
                line.append(sessionId);
            } else {
                line.append("session");
            }
            if (!isBlank(workspace)) {
                line.append("  ").append(workspace);
            }
            if (!isBlank(model)) {
                line.append("  ").append(model);
            }
            lines.add(clip(line.toString(), 108));
        }
    }

    private String shortenSessionId(String sessionId) {
        String value = trimToNull(sessionId);
        if (isBlank(value)) {
            return null;
        }
        if (value.length() <= 12) {
            return value;
        }
        return value.substring(0, 12);
    }

    private String payloadString(Map<String, Object> payload, String key) {
        if (payload == null || isBlank(key)) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> payloadLines(Map<String, Object> payload, String key) {
        if (payload == null || isBlank(key)) {
            return new ArrayList<String>();
        }
        Object value = payload.get(key);
        if (value == null) {
            return new ArrayList<String>();
        }
        if (value instanceof Iterable<?>) {
            return toStringLines((Iterable<?>) value);
        }
        if (value instanceof String) {
            String raw = (String) value;
            if (isBlank(raw)) {
                return new ArrayList<String>();
            }
            try {
                return toStringLines(JSON.parseArray(raw));
            } catch (Exception ignored) {
                return new ArrayList<String>();
            }
        }
        return new ArrayList<String>();
    }

    private List<String> toStringLines(Iterable<?> source) {
        List<String> lines = new ArrayList<String>();
        if (source == null) {
            return lines;
        }
        for (Object item : source) {
            if (item == null) {
                continue;
            }
            String line = String.valueOf(item);
            if (!isBlank(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void appendWrappedText(List<String> lines, String prefix, String rawText, int maxLines, int maxChars) {
        if (lines == null || isBlank(rawText) || maxLines <= 0) {
            return;
        }
        String[] rawLines = rawText.replace("\r", "").split("\n");
        String continuation = repeat(' ', prefix == null ? 0 : prefix.length());
        int count = 0;
        for (String rawLine : rawLines) {
            if (count >= maxLines) {
                lines.add(continuation + "...");
                return;
            }
            String safeLine = clip(rawLine, maxChars);
            lines.add((count == 0 ? defaultText(prefix, "") : continuation) + safeLine);
            count++;
        }
    }

    private void appendBlankLineIfNeeded(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (!isBlank(lines.get(lines.size() - 1))) {
            lines.add("");
        }
    }

    private List<String> wrapPrefixedText(String firstPrefix, String continuationPrefix, String rawText, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        if (isBlank(rawText)) {
            return lines;
        }
        String first = firstPrefix == null ? "" : firstPrefix;
        String continuation = continuationPrefix == null ? "" : continuationPrefix;
        int firstWidth = Math.max(12, maxWidth - first.length());
        int continuationWidth = Math.max(12, maxWidth - continuation.length());
        boolean firstLine = true;
        String[] paragraphs = rawText.replace("\r", "").split("\n");
        for (String paragraph : paragraphs) {
            String text = trimToNull(paragraph);
            if (isBlank(text)) {
                continue;
            }
            while (!isBlank(text)) {
                int width = firstLine ? firstWidth : continuationWidth;
                int split = findWrapIndex(text, width);
                String chunk = text.substring(0, split).trim();
                lines.add((firstLine ? first : continuation) + chunk);
                text = text.substring(split).trim();
                firstLine = false;
            }
        }
        return lines;
    }

    private int findWrapIndex(String text, int width) {
        if (isBlank(text) || text.length() <= width) {
            return text == null ? 0 : text.length();
        }
        int whitespace = -1;
        for (int i = Math.min(width, text.length() - 1); i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                whitespace = i;
                break;
            }
        }
        return whitespace > 0 ? whitespace : width;
    }

    private void appendMultiline(List<String> lines, String rawContent, int maxLines, int maxChars) {
        if (lines == null || isBlank(rawContent) || maxLines <= 0) {
            return;
        }
        String[] rawLines = rawContent.replace("\r", "").split("\n");
        int count = 0;
        for (String rawLine : rawLines) {
            if (count >= maxLines) {
                lines.add("...");
                return;
            }
            if (!isBlank(rawLine)) {
                lines.add(clip(rawLine, maxChars));
                count++;
            }
        }
    }

    private List<String> copyLines(List<String> lines, int maxLines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<String>(lines);
        return copy.size() > maxLines ? new ArrayList<String>(copy.subList(0, maxLines)) : copy;
    }

    private <T> List<T> trimObjects(List<T> source, int maxEntries) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> copy = new ArrayList<T>(source);
        return copy.size() > maxEntries ? new ArrayList<T>(copy.subList(0, maxEntries)) : copy;
    }

    private <T> List<T> tailObjects(List<T> source, int maxEntries) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> copy = new ArrayList<T>(source);
        return copy.size() > maxEntries ? new ArrayList<T>(copy.subList(copy.size() - maxEntries, copy.size())) : copy;
    }

    private List<String> tailLines(List<String> source, int maxEntries) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.size() > maxEntries
                ? new ArrayList<String>(source.subList(source.size() - maxEntries, source.size()))
                : new ArrayList<String>(source);
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

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String lastPathSegment(String value) {
        if (isBlank(value)) {
            return value;
        }
        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 && index + 1 < normalized.length() ? normalized.substring(index + 1) : normalized;
    }

    private String repeat(char c, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private String line(char c, int width) {
        return repeat(c, width);
    }

    private String clip(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private String clipCodeLine(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\t', ' ');
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
