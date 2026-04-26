package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.util.ArrayList;
import java.util.List;

public class TuiScreenModel {

    private final TuiConfig config;
    private final TuiTheme theme;
    private final CodingSessionDescriptor descriptor;
    private final CodingSessionSnapshot snapshot;
    private final CodingSessionCheckpoint checkpoint;
    private final TuiRenderContext renderContext;
    private final TuiInteractionState interactionState;
    private final List<CodingSessionDescriptor> cachedSessions;
    private final List<String> cachedHistory;
    private final List<String> cachedTree;
    private final List<String> cachedCommands;
    private final List<SessionEvent> cachedEvents;
    private final List<String> cachedReplay;
    private final List<String> cachedTeamBoard;
    private final BashProcessInfo inspectedProcess;
    private final BashProcessLogChunk inspectedProcessLogs;
    private final String assistantOutput;
    private final TuiAssistantViewModel assistantViewModel;

    private TuiScreenModel(Builder builder) {
        this.config = builder.config;
        this.theme = builder.theme;
        this.descriptor = builder.descriptor;
        this.snapshot = builder.snapshot;
        this.checkpoint = builder.checkpoint;
        this.renderContext = builder.renderContext;
        this.interactionState = builder.interactionState;
        this.cachedSessions = builder.cachedSessions;
        this.cachedHistory = builder.cachedHistory;
        this.cachedTree = builder.cachedTree;
        this.cachedCommands = builder.cachedCommands;
        this.cachedEvents = builder.cachedEvents;
        this.cachedReplay = builder.cachedReplay;
        this.cachedTeamBoard = builder.cachedTeamBoard;
        this.inspectedProcess = builder.inspectedProcess;
        this.inspectedProcessLogs = builder.inspectedProcessLogs;
        this.assistantOutput = builder.assistantOutput;
        this.assistantViewModel = builder.assistantViewModel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TuiConfig getConfig() {
        return config;
    }

    public TuiTheme getTheme() {
        return theme;
    }

    public CodingSessionDescriptor getDescriptor() {
        return descriptor;
    }

    public CodingSessionSnapshot getSnapshot() {
        return snapshot;
    }

    public CodingSessionCheckpoint getCheckpoint() {
        return checkpoint;
    }

    public TuiRenderContext getRenderContext() {
        return renderContext;
    }

    public TuiInteractionState getInteractionState() {
        return interactionState;
    }

    public List<CodingSessionDescriptor> getCachedSessions() {
        return cachedSessions;
    }

    public List<String> getCachedHistory() {
        return cachedHistory;
    }

    public List<String> getCachedTree() {
        return cachedTree;
    }

    public List<String> getCachedCommands() {
        return cachedCommands;
    }

    public List<SessionEvent> getCachedEvents() {
        return cachedEvents;
    }

    public List<String> getCachedReplay() {
        return cachedReplay;
    }

    public List<String> getCachedTeamBoard() {
        return cachedTeamBoard;
    }

    public BashProcessInfo getInspectedProcess() {
        return inspectedProcess;
    }

    public BashProcessLogChunk getInspectedProcessLogs() {
        return inspectedProcessLogs;
    }

    public String getAssistantOutput() {
        return assistantOutput;
    }

    public TuiAssistantViewModel getAssistantViewModel() {
        return assistantViewModel;
    }

    public static final class Builder {

        private TuiConfig config;
        private TuiTheme theme;
        private CodingSessionDescriptor descriptor;
        private CodingSessionSnapshot snapshot;
        private CodingSessionCheckpoint checkpoint;
        private TuiRenderContext renderContext;
        private TuiInteractionState interactionState;
        private List<CodingSessionDescriptor> cachedSessions = new ArrayList<CodingSessionDescriptor>();
        private List<String> cachedHistory = new ArrayList<String>();
        private List<String> cachedTree = new ArrayList<String>();
        private List<String> cachedCommands = new ArrayList<String>();
        private List<SessionEvent> cachedEvents = new ArrayList<SessionEvent>();
        private List<String> cachedReplay = new ArrayList<String>();
        private List<String> cachedTeamBoard = new ArrayList<String>();
        private BashProcessInfo inspectedProcess;
        private BashProcessLogChunk inspectedProcessLogs;
        private String assistantOutput;
        private TuiAssistantViewModel assistantViewModel;

        private Builder() {
        }

        public Builder config(TuiConfig config) {
            this.config = config;
            return this;
        }

        public Builder theme(TuiTheme theme) {
            this.theme = theme;
            return this;
        }

        public Builder descriptor(CodingSessionDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder snapshot(CodingSessionSnapshot snapshot) {
            this.snapshot = snapshot;
            return this;
        }

        public Builder checkpoint(CodingSessionCheckpoint checkpoint) {
            this.checkpoint = checkpoint;
            return this;
        }

        public Builder renderContext(TuiRenderContext renderContext) {
            this.renderContext = renderContext;
            return this;
        }

        public Builder interactionState(TuiInteractionState interactionState) {
            this.interactionState = interactionState;
            return this;
        }

        public Builder cachedSessions(List<CodingSessionDescriptor> cachedSessions) {
            this.cachedSessions = copy(cachedSessions);
            return this;
        }

        public Builder cachedHistory(List<String> cachedHistory) {
            this.cachedHistory = copy(cachedHistory);
            return this;
        }

        public Builder cachedTree(List<String> cachedTree) {
            this.cachedTree = copy(cachedTree);
            return this;
        }

        public Builder cachedCommands(List<String> cachedCommands) {
            this.cachedCommands = copy(cachedCommands);
            return this;
        }

        public Builder cachedEvents(List<SessionEvent> cachedEvents) {
            this.cachedEvents = copy(cachedEvents);
            return this;
        }

        public Builder cachedReplay(List<String> cachedReplay) {
            this.cachedReplay = copy(cachedReplay);
            return this;
        }

        public Builder cachedTeamBoard(List<String> cachedTeamBoard) {
            this.cachedTeamBoard = copy(cachedTeamBoard);
            return this;
        }

        public Builder inspectedProcess(BashProcessInfo inspectedProcess) {
            this.inspectedProcess = inspectedProcess;
            return this;
        }

        public Builder inspectedProcessLogs(BashProcessLogChunk inspectedProcessLogs) {
            this.inspectedProcessLogs = inspectedProcessLogs;
            return this;
        }

        public Builder assistantOutput(String assistantOutput) {
            this.assistantOutput = assistantOutput;
            return this;
        }

        public Builder assistantViewModel(TuiAssistantViewModel assistantViewModel) {
            this.assistantViewModel = assistantViewModel;
            return this;
        }

        public TuiScreenModel build() {
            return new TuiScreenModel(this);
        }

        private static <T> List<T> copy(List<T> source) {
            return source == null ? new ArrayList<T>() : new ArrayList<T>(source);
        }
    }
}
