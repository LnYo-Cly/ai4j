package io.github.lnyocly.ai4j.tui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TuiInteractionState {

    public enum PaletteMode {
        GLOBAL,
        SLASH
    }

    private ApprovalSnapshot approvalSnapshot = ApprovalSnapshot.idle(null);
    private Runnable renderCallback;
    private TuiPanelId focusedPanel = TuiPanelId.INPUT;
    private final StringBuilder inputBuffer = new StringBuilder();
    private final StringBuilder processInputBuffer = new StringBuilder();
    private boolean paletteOpen;
    private PaletteMode paletteMode = PaletteMode.GLOBAL;
    private String paletteQuery = "";
    private int paletteSelectedIndex;
    private List<TuiPaletteItem> paletteItems = Collections.emptyList();
    private String selectedProcessId;
    private boolean processInspectorOpen;
    private boolean replayViewerOpen;
    private boolean teamBoardOpen;
    private int replayScrollOffset;
    private int teamBoardScrollOffset;
    private int transcriptScrollOffset;

    public synchronized void setRenderCallback(Runnable renderCallback) {
        this.renderCallback = renderCallback;
    }

    public synchronized ApprovalSnapshot getApprovalSnapshot() {
        return approvalSnapshot;
    }

    public synchronized TuiPanelId getFocusedPanel() {
        return focusedPanel;
    }

    public synchronized String getInputBuffer() {
        return inputBuffer.toString();
    }

    public synchronized String getProcessInputBuffer() {
        return processInputBuffer.toString();
    }

    public synchronized boolean isPaletteOpen() {
        return paletteOpen;
    }

    public synchronized String getPaletteQuery() {
        return paletteQuery;
    }

    public synchronized PaletteMode getPaletteMode() {
        return paletteMode;
    }

    public synchronized int getPaletteSelectedIndex() {
        return paletteSelectedIndex;
    }

    public synchronized List<TuiPaletteItem> getPaletteItems() {
        return new ArrayList<TuiPaletteItem>(filterPaletteItems());
    }

    public synchronized String getSelectedProcessId() {
        return selectedProcessId;
    }

    public synchronized boolean isProcessInspectorOpen() {
        return processInspectorOpen;
    }

    public synchronized boolean isReplayViewerOpen() {
        return replayViewerOpen;
    }

    public synchronized boolean isTeamBoardOpen() {
        return teamBoardOpen;
    }

    public synchronized int getReplayScrollOffset() {
        return replayScrollOffset;
    }

    public synchronized int getTeamBoardScrollOffset() {
        return teamBoardScrollOffset;
    }

    public synchronized int getTranscriptScrollOffset() {
        return transcriptScrollOffset;
    }

    public void setFocusedPanel(TuiPanelId focusedPanel) {
        updateState(() -> this.focusedPanel = focusedPanel == null ? TuiPanelId.INPUT : focusedPanel);
    }

    public void focusNextPanel() {
        updateState(() -> {
            TuiPanelId[] values = TuiPanelId.values();
            int nextIndex = (focusedPanel.ordinal() + 1) % values.length;
            focusedPanel = values[nextIndex];
        });
    }

    public void appendInput(String text) {
        if (isBlank(text)) {
            return;
        }
        updateState(() -> inputBuffer.append(text));
    }

    public void backspaceInput() {
        updateState(() -> {
            if (inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
        });
    }

    public synchronized String consumeInputBuffer() {
        String value = inputBuffer.toString();
        inputBuffer.setLength(0);
        triggerRender();
        return value;
    }

    public synchronized String consumeInputBufferSilently() {
        String value = inputBuffer.toString();
        inputBuffer.setLength(0);
        return value;
    }

    public void clearInputBuffer() {
        updateState(() -> inputBuffer.setLength(0));
    }

    public void replaceInputBuffer(String value) {
        updateState(() -> {
            inputBuffer.setLength(0);
            if (!isBlank(value)) {
                inputBuffer.append(value);
            }
        });
    }

    public void replaceInputBufferAndClosePalette(String value) {
        updateState(() -> {
            inputBuffer.setLength(0);
            if (!isBlank(value)) {
                inputBuffer.append(value);
            }
            closePaletteState();
        });
    }

    public void appendInputAndSyncSlashPalette(String text, List<TuiPaletteItem> items) {
        if (isBlank(text)) {
            return;
        }
        updateState(() -> {
            inputBuffer.append(text);
            syncSlashPaletteState(items);
        });
    }

    public void backspaceInputAndSyncSlashPalette(List<TuiPaletteItem> items) {
        updateState(() -> {
            if (inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
            syncSlashPaletteState(items);
        });
    }

    public void syncSlashPalette(List<TuiPaletteItem> items) {
        updateState(() -> syncSlashPaletteState(items));
    }

    public void appendProcessInput(String text) {
        if (isBlank(text)) {
            return;
        }
        updateState(() -> processInputBuffer.append(text));
    }

    public void backspaceProcessInput() {
        updateState(() -> {
            if (processInputBuffer.length() > 0) {
                processInputBuffer.deleteCharAt(processInputBuffer.length() - 1);
            }
        });
    }

    public synchronized String consumeProcessInputBuffer() {
        String value = processInputBuffer.toString();
        processInputBuffer.setLength(0);
        triggerRender();
        return value;
    }

    public void clearProcessInputBuffer() {
        updateState(() -> processInputBuffer.setLength(0));
    }

    public void openPalette(List<TuiPaletteItem> items) {
        openPalette(items, PaletteMode.GLOBAL, "");
    }

    public void openSlashPalette(List<TuiPaletteItem> items, String input) {
        openPalette(items, PaletteMode.SLASH, normalizeSlashQuery(input));
    }

    public void refreshSlashPalette(List<TuiPaletteItem> items, String input) {
        updateState(() -> {
            openSlashPaletteState(items, input);
        });
    }

    private void openPalette(List<TuiPaletteItem> items, PaletteMode mode, String query) {
        updateState(() -> {
            paletteOpen = true;
            paletteMode = mode == null ? PaletteMode.GLOBAL : mode;
            paletteQuery = query == null ? "" : query;
            paletteSelectedIndex = 0;
            paletteItems = items == null ? Collections.<TuiPaletteItem>emptyList() : new ArrayList<TuiPaletteItem>(items);
        });
    }

    public void closePalette() {
        updateState(this::closePaletteState);
    }

    public synchronized void closePaletteSilently() {
        closePaletteState();
    }

    public void appendPaletteQuery(String text) {
        if (isBlank(text)) {
            return;
        }
        updateState(() -> {
            paletteQuery = paletteQuery + text;
            paletteSelectedIndex = 0;
        });
    }

    public void backspacePaletteQuery() {
        updateState(() -> {
            if (!isBlank(paletteQuery)) {
                paletteQuery = paletteQuery.substring(0, paletteQuery.length() - 1);
            }
            paletteSelectedIndex = 0;
        });
    }

    public void movePaletteSelection(int delta) {
        updateState(() -> {
            List<TuiPaletteItem> visible = filterPaletteItems();
            if (visible.isEmpty()) {
                paletteSelectedIndex = 0;
                return;
            }
            int size = visible.size();
            int index = paletteSelectedIndex + delta;
            while (index < 0) {
                index += size;
            }
            paletteSelectedIndex = index % size;
        });
    }

    public void selectProcess(String processId) {
        updateState(() -> this.selectedProcessId = isBlank(processId) ? null : processId);
    }

    public void selectAdjacentProcess(List<String> processIds, int delta) {
        if (processIds == null || processIds.isEmpty()) {
            updateState(() -> selectedProcessId = null);
            return;
        }
        updateState(() -> {
            int currentIndex = processIds.indexOf(selectedProcessId);
            if (currentIndex < 0) {
                selectedProcessId = delta >= 0 ? processIds.get(0) : processIds.get(processIds.size() - 1);
                return;
            }
            int index = currentIndex + delta;
            while (index < 0) {
                index += processIds.size();
            }
            selectedProcessId = processIds.get(index % processIds.size());
        });
    }

    public void openProcessInspector(String processId) {
        updateState(() -> {
            if (!isBlank(processId)) {
                selectedProcessId = processId;
            }
            processInspectorOpen = true;
            replayViewerOpen = false;
            teamBoardOpen = false;
            processInputBuffer.setLength(0);
        });
    }

    public void closeProcessInspector() {
        updateState(() -> {
            processInspectorOpen = false;
            processInputBuffer.setLength(0);
        });
    }

    public void openReplayViewer() {
        updateState(() -> {
            replayViewerOpen = true;
            processInspectorOpen = false;
            teamBoardOpen = false;
            replayScrollOffset = 0;
        });
    }

    public void closeReplayViewer() {
        updateState(() -> replayViewerOpen = false);
    }

    public void moveReplayScroll(int delta) {
        updateState(() -> replayScrollOffset = Math.max(0, replayScrollOffset + delta));
    }

    public void openTeamBoard() {
        updateState(() -> {
            teamBoardOpen = true;
            replayViewerOpen = false;
            processInspectorOpen = false;
            teamBoardScrollOffset = 0;
        });
    }

    public void closeTeamBoard() {
        updateState(() -> teamBoardOpen = false);
    }

    public void moveTeamBoardScroll(int delta) {
        updateState(() -> teamBoardScrollOffset = Math.max(0, teamBoardScrollOffset + delta));
    }

    public void resetTranscriptScroll() {
        synchronized (this) {
            if (transcriptScrollOffset == 0) {
                return;
            }
        }
        updateState(() -> transcriptScrollOffset = 0);
    }

    public void moveTranscriptScroll(int delta) {
        updateState(() -> transcriptScrollOffset = Math.max(0, transcriptScrollOffset + delta));
    }

    public synchronized TuiPaletteItem getSelectedPaletteItem() {
        List<TuiPaletteItem> visible = filterPaletteItems();
        if (visible.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(paletteSelectedIndex, visible.size() - 1));
        return visible.get(index);
    }

    public void showApproval(String mode, String toolName, String summary) {
        updateApproval(ApprovalSnapshot.pending(mode, toolName, summary));
    }

    public void resolveApproval(String toolName, boolean approved) {
        updateApproval(ApprovalSnapshot.resolved(toolName, approved));
    }

    public void clearApproval() {
        updateApproval(ApprovalSnapshot.idle(null));
    }

    private synchronized List<TuiPaletteItem> filterPaletteItems() {
        if (paletteItems == null || paletteItems.isEmpty()) {
            return Collections.emptyList();
        }
        if (isBlank(paletteQuery)) {
            return new ArrayList<TuiPaletteItem>(paletteItems);
        }
        if ("/".equals(paletteQuery.trim())) {
            return new ArrayList<TuiPaletteItem>(paletteItems);
        }
        String query = paletteQuery.toLowerCase(Locale.ROOT);
        List<TuiPaletteItem> filtered = new ArrayList<TuiPaletteItem>();
        for (TuiPaletteItem item : paletteItems) {
            if (item == null) {
                continue;
            }
            if (matches(query, item.getLabel())
                    || matches(query, item.getDetail())
                    || matches(query, item.getGroup())
                    || matches(query, item.getCommand())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean matches(String query, String value) {
        return !isBlank(value) && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void syncSlashPaletteState(List<TuiPaletteItem> items) {
        String input = inputBuffer.toString();
        if (shouldOpenSlashPalette(input)) {
            openSlashPaletteState(items, input);
            return;
        }
        if (paletteMode == PaletteMode.SLASH) {
            closePaletteState();
        }
    }

    private void openSlashPaletteState(List<TuiPaletteItem> items, String input) {
        paletteOpen = true;
        paletteMode = PaletteMode.SLASH;
        paletteQuery = normalizeSlashQuery(input);
        paletteSelectedIndex = 0;
        paletteItems = items == null ? Collections.<TuiPaletteItem>emptyList() : new ArrayList<TuiPaletteItem>(items);
    }

    private void closePaletteState() {
        paletteOpen = false;
        paletteMode = PaletteMode.GLOBAL;
        paletteQuery = "";
        paletteSelectedIndex = 0;
    }

    private boolean shouldOpenSlashPalette(String input) {
        return !isBlank(input)
                && input.startsWith("/")
                && input.indexOf(' ') < 0
                && input.indexOf('\t') < 0;
    }

    private void updateApproval(ApprovalSnapshot snapshot) {
        synchronized (this) {
            this.approvalSnapshot = snapshot == null ? ApprovalSnapshot.idle(null) : snapshot;
        }
        triggerRender();
    }

    private void updateState(StateMutation mutation) {
        synchronized (this) {
            mutation.apply();
        }
        triggerRender();
    }

    private void triggerRender() {
        Runnable callback;
        synchronized (this) {
            callback = this.renderCallback;
        }
        if (callback != null) {
            callback.run();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeSlashQuery(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
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

    private interface StateMutation {
        void apply();
    }

    public static final class ApprovalSnapshot {

        private final boolean pending;
        private final String mode;
        private final String toolName;
        private final String summary;
        private final String lastDecision;

        private ApprovalSnapshot(boolean pending,
                                 String mode,
                                 String toolName,
                                 String summary,
                                 String lastDecision) {
            this.pending = pending;
            this.mode = defaultText(mode, "auto");
            this.toolName = toolName;
            this.summary = summary;
            this.lastDecision = lastDecision;
        }

        public static ApprovalSnapshot pending(String mode, String toolName, String summary) {
            return new ApprovalSnapshot(true, mode, toolName, summary, null);
        }

        public static ApprovalSnapshot resolved(String toolName, boolean approved) {
            return new ApprovalSnapshot(false,
                    "auto",
                    toolName,
                    null,
                    "last=" + (approved ? "approved" : "rejected") + " tool=" + defaultText(toolName, "unknown"));
        }

        public static ApprovalSnapshot idle(String lastDecision) {
            return new ApprovalSnapshot(false, "auto", null, null, lastDecision);
        }

        public boolean isPending() {
            return pending;
        }

        public String getMode() {
            return mode;
        }

        public String getToolName() {
            return toolName;
        }

        public String getSummary() {
            return summary;
        }

        public String getLastDecision() {
            return lastDecision;
        }

        private static String defaultText(String value, String defaultValue) {
            return value == null || value.trim().isEmpty() ? defaultValue : value;
        }
    }
}
