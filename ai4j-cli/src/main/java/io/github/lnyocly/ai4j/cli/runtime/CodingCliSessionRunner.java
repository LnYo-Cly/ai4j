package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.SlashCommandController;
import io.github.lnyocly.ai4j.cli.agent.CliCodingAgentRegistry;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CustomCommandRegistry;
import io.github.lnyocly.ai4j.cli.command.CustomCommandTemplate;
import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.CodingCliTuiFactory;
import io.github.lnyocly.ai4j.cli.factory.DefaultCodingCliTuiFactory;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpConfig;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpConfigManager;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpServerDefinition;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpStatusSnapshot;
import io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig;
import io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpServer;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.provider.CliProviderProfile;
import io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig;
import io.github.lnyocly.ai4j.cli.provider.CliResolvedProviderConfig;
import io.github.lnyocly.ai4j.cli.render.AssistantTranscriptRenderer;
import io.github.lnyocly.ai4j.cli.render.CliDisplayWidth;
import io.github.lnyocly.ai4j.cli.render.CliThemeStyler;
import io.github.lnyocly.ai4j.cli.render.CodexStyleBlockFormatter;
import io.github.lnyocly.ai4j.cli.render.PatchSummaryFormatter;
import io.github.lnyocly.ai4j.cli.render.TranscriptPrinter;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.StoredCodingSession;
import io.github.lnyocly.ai4j.cli.shell.JlineShellTerminalIO;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentRequest;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpointFormatter;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.loop.CodingLoopDecision;
import io.github.lnyocly.ai4j.coding.loop.CodingStopReason;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.process.BashProcessStatus;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntime;
import io.github.lnyocly.ai4j.coding.skill.CodingSkillDescriptor;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiAssistantPhase;
import io.github.lnyocly.ai4j.tui.TuiAssistantToolView;
import io.github.lnyocly.ai4j.tui.TuiAssistantViewModel;
import io.github.lnyocly.ai4j.tui.TuiConfig;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import io.github.lnyocly.ai4j.tui.TuiKeyStroke;
import io.github.lnyocly.ai4j.tui.TuiKeyType;
import io.github.lnyocly.ai4j.tui.TuiPaletteItem;
import io.github.lnyocly.ai4j.tui.TuiRenderContext;
import io.github.lnyocly.ai4j.tui.TuiRenderer;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiScreenModel;
import io.github.lnyocly.ai4j.tui.AnsiTuiRuntime;
import io.github.lnyocly.ai4j.tui.AppendOnlyTuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiTheme;
import io.github.lnyocly.ai4j.tui.TuiSessionView;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class CodingCliSessionRunner {

    private static final int DEFAULT_EVENT_LIMIT = 12;
    private static final int DEFAULT_PROCESS_LOG_LIMIT = 800;
    private static final int DEFAULT_REPLAY_LIMIT = 40;
    private static final long PROCESS_FOLLOW_POLL_MS = 250L;
    private static final int PROCESS_FOLLOW_MAX_IDLE_POLLS = 8;
    private static final long TUI_TURN_ANIMATION_POLL_MS = 160L;
    private static final long NON_ALTERNATE_SCREEN_RENDER_THROTTLE_MS = 120L;
    private static final String TURN_INTERRUPTED_MESSAGE = "Conversation interrupted by user.";
    private CodingAgent agent;
    private CliProtocol protocol;
    private CodeCommandOptions options;
    private final TerminalIO terminal;
    private final CodingSessionManager sessionManager;
    private final TuiConfigManager tuiConfigManager;
    private final CliProviderConfigManager providerConfigManager;
    private final CliMcpConfigManager mcpConfigManager;
    private final CustomCommandRegistry customCommandRegistry;
    private final TuiInteractionState interactionState;
    private final TuiRenderer tuiRenderer;
    private final TuiRuntime tuiRuntime;
    private final CodingCliAgentFactory agentFactory;
    private final Map<String, String> env;
    private final Properties properties;
    private TuiConfig tuiConfig;
    private TuiTheme tuiTheme;
    private final CodexStyleBlockFormatter codexStyleBlockFormatter = new CodexStyleBlockFormatter(120, 4);
    private final AssistantTranscriptRenderer assistantTranscriptRenderer = new AssistantTranscriptRenderer();
    private final Set<String> pausedMcpServers = new LinkedHashSet<String>();
    private List<CodingSessionDescriptor> tuiSessions = new ArrayList<CodingSessionDescriptor>();
    private List<String> tuiHistory = new ArrayList<String>();
    private List<String> tuiTree = new ArrayList<String>();
    private List<String> tuiCommands = new ArrayList<String>();
    private List<SessionEvent> tuiEvents = new ArrayList<SessionEvent>();
    private List<String> tuiReplay = new ArrayList<String>();
    private List<String> tuiTeamBoard = new ArrayList<String>();
    private BashProcessInfo tuiInspectedProcess;
    private BashProcessLogChunk tuiInspectedProcessLogs;
    private String tuiAssistantOutput;
    private final TuiLiveTurnState tuiLiveTurnState = new TuiLiveTurnState();
    private final MainBufferTurnPrinter mainBufferTurnPrinter = new MainBufferTurnPrinter();
    private final Object mainBufferTurnInterruptLock = new Object();
    private volatile boolean tuiTurnAnimationRunning;
    private Thread tuiTurnAnimationThread;
    private volatile long lastNonAlternateScreenRenderAtMs;
    private ManagedCodingSession activeSession;
    private CliMcpRuntimeManager mcpRuntimeManager;
    private boolean streamEnabled = false;
    private volatile ActiveTuiTurn activeTuiTurn;
    private volatile Thread activeMainBufferTurnThread;
    private volatile String activeMainBufferTurnId;
    private volatile boolean activeMainBufferTurnInterrupted;
    private CodingRuntime bridgedRuntime;
    private CodingTaskSessionEventBridge codingTaskEventBridge;

    public CodingCliSessionRunner(CodingAgent agent,
                                  CliProtocol protocol,
                                  CodeCommandOptions options,
                                  TerminalIO terminal,
                                  CodingSessionManager sessionManager,
                                  TuiInteractionState interactionState) {
        this(agent, protocol, options, terminal, sessionManager, interactionState,
                new DefaultCodingCliTuiFactory(), null, null, null, null);
    }

    public CodingCliSessionRunner(CodingAgent agent,
                                  CliProtocol protocol,
                                  CodeCommandOptions options,
                                  TerminalIO terminal,
                                  CodingSessionManager sessionManager,
                                  TuiInteractionState interactionState,
                                  SlashCommandController slashCommandController) {
        this(agent, protocol, options, terminal, sessionManager, interactionState,
                new DefaultCodingCliTuiFactory(), slashCommandController, null, null, null);
    }

    public CodingCliSessionRunner(CodingAgent agent,
                                  CliProtocol protocol,
                                  CodeCommandOptions options,
                                  TerminalIO terminal,
                                  CodingSessionManager sessionManager,
                                  TuiInteractionState interactionState,
                                  CodingCliTuiFactory tuiFactory) {
        this(agent, protocol, options, terminal, sessionManager, interactionState,
                tuiFactory, null, null, null, null);
    }

    public CodingCliSessionRunner(CodingAgent agent,
                                  CliProtocol protocol,
                                  CodeCommandOptions options,
                                  TerminalIO terminal,
                                  CodingSessionManager sessionManager,
                                  TuiInteractionState interactionState,
                                  CodingCliTuiFactory tuiFactory,
                                  SlashCommandController slashCommandController,
                                  CodingCliAgentFactory agentFactory,
                                  Map<String, String> env,
                                  Properties properties) {
        this(agent, protocol, options, terminal, sessionManager, interactionState,
                tuiFactory, slashCommandController, agentFactory, env, properties, true);
    }

    private CodingCliSessionRunner(CodingAgent agent,
                                   CliProtocol protocol,
                                   CodeCommandOptions options,
                                   TerminalIO terminal,
                                   CodingSessionManager sessionManager,
                                   TuiInteractionState interactionState,
                                   CodingCliTuiFactory tuiFactory,
                                   SlashCommandController slashCommandController,
                                   CodingCliAgentFactory agentFactory,
                                   Map<String, String> env,
                                   Properties properties,
                                   boolean unused) {
        this.agent = agent;
        this.protocol = protocol;
        this.options = options;
        this.streamEnabled = options != null && options.isStream();
        this.terminal = terminal;
        this.sessionManager = sessionManager;
        this.tuiConfigManager = new TuiConfigManager(java.nio.file.Paths.get(options.getWorkspace()));
        this.providerConfigManager = new CliProviderConfigManager(java.nio.file.Paths.get(options.getWorkspace()));
        this.mcpConfigManager = new CliMcpConfigManager(java.nio.file.Paths.get(options.getWorkspace()));
        this.customCommandRegistry = new CustomCommandRegistry(java.nio.file.Paths.get(options.getWorkspace()));
        this.interactionState = interactionState == null ? new TuiInteractionState() : interactionState;
        this.agentFactory = agentFactory;
        this.env = env == null ? Collections.<String, String>emptyMap() : new LinkedHashMap<String, String>(env);
        this.properties = properties == null ? new Properties() : properties;
        attachCodingTaskEventBridge();
        if (options.getUiMode() == CliUiMode.TUI) {
            CodingCliTuiSupport tuiSupport = (tuiFactory == null ? new DefaultCodingCliTuiFactory() : tuiFactory)
                    .create(options, terminal, tuiConfigManager);
            this.tuiConfig = tuiSupport == null || tuiSupport.getConfig() == null ? new TuiConfig() : tuiSupport.getConfig();
            this.tuiTheme = tuiSupport == null || tuiSupport.getTheme() == null ? new TuiTheme() : tuiSupport.getTheme();
            this.tuiRenderer = tuiSupport == null ? null : tuiSupport.getRenderer();
            this.tuiRuntime = tuiSupport == null ? null : tuiSupport.getRuntime();
            this.interactionState.setRenderCallback(new Runnable() {
                @Override
                public void run() {
                    if (activeSession != null) {
                        renderTuiFromCache(activeSession);
                    }
                }
            });
        } else {
            this.tuiRenderer = null;
            this.tuiRuntime = null;
        }
        if (terminal instanceof JlineShellTerminalIO) {
            ((JlineShellTerminalIO) terminal).updateTheme(tuiTheme);
        }
        if (slashCommandController != null) {
            slashCommandController.setProcessCandidateSupplier(
                    new java.util.function.Supplier<List<SlashCommandController.ProcessCompletionCandidate>>() {
                        @Override
                        public List<SlashCommandController.ProcessCompletionCandidate> get() {
                            return buildProcessCompletionCandidates();
                    }
                }
            );
            slashCommandController.setProfileCandidateSupplier(new java.util.function.Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return providerConfigManager.listProfileNames();
                }
            });
            slashCommandController.setModelCandidateSupplier(
                    new java.util.function.Supplier<List<SlashCommandController.ModelCompletionCandidate>>() {
                        @Override
                        public List<SlashCommandController.ModelCompletionCandidate> get() {
                            return buildModelCompletionCandidates();
                        }
                    }
            );
            slashCommandController.setMcpServerCandidateSupplier(new java.util.function.Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return listKnownMcpServerNames();
                }
            });
            slashCommandController.setSkillCandidateSupplier(new java.util.function.Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return listKnownSkillNames();
                }
            });
            slashCommandController.setAgentCandidateSupplier(new java.util.function.Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return listKnownAgentNames();
                }
            });
        }
    }

    public int run() throws Exception {
        ManagedCodingSession session = openInitialSession();
        activeSession = session;
        boolean mainBufferInteractive = useMainBufferInteractiveShell();
        boolean interactiveTui = options.getUiMode() == CliUiMode.TUI
                && tuiRuntime != null
                && tuiRuntime.supportsRawInput()
                && !mainBufferInteractive
                && isBlank(options.getPrompt());
        try {
            if (options.getUiMode() == CliUiMode.TUI
                    && isBlank(options.getPrompt())
                    && !interactiveTui
                    && !mainBufferInteractive) {
                terminal.errorln("Interactive TUI input is unavailable on this terminal, falling back to CLI mode.");
            }
            if (!interactiveTui) {
                printSessionHeader(session);
            }

            if (!isBlank(options.getPrompt())) {
                runTurn(session, options.getPrompt());
                return 0;
            }

            if (mainBufferInteractive) {
                return runCliLoop(session);
            }

            if (interactiveTui) {
                return runTuiLoop(session);
            }
            terminal.println("Use /help for in-session commands, /exit or /quit to leave.\n");
            return runCliLoop(session);
        } finally {
            detachCodingTaskEventBridge();
            closeQuietly(activeSession);
            closeMcpRuntimeQuietly(mcpRuntimeManager);
            persistSession(activeSession, false);
        }
    }

    public void setMcpRuntimeManager(CliMcpRuntimeManager mcpRuntimeManager) {
        this.mcpRuntimeManager = mcpRuntimeManager;
    }

    private int runCliLoop(ManagedCodingSession session) throws Exception {
        while (true) {
            // Keep readLine and transcript output on the same thread. When the
            // prompt lived on a background thread, JLine could still report
            // itself as "reading" for a short window after Enter, which routed
            // the next assistant block through printAbove() and created the
            // blank region the user was seeing before the actual text.
            String input = terminal.readLine("> ");
            if (input == null) {
                terminal.println("");
                return 0;
            }
            if (isBlank(input)) {
                continue;
            }
            JlineShellTerminalIO shellTerminal = terminal instanceof JlineShellTerminalIO
                    ? (JlineShellTerminalIO) terminal
                    : null;
            if (shellTerminal != null && useMainBufferInteractiveShell()) {
                shellTerminal.beginDirectOutputWindow();
            }
            try {
                DispatchResult result = dispatchInteractiveInput(session, input);
                session = result.getSession();
                activeSession = session;
                if (result.isExitRequested()) {
                    if (!useMainBufferInteractiveShell()) {
                        terminal.println("Session closed.");
                    }
                    return 0;
                }
            } finally {
                if (shellTerminal != null && useMainBufferInteractiveShell()) {
                    shellTerminal.endDirectOutputWindow();
                }
            }
        }
    }

    private int runTuiLoop(ManagedCodingSession session) throws Exception {
        tuiRuntime.enter();
        try {
            setTuiAssistantOutput("Ask AI4J to inspect this repository\nOpen the command palette with /\nReplay recent history with Ctrl+R\nOpen the team board with /team");
            renderTui(session);
            while (true) {
                session = reapCompletedTuiTurn(session);
                activeSession = session;
                TuiKeyStroke keyStroke = tuiRuntime.readKeyStroke(TUI_TURN_ANIMATION_POLL_MS);
                if (keyStroke == null) {
                    if (hasActiveTuiTurn()) {
                        continue;
                    }
                    if (terminal != null && terminal.isInputClosed()) {
                        return 0;
                    }
                    if (shouldAnimateAppendOnlyFooter() && tuiLiveTurnState.advanceAnimationTick()) {
                        renderTuiFromCache(session);
                        continue;
                    }
                    if (shouldAutoRefresh(session)) {
                        renderTui(session);
                    }
                    continue;
                }
                DispatchResult result = handleTuiKey(session, keyStroke);
                session = result.getSession();
                activeSession = session;
                if (result.isExitRequested()) {
                    return 0;
                }
            }
        } finally {
            tuiRuntime.exit();
        }
    }

    private DispatchResult handleTuiKey(ManagedCodingSession session, TuiKeyStroke keyStroke) throws Exception {
        if (keyStroke == null) {
            return DispatchResult.stay(session);
        }
        if (hasActiveTuiTurn()) {
            return handleActiveTuiTurnKey(session, keyStroke);
        }
        TuiKeyType keyType = keyStroke.getType();
        if (interactionState.isPaletteOpen()) {
            if (interactionState.getPaletteMode() == TuiInteractionState.PaletteMode.SLASH) {
                switch (keyType) {
                    case ESCAPE:
                        interactionState.closePalette();
                        return DispatchResult.stay(session);
                    case ARROW_UP:
                        interactionState.movePaletteSelection(-1);
                        return DispatchResult.stay(session);
                    case ARROW_DOWN:
                        interactionState.movePaletteSelection(1);
                        return DispatchResult.stay(session);
                    case BACKSPACE:
                        backspaceInputAndRefreshSlashPalette();
                        return DispatchResult.stay(session);
                    case TAB:
                        applySlashSelection();
                        return DispatchResult.stay(session);
                    case ENTER:
                        TuiPaletteItem slashItem = interactionState.getSelectedPaletteItem();
                        if (slashItem != null) {
                            interactionState.replaceInputBufferAndClosePalette(slashItem.getCommand());
                            return DispatchResult.stay(session);
                        }
                        interactionState.closePaletteSilently();
                        String slashInput = interactionState.consumeInputBufferSilently();
                        if (isBlank(slashInput)) {
                            return DispatchResult.stay(session);
                        }
                        return dispatchInteractiveInput(session, slashInput);
                    case CHARACTER:
                        appendInputAndRefreshSlashPalette(keyStroke.getText());
                        return DispatchResult.stay(session);
                    default:
                        return DispatchResult.stay(session);
                }
            }
            switch (keyType) {
                case ESCAPE:
                    interactionState.closePalette();
                    return DispatchResult.stay(session);
                case ARROW_UP:
                    interactionState.movePaletteSelection(-1);
                    return DispatchResult.stay(session);
                case ARROW_DOWN:
                    interactionState.movePaletteSelection(1);
                    return DispatchResult.stay(session);
                case BACKSPACE:
                    interactionState.backspacePaletteQuery();
                    return DispatchResult.stay(session);
                case ENTER:
                    TuiPaletteItem item = interactionState.getSelectedPaletteItem();
                    interactionState.closePalette();
                    return item == null ? DispatchResult.stay(session) : dispatchInteractiveInput(session, item.getCommand());
                case CHARACTER:
                    interactionState.appendPaletteQuery(keyStroke.getText());
                    return DispatchResult.stay(session);
                default:
                    return DispatchResult.stay(session);
            }
        }

        if (keyType == TuiKeyType.CTRL_P) {
            interactionState.openPalette(buildPaletteItems(session));
            return DispatchResult.stay(session);
        }
        if (keyType == TuiKeyType.CTRL_R) {
            if (interactionState.isReplayViewerOpen()) {
                interactionState.closeReplayViewer();
            } else {
                openReplayViewer(session, DEFAULT_REPLAY_LIMIT);
            }
            return DispatchResult.stay(session);
        }
        if (keyType == TuiKeyType.CTRL_L) {
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (interactionState.isTeamBoardOpen()) {
            return handleTeamBoardKey(session, keyType);
        }
        if (interactionState.isReplayViewerOpen()) {
            return handleReplayViewerKey(session, keyType);
        }
        if (interactionState.isProcessInspectorOpen()) {
            return handleProcessInspectorKey(session, keyStroke);
        }

        switch (keyType) {
            case TAB:
                return DispatchResult.stay(session);
            case ARROW_UP:
                interactionState.moveTranscriptScroll(1);
                return DispatchResult.stay(session);
            case ARROW_DOWN:
                interactionState.moveTranscriptScroll(-1);
                return DispatchResult.stay(session);
            case ESCAPE:
                interactionState.clearInputBuffer();
                closeSlashPaletteIfNeeded();
                return DispatchResult.stay(session);
            case BACKSPACE:
                backspaceInputAndRefreshSlashPalette();
                return DispatchResult.stay(session);
            case ENTER:
                if (interactionState.getFocusedPanel() == io.github.lnyocly.ai4j.tui.TuiPanelId.PROCESSES) {
                    String processId = firstNonBlank(interactionState.getSelectedProcessId(), firstProcessId(session));
                    if (!isBlank(processId)) {
                        openProcessInspector(session, processId);
                        return DispatchResult.stay(session);
                    }
                }
                String input = interactionState.consumeInputBufferSilently();
                closeSlashPaletteIfNeededSilently();
                if (isBlank(input)) {
                    return DispatchResult.stay(session);
                }
                return dispatchInteractiveInput(session, input);
            case CHARACTER:
                appendInputAndRefreshSlashPalette(keyStroke.getText());
                return DispatchResult.stay(session);
            default:
                return DispatchResult.stay(session);
        }
    }

    private DispatchResult handleActiveTuiTurnKey(ManagedCodingSession session, TuiKeyStroke keyStroke) {
        if (keyStroke == null || keyStroke.getType() == null) {
            return DispatchResult.stay(session);
        }
        if (keyStroke.getType() == TuiKeyType.ESCAPE) {
            interruptActiveTuiTurn(session);
        }
        return DispatchResult.stay(session);
    }

    private DispatchResult handleReplayViewerKey(ManagedCodingSession session, TuiKeyType keyType) {
        switch (keyType) {
            case ESCAPE:
                interactionState.closeReplayViewer();
                return DispatchResult.stay(session);
            case ARROW_UP:
                interactionState.moveReplayScroll(-1);
                return DispatchResult.stay(session);
            case ARROW_DOWN:
                interactionState.moveReplayScroll(1);
                return DispatchResult.stay(session);
            default:
                return DispatchResult.stay(session);
        }
    }

    private DispatchResult handleTeamBoardKey(ManagedCodingSession session, TuiKeyType keyType) {
        switch (keyType) {
            case ESCAPE:
                interactionState.closeTeamBoard();
                return DispatchResult.stay(session);
            case ARROW_UP:
                interactionState.moveTeamBoardScroll(-1);
                return DispatchResult.stay(session);
            case ARROW_DOWN:
                interactionState.moveTeamBoardScroll(1);
                return DispatchResult.stay(session);
            default:
                return DispatchResult.stay(session);
        }
    }

    private void appendInputAndRefreshSlashPalette(String text) {
        interactionState.appendInputAndSyncSlashPalette(text, buildCommandPaletteItems());
    }

    private void backspaceInputAndRefreshSlashPalette() {
        interactionState.backspaceInputAndSyncSlashPalette(buildCommandPaletteItems());
    }

    private void refreshSlashPalette() {
        interactionState.syncSlashPalette(buildCommandPaletteItems());
    }

    private void closeSlashPaletteIfNeeded() {
        if (interactionState.isPaletteOpen()
                && interactionState.getPaletteMode() == TuiInteractionState.PaletteMode.SLASH) {
            interactionState.closePalette();
        }
    }

    private void closeSlashPaletteIfNeededSilently() {
        if (interactionState.isPaletteOpen()
                && interactionState.getPaletteMode() == TuiInteractionState.PaletteMode.SLASH) {
            interactionState.closePaletteSilently();
        }
    }

    private void applySlashSelection() {
        TuiPaletteItem item = interactionState.getSelectedPaletteItem();
        if (item == null) {
            return;
        }
        interactionState.replaceInputBufferAndClosePalette(item.getCommand());
    }

    private DispatchResult handleProcessInspectorKey(ManagedCodingSession session, TuiKeyStroke keyStroke) {
        TuiKeyType keyType = keyStroke == null ? null : keyStroke.getType();
        switch (keyType) {
            case ESCAPE:
                interactionState.closeProcessInspector();
                return DispatchResult.stay(session);
            case ARROW_UP:
                interactionState.selectAdjacentProcess(resolveProcessIds(session), -1);
                return DispatchResult.stay(session);
            case ARROW_DOWN:
                interactionState.selectAdjacentProcess(resolveProcessIds(session), 1);
                return DispatchResult.stay(session);
            case BACKSPACE:
                interactionState.backspaceProcessInput();
                return DispatchResult.stay(session);
            case ENTER:
                writeSelectedProcessInput(session);
                return DispatchResult.stay(session);
            case CHARACTER:
                interactionState.appendProcessInput(keyStroke.getText());
                return DispatchResult.stay(session);
            default:
                return DispatchResult.stay(session);
        }
    }

    private DispatchResult moveFocusedSelection(ManagedCodingSession session, int delta) {
        if (interactionState.getFocusedPanel() == io.github.lnyocly.ai4j.tui.TuiPanelId.PROCESSES) {
            interactionState.selectAdjacentProcess(resolveProcessIds(session), delta);
        }
        return DispatchResult.stay(session);
    }

    private DispatchResult dispatchInteractiveInput(ManagedCodingSession session, String input) throws Exception {
        if (isBlank(input)) {
            return DispatchResult.stay(session);
        }
        interactionState.resetTranscriptScroll();
        String normalized = input.trim();
        if ("/exit".equalsIgnoreCase(normalized) || "/quit".equalsIgnoreCase(normalized)) {
            return DispatchResult.exit(session);
        }
        if ("/help".equalsIgnoreCase(normalized)) {
            printSessionHelp();
            return DispatchResult.stay(session);
        }
        if ("/status".equalsIgnoreCase(normalized)) {
            printStatus(session);
            return DispatchResult.stay(session);
        }
        if ("/session".equalsIgnoreCase(normalized)) {
            printCurrentSession(session);
            return DispatchResult.stay(session);
        }
        if ("/theme".equalsIgnoreCase(normalized)) {
            printThemes();
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/theme ")) {
            applyTheme(extractCommandArgument(normalized), session);
            return DispatchResult.stay(session);
        }
        if ("/save".equalsIgnoreCase(normalized)) {
            persistSession(session, true);
            return DispatchResult.stay(session);
        }
        if ("/providers".equalsIgnoreCase(normalized)) {
            printProviders();
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/provider".equalsIgnoreCase(normalized)) {
            printCurrentProvider();
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/provider ")) {
            ManagedCodingSession switched = handleProviderCommand(session, extractCommandArgument(normalized));
            activeSession = switched;
            renderTui(switched);
            return DispatchResult.stay(switched);
        }
        if ("/model".equalsIgnoreCase(normalized) || normalized.startsWith("/model ")) {
            ManagedCodingSession switched = handleModelCommand(session, extractCommandArgument(normalized));
            activeSession = switched;
            renderTui(switched);
            return DispatchResult.stay(switched);
        }
        if ("/skills".equalsIgnoreCase(normalized) || normalized.startsWith("/skills ")) {
            printSkills(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/agents".equalsIgnoreCase(normalized) || normalized.startsWith("/agents ")) {
            printAgents(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/mcp".equalsIgnoreCase(normalized) || normalized.startsWith("/mcp ")) {
            ManagedCodingSession switched = handleMcpCommand(session, extractCommandArgument(normalized));
            activeSession = switched;
            renderTui(switched);
            return DispatchResult.stay(switched);
        }
        if ("/commands".equalsIgnoreCase(normalized) || "/palette".equalsIgnoreCase(normalized)) {
            printCommands();
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/cmd ")) {
            runCustomCommand(session, extractCommandArgument(normalized));
            return DispatchResult.stay(session);
        }
        if ("/sessions".equalsIgnoreCase(normalized)) {
            printSessions();
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/history".equalsIgnoreCase(normalized) || normalized.startsWith("/history ")) {
            printHistory(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/tree".equalsIgnoreCase(normalized) || normalized.startsWith("/tree ")) {
            printTree(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/events")) {
            printEvents(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/replay")) {
            printReplay(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/team".equalsIgnoreCase(normalized)) {
            printTeamBoard(session);
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/compacts")) {
            printCompacts(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if ("/stream".equalsIgnoreCase(normalized) || normalized.startsWith("/stream ")) {
            ManagedCodingSession switched = handleStreamCommand(session, extractCommandArgument(normalized));
            activeSession = switched;
            renderTui(switched);
            return DispatchResult.stay(switched);
        }
        if ("/processes".equalsIgnoreCase(normalized)) {
            printProcesses(session);
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/process ")) {
            handleProcessCommand(session, extractCommandArgument(normalized));
            renderTui(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/checkpoint")) {
            printCheckpoint(session);
            return DispatchResult.stay(session);
        }
        if ("/clear".equalsIgnoreCase(normalized)) {
            printClearMarker(session);
            return DispatchResult.stay(session);
        }
        if (normalized.startsWith("/resume ") || normalized.startsWith("/load ")) {
            ManagedCodingSession resumed = resumeSession(session, extractCommandArgument(normalized));
            activeSession = resumed;
            return DispatchResult.stay(resumed);
        }
        if ("/fork".equalsIgnoreCase(normalized) || normalized.startsWith("/fork ")) {
            ManagedCodingSession forked = forkSession(session, extractCommandArgument(normalized));
            activeSession = forked;
            return DispatchResult.stay(forked);
        }
        if (normalized.startsWith("/compact")) {
            String turnId = newTurnId();
            CodingSessionCompactResult result = resolveCompact(session.getSession(), normalized);
            printCompactResult(result);
            appendCompactEvent(session, result, turnId);
            persistSession(session, false);
            renderTui(session);
            return DispatchResult.stay(session);
        }

        runAgentTurn(session, input);
        return DispatchResult.stay(session);
    }

    private void runTurn(ManagedCodingSession session, String input) throws Exception {
        runTurn(session, input, null, newTurnId());
    }

    private void runTurn(ManagedCodingSession session, String input, ActiveTuiTurn activeTurn) throws Exception {
        runTurn(session, input, activeTurn, activeTurn == null ? newTurnId() : activeTurn.getTurnId());
    }

    private void runTurn(ManagedCodingSession session, String input, ActiveTuiTurn activeTurn, String turnId) throws Exception {
        if (isTurnInterrupted(turnId, activeTurn)) {
            return;
        }
        beginTuiTurn(input);
        if (useMainBufferInteractiveShell()) {
            mainBufferTurnPrinter.beginTurn(input);
        } else {
            startTuiTurnAnimation(session);
        }
        appendEvent(session, SessionEventType.USER_MESSAGE, turnId, null, clip(input, 200), payloadOf(
                "input", clip(input, options.isVerbose() ? 4000 : 1200)
        ));
        renderTuiIfEnabled(session);

        CliAgentListener listener = new CliAgentListener(session, turnId, activeTurn);
        try {
            CodingAgentResult result = session.getSession().runStream(CodingAgentRequest.builder().input(input).build(), listener);
            if (activeTurn != null && activeTurn.isInterrupted()) {
                return;
            }
            if (isMainBufferTurnInterrupted(turnId)) {
                handleMainBufferTurnInterrupted(session, turnId);
                return;
            }
            listener.flushFinalOutput();
            if (activeTurn != null && activeTurn.isInterrupted()) {
                return;
            }
            if (isMainBufferTurnInterrupted(turnId)) {
                handleMainBufferTurnInterrupted(session, turnId);
                return;
            }
            appendLoopDecisionEvents(session, turnId, result);
            printAutoCompactOutcome(session, turnId);
            renderTui(session);
        } catch (Exception ex) {
            if (activeTurn != null && activeTurn.isInterrupted()) {
                return;
            }
            if (isMainBufferTurnInterrupted(turnId)) {
                handleMainBufferTurnInterrupted(session, turnId);
                return;
            }
            tuiLiveTurnState.onError(null, safeMessage(ex));
            renderTuiIfEnabled(session);
            appendEvent(session, SessionEventType.ERROR, turnId, null, safeMessage(ex), payloadOf(
                    "error", safeMessage(ex)
            ));
            throw ex;
        } finally {
            listener.close();
            try {
                stopTuiTurnAnimation();
            } finally {
                mainBufferTurnPrinter.finishTurn();
            }
        }
    }

    private void runAgentTurn(ManagedCodingSession session, String input) throws Exception {
        if (useMainBufferInteractiveShell()) {
            runMainBufferTurn(session, input);
            persistSession(session, false);
            return;
        }
        if (!shouldRunTurnsAsync()) {
            runTurn(session, input);
            persistSession(session, false);
            return;
        }
        startAsyncTuiTurn(session, input);
    }

    private void printSessionHelp() {
        StringBuilder builder = new StringBuilder();
        builder.append("In-session commands:\n");
        builder.append("  /help    Show help\n");
        builder.append("  /status  Show current session status\n");
        builder.append("  /session Show current session metadata\n");
        builder.append("  /theme [name]  Show or switch the active TUI theme\n");
        builder.append("  /save    Persist the current session state\n");
        builder.append("  /providers  List saved provider profiles\n");
        builder.append("  /provider  Show current provider/profile state\n");
        builder.append("  /provider use <name>  Switch workspace to a saved provider profile\n");
        builder.append("  /provider save <name>  Save the current runtime as a provider profile\n");
        builder.append("  /provider add <name> [options]  Create a provider profile from explicit fields\n");
        builder.append("  /provider edit <name> [options]  Update a saved provider profile\n");
        builder.append("  /provider default <name|clear>  Set or clear the global default profile\n");
        builder.append("  /provider remove <name>  Delete a saved provider profile\n");
        builder.append("  /model  Show the current effective model and override state\n");
        builder.append("  /model <name>  Save a workspace model override and switch immediately\n");
        builder.append("  /model reset  Clear the workspace model override\n");
        builder.append("  /skills [name]  List discovered coding skills or inspect one skill in detail\n");
        builder.append("  /agents [name]  List available coding agents or inspect one worker definition\n");
        builder.append("  /mcp  Show current MCP services and status\n");
        builder.append("  /mcp add --transport <stdio|sse|http> <name> <target>  Add a global MCP service\n");
        builder.append("  /mcp enable|disable <name>  Toggle workspace MCP enablement\n");
        builder.append("  /mcp pause|resume <name>  Toggle current session MCP activation\n");
        builder.append("  /mcp retry <name>  Reconnect an enabled MCP service\n");
        builder.append("  /mcp remove <name>  Delete a global MCP service\n");
        builder.append("  /commands  List available custom commands\n");
        builder.append("  /palette  Alias of /commands\n");
        builder.append("  /cmd <name> [args]  Run a custom command template\n");
        builder.append("  /sessions  List saved sessions\n");
        builder.append("  /history [id]  Show session lineage from root to target\n");
        builder.append("  /tree [id]  Show the current session tree\n");
        builder.append("  /events [n]  Show the latest session ledger events\n");
        builder.append("  /replay [n]  Replay recent turns grouped from the event ledger\n");
        builder.append("  /team    Show the current agent team board grouped by member lane\n");
        builder.append("  /compacts [n]  Show recent compact history from the event ledger\n");
        builder.append("  /stream [on|off]  Show or switch model request streaming\n");
        builder.append("  /processes  List active and restored process metadata\n");
        builder.append("  /process status <id>  Show metadata for one process\n");
        builder.append("  /process follow <id> [limit]  Show process metadata with buffered logs\n");
        builder.append("  /process logs <id> [limit]  Read buffered logs for a process\n");
        builder.append("  /process write <id> <text>  Write text to a live process stdin\n");
        builder.append("  /process stop <id>  Stop a live process\n");
        builder.append("  /checkpoint  Show the current structured checkpoint summary\n");
        builder.append("  /resume <id>  Resume a saved session\n");
        builder.append("  /load <id>    Alias of /resume\n");
        builder.append("  /fork [new-id] or /fork <source-id> <new-id>  Fork a session branch\n");
        builder.append("  /compact [summary]  Compact current session memory\n");
        builder.append("  /clear   Print a new screen section\n");
        builder.append("  /exit    Exit the session\n");
        builder.append("  /quit    Exit the session\n");
        builder.append("TUI keys: / opens command list, Tab accepts completion, Ctrl+P palette, Ctrl+R replay, /team opens the team board, Enter submit, Esc interrupts an active raw-TUI turn or clears input.\n");
        builder.append("Type natural language instructions to drive the coding agent.\n");
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            setTuiAssistantOutput(builder.toString().trim());
            return;
        }
        terminal.println(builder.toString());
    }

    private void printSessionHeader(ManagedCodingSession session) {
        if (options.getUiMode() == CliUiMode.TUI) {
            if (useMainBufferInteractiveShell()) {
                refreshSessionContext(session);
                String model = session == null ? "model" : firstNonBlank(session.getModel(), "model");
                String workspace = session == null ? "." : lastPathSegment(firstNonBlank(session.getWorkspace(), "."));
                terminal.println("AI4J  " + clip(model, 28) + "  " + clip(workspace, 32));
                terminal.println("");
                return;
            }
            renderTui(session);
            return;
        }

        terminal.println("ai4j-cli code");
        terminal.println("session=" + session.getSessionId());
        terminal.println("provider=" + session.getProvider()
                + ", protocol=" + session.getProtocol()
                + ", model=" + session.getModel());
        terminal.println("workspace=" + session.getWorkspace());
        terminal.println("mode=" + (options.isNoSession() ? "memory-only" : "persistent")
                + ", root=" + clip(session.getRootSessionId(), 64)
                + ", parent=" + clip(session.getParentSessionId(), 64));
        terminal.println("store=" + sessionManager.getDirectory());
    }

    private void printStatus(ManagedCodingSession session) {
        CodingSessionSnapshot snapshot = session == null || session.getSession() == null ? null : session.getSession().snapshot();
        if (useMainBufferInteractiveShell()) {
            emitOutput(renderStatusOutput(session, snapshot));
            return;
        }
        if (useAppendOnlyTranscriptTui()) {
            emitOutput(renderStatusOutput(session, snapshot));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            renderTui(session);
            return;
        }
        terminal.println("status: session=" + session.getSessionId()
                + ", provider=" + session.getProvider()
                + ", protocol=" + session.getProtocol()
                + ", model=" + session.getModel()
                + ", workspace=" + session.getWorkspace()
                + ", mode=" + (options.isNoSession() ? "memory-only" : "persistent")
                + ", memory=" + (snapshot == null ? 0 : snapshot.getMemoryItemCount())
                + ", activeProcesses=" + (snapshot == null ? 0 : snapshot.getActiveProcessCount())
                + ", restoredProcesses=" + (snapshot == null ? 0 : snapshot.getRestoredProcessCount())
                + ", tokens=" + (snapshot == null ? 0 : snapshot.getEstimatedContextTokens())
                + ", checkpointGoal=" + clip(snapshot == null ? null : snapshot.getCheckpointGoal(), 80)
                + ", compact=" + firstNonBlank(snapshot == null ? null : snapshot.getLastCompactMode(), "none"));
    }

    private void printCurrentSession(ManagedCodingSession session) {
        if (useMainBufferInteractiveShell()) {
            emitOutput(renderSessionOutput(session));
            return;
        }
        if (useAppendOnlyTranscriptTui()) {
            emitOutput(renderSessionOutput(session));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            renderTui(session);
            return;
        }
        CodingSessionDescriptor descriptor = session == null ? null : session.toDescriptor();
        if (descriptor == null) {
            terminal.println("session: (none)");
            return;
        }
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(null, null, null, null, null, env, properties);
        CodingSessionSnapshot snapshot = session.getSession() == null ? null : session.getSession().snapshot();
        terminal.println(renderPanel("session",
                "id        : " + descriptor.getSessionId(),
                "root      : " + descriptor.getRootSessionId(),
                "parent    : " + firstNonBlank(descriptor.getParentSessionId(), "(none)"),
                "provider  : " + descriptor.getProvider(),
                "protocol  : " + descriptor.getProtocol(),
                "model     : " + descriptor.getModel(),
                "profile   : " + firstNonBlank(resolved.getActiveProfile(), resolved.getEffectiveProfile(), "(none)"),
                "override  : " + firstNonBlank(resolved.getModelOverride(), "(none)"),
                "workspace : " + descriptor.getWorkspace(),
                "mode      : " + (options.isNoSession() ? "memory-only" : "persistent"),
                "created   : " + formatTimestamp(descriptor.getCreatedAtEpochMs()),
                "updated   : " + formatTimestamp(descriptor.getUpdatedAtEpochMs()),
                "memory    : " + descriptor.getMemoryItemCount(),
                "processes : " + descriptor.getProcessCount()
                        + " (active=" + descriptor.getActiveProcessCount()
                        + ", restored=" + descriptor.getRestoredProcessCount() + ")",
                "tokens    : " + (snapshot == null ? 0 : snapshot.getEstimatedContextTokens()),
                "checkpoint: " + clip(snapshot == null ? null : snapshot.getCheckpointGoal(), 220),
                "compact   : " + firstNonBlank(snapshot == null ? null : snapshot.getLastCompactMode(), "none")
                        + " " + (snapshot == null ? "" : snapshot.getLastCompactTokensBefore() + "->" + snapshot.getLastCompactTokensAfter()),
                "summary   : " + clip(descriptor.getSummary(), 220)
        ));
    }

    private void printThemes() {
        List<String> themes = tuiConfigManager.listThemeNames();
        String currentTheme = tuiRenderer == null ? options.getTheme() : tuiRenderer.getThemeName();
        if (useMainBufferInteractiveShell()) {
            StringBuilder builder = new StringBuilder("themes:\n");
            for (String themeName : themes) {
                builder.append("- ").append(themeName);
                if (themeName.equalsIgnoreCase(currentTheme)) {
                    builder.append(" (active)");
                }
                builder.append('\n');
            }
            emitOutput(builder.toString().trim());
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            if (tuiRenderer != null) {
                StringBuilder builder = new StringBuilder();
                builder.append("Available themes:\n");
                for (String themeName : themes) {
                    builder.append("- ").append(themeName);
                    if (themeName.equalsIgnoreCase(currentTheme)) {
                        builder.append("  (active)");
                    }
                    builder.append('\n');
                }
                setTuiAssistantOutput(builder.toString().trim());
            }
            return;
        }
        terminal.println("themes:");
        for (String themeName : themes) {
            terminal.println("  " + themeName + (themeName.equalsIgnoreCase(currentTheme) ? "  *" : ""));
        }
    }

    private void printProviders() {
        emitOutput(renderProvidersOutput());
    }

    private void printCurrentProvider() {
        emitOutput(renderCurrentProviderOutput());
    }

    private void printSkills(ManagedCodingSession session, String argument) {
        emitOutput(renderSkillsOutput(session, argument));
    }

    private void printAgents(ManagedCodingSession session, String argument) {
        emitOutput(renderAgentsOutput(session, argument));
    }

    private ManagedCodingSession handleProviderCommand(ManagedCodingSession session, String argument) throws Exception {
        if (isBlank(argument)) {
            printCurrentProvider();
            return session;
        }
        String trimmed = argument.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String action = parts[0].toLowerCase(Locale.ROOT);
        String value = parts.length > 1 ? parts[1].trim() : null;
        if ("use".equals(action)) {
            return switchToProviderProfile(session, value);
        }
        if ("save".equals(action)) {
            saveCurrentProviderProfile(value);
            return session;
        }
        if ("add".equals(action)) {
            addProviderProfile(value);
            return session;
        }
        if ("edit".equals(action)) {
            return editProviderProfile(session, value);
        }
        if ("default".equals(action)) {
            setDefaultProviderProfile(value);
            return session;
        }
        if ("remove".equals(action)) {
            removeProviderProfile(value);
            return session;
        }
        emitError("Unknown /provider action: " + action + ". Use /provider, /providers, /provider use <name>, /provider save <name>, /provider add <name> ..., /provider edit <name> ..., /provider default <name|clear>, or /provider remove <name>.");
        return session;
    }

    private ManagedCodingSession handleModelCommand(ManagedCodingSession session, String argument) throws Exception {
        if (isBlank(argument)) {
            emitOutput(renderModelOutput());
            return session;
        }
        String normalized = argument.trim();
        CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
        if ("reset".equalsIgnoreCase(normalized)) {
            workspaceConfig.setModelOverride(null);
            providerConfigManager.saveWorkspaceConfig(workspaceConfig);
            CodeCommandOptions nextOptions = resolveConfiguredRuntimeOptions();
            ManagedCodingSession rebound = switchSessionRuntime(session, nextOptions);
            emitOutput(renderModelOutput());
            persistSession(rebound, false);
            return rebound;
        }
        workspaceConfig.setModelOverride(normalized);
        providerConfigManager.saveWorkspaceConfig(workspaceConfig);
        CodeCommandOptions nextOptions = resolveConfiguredRuntimeOptions();
        ManagedCodingSession rebound = switchSessionRuntime(session, nextOptions);
        emitOutput(renderModelOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private ManagedCodingSession handleMcpCommand(ManagedCodingSession session, String argument) throws Exception {
        if (isBlank(argument)) {
            emitOutput(renderMcpOutput());
            return session;
        }
        String trimmed = argument.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String action = parts[0].toLowerCase(Locale.ROOT);
        String value = parts.length > 1 ? parts[1].trim() : null;
        if ("list".equals(action)) {
            emitOutput(renderMcpOutput());
            return session;
        }
        if ("add".equals(action)) {
            return addMcpServer(session, value);
        }
        if ("remove".equals(action) || "delete".equals(action)) {
            return removeMcpServer(session, value);
        }
        if ("enable".equals(action)) {
            return enableMcpServer(session, value);
        }
        if ("disable".equals(action)) {
            return disableMcpServer(session, value);
        }
        if ("pause".equals(action)) {
            return pauseMcpServer(session, value);
        }
        if ("resume".equals(action)) {
            return resumeMcpServer(session, value);
        }
        if ("retry".equals(action)) {
            return retryMcpServer(session, value);
        }
        emitError("Unknown /mcp action: " + action + ". Use /mcp, /mcp list, /mcp add --transport <stdio|sse|http> <name> <target>, /mcp enable <name>, /mcp disable <name>, /mcp pause <name>, /mcp resume <name>, /mcp retry <name>, or /mcp remove <name>.");
        return session;
    }

    private ManagedCodingSession addMcpServer(ManagedCodingSession session, String rawArguments) throws Exception {
        McpAddCommand command = parseMcpAddCommand(rawArguments);
        if (command == null) {
            return session;
        }
        CliMcpConfig globalConfig = mcpConfigManager.loadGlobalConfig();
        if (globalConfig.getMcpServers().containsKey(command.name)) {
            emitError("MCP server already exists: " + command.name);
            return session;
        }
        globalConfig.getMcpServers().put(command.name, command.definition);
        mcpConfigManager.saveGlobalConfig(globalConfig);
        emitOutput("mcp added: " + command.name + " -> " + mcpConfigManager.globalMcpPath());

        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        if (containsIgnoreCase(workspaceConfig.getEnabledMcpServers(), command.name)) {
            ManagedCodingSession rebound = switchSessionRuntime(session, options);
            emitOutput(renderMcpOutput());
            persistSession(rebound, false);
            return rebound;
        }
        return session;
    }

    private ManagedCodingSession removeMcpServer(ManagedCodingSession session, String name) throws Exception {
        if (isBlank(name)) {
            emitError("Usage: /mcp remove <name>");
            return session;
        }
        String normalizedName = name.trim();
        CliMcpConfig globalConfig = mcpConfigManager.loadGlobalConfig();
        if (globalConfig.getMcpServers().remove(normalizedName) == null) {
            emitError("Unknown MCP server: " + normalizedName);
            return session;
        }
        mcpConfigManager.saveGlobalConfig(globalConfig);

        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        workspaceConfig.setEnabledMcpServers(removeName(workspaceConfig.getEnabledMcpServers(), normalizedName));
        mcpConfigManager.saveWorkspaceConfig(workspaceConfig);
        pausedMcpServers.remove(normalizedName);

        ManagedCodingSession rebound = switchSessionRuntime(session, options);
        emitOutput("mcp removed: " + normalizedName);
        emitOutput(renderMcpOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private ManagedCodingSession enableMcpServer(ManagedCodingSession session, String name) throws Exception {
        if (isBlank(name)) {
            emitError("Usage: /mcp enable <name>");
            return session;
        }
        String normalizedName = name.trim();
        if (!mcpConfigManager.loadGlobalConfig().getMcpServers().containsKey(normalizedName)) {
            emitError("Unknown MCP server: " + normalizedName);
            return session;
        }
        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        workspaceConfig.setEnabledMcpServers(addName(workspaceConfig.getEnabledMcpServers(), normalizedName));
        mcpConfigManager.saveWorkspaceConfig(workspaceConfig);

        ManagedCodingSession rebound = switchSessionRuntime(session, options);
        emitOutput(renderMcpOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private ManagedCodingSession disableMcpServer(ManagedCodingSession session, String name) throws Exception {
        if (isBlank(name)) {
            emitError("Usage: /mcp disable <name>");
            return session;
        }
        String normalizedName = name.trim();
        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        if (!containsIgnoreCase(workspaceConfig.getEnabledMcpServers(), normalizedName)) {
            emitError("MCP server is not enabled in this workspace: " + normalizedName);
            return session;
        }
        workspaceConfig.setEnabledMcpServers(removeName(workspaceConfig.getEnabledMcpServers(), normalizedName));
        mcpConfigManager.saveWorkspaceConfig(workspaceConfig);
        pausedMcpServers.remove(normalizedName);

        ManagedCodingSession rebound = switchSessionRuntime(session, options);
        emitOutput(renderMcpOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private ManagedCodingSession pauseMcpServer(ManagedCodingSession session, String name) throws Exception {
        if (isBlank(name)) {
            emitError("Usage: /mcp pause <name>");
            return session;
        }
        String normalizedName = name.trim();
        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        if (!containsIgnoreCase(workspaceConfig.getEnabledMcpServers(), normalizedName)) {
            emitError("MCP server is not enabled in this workspace: " + normalizedName);
            return session;
        }
        if (!pausedMcpServers.add(normalizedName)) {
            emitOutput("mcp already paused: " + normalizedName);
            return session;
        }
        ManagedCodingSession rebound = switchSessionRuntime(session, options);
        emitOutput(renderMcpOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private ManagedCodingSession resumeMcpServer(ManagedCodingSession session, String name) throws Exception {
        if (isBlank(name)) {
            emitError("Usage: /mcp resume <name>");
            return session;
        }
        String normalizedName = name.trim();
        if (!pausedMcpServers.remove(normalizedName)) {
            emitError("MCP server is not paused in this session: " + normalizedName);
            return session;
        }
        ManagedCodingSession rebound = switchSessionRuntime(session, options);
        emitOutput(renderMcpOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private ManagedCodingSession retryMcpServer(ManagedCodingSession session, String name) throws Exception {
        if (isBlank(name)) {
            emitError("Usage: /mcp retry <name>");
            return session;
        }
        String normalizedName = name.trim();
        if (!containsMcpServer(normalizedName)) {
            emitError("Unknown MCP server: " + normalizedName);
            return session;
        }
        ManagedCodingSession rebound = switchSessionRuntime(session, options);
        emitOutput(renderMcpOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private McpAddCommand parseMcpAddCommand(String rawArguments) {
        if (isBlank(rawArguments)) {
            emitError("Usage: /mcp add --transport <stdio|sse|http> <name> <target>");
            return null;
        }
        List<String> tokens = splitWhitespace(rawArguments);
        if (tokens.size() < 4 || !"--transport".equalsIgnoreCase(tokens.get(0))) {
            emitError("Usage: /mcp add --transport <stdio|sse|http> <name> <target>");
            return null;
        }
        String transport = normalizeMcpTransport(tokens.get(1));
        if (transport == null) {
            emitError("Unsupported MCP transport: " + tokens.get(1) + ". Use stdio, sse, or http.");
            return null;
        }
        String name = tokens.get(2).trim();
        if (isBlank(name)) {
            emitError("Usage: /mcp add --transport <stdio|sse|http> <name> <target>");
            return null;
        }

        CliMcpServerDefinition definition = new CliMcpServerDefinition();
        definition.setType(transport);
        if ("stdio".equals(transport)) {
            if (tokens.size() < 4) {
                emitError("Usage: /mcp add --transport stdio <name> <command> [args...]");
                return null;
            }
            definition.setCommand(tokens.get(3));
            if (tokens.size() > 4) {
                definition.setArgs(new ArrayList<String>(tokens.subList(4, tokens.size())));
            }
        } else {
            if (tokens.size() != 4) {
                emitError("Usage: /mcp add --transport " + tokens.get(1) + " <name> <url>");
                return null;
            }
            definition.setUrl(tokens.get(3));
        }
        return new McpAddCommand(name, definition);
    }

    private String renderMcpOutput() {
        CliResolvedMcpConfig resolvedConfig = mcpConfigManager.resolve(pausedMcpServers);
        List<CliMcpStatusSnapshot> statuses = mcpRuntimeManager != null && mcpRuntimeManager.hasStatuses()
                ? mcpRuntimeManager.getStatuses()
                : deriveMcpStatuses(resolvedConfig);
        if ((statuses == null || statuses.isEmpty()) && resolvedConfig.getServers().isEmpty()) {
            return "mcp: (none)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("mcp:\n");
        for (CliMcpStatusSnapshot status : statuses) {
            if (status == null) {
                continue;
            }
            builder.append("- ").append(status.getServerName())
                    .append(" | type=").append(firstNonBlank(status.getTransportType(), "(unknown)"))
                    .append(" | state=").append(firstNonBlank(status.getState(), "(unknown)"))
                    .append(" | workspace=").append(status.isWorkspaceEnabled() ? "enabled" : "disabled")
                    .append(" | paused=").append(status.isSessionPaused() ? "yes" : "no")
                    .append(" | tools=").append(status.getToolCount());
            if (!isBlank(status.getErrorSummary())) {
                builder.append(" | error=").append(clip(status.getErrorSummary(), 120));
            }
            builder.append('\n');
        }
        builder.append("- store=").append(mcpConfigManager.globalMcpPath()).append('\n');
        builder.append("- workspaceConfig=").append(mcpConfigManager.workspaceConfigPath());
        return builder.toString().trim();
    }

    private List<CliMcpStatusSnapshot> deriveMcpStatuses(CliResolvedMcpConfig resolvedConfig) {
        List<CliMcpStatusSnapshot> statuses = new ArrayList<CliMcpStatusSnapshot>();
        if (resolvedConfig == null) {
            return statuses;
        }
        for (CliResolvedMcpServer server : resolvedConfig.getServers().values()) {
            String state = server.isWorkspaceEnabled()
                    ? (server.isSessionPaused()
                    ? CliMcpRuntimeManager.STATE_PAUSED
                    : (server.isValid() ? "configured" : CliMcpRuntimeManager.STATE_ERROR))
                    : CliMcpRuntimeManager.STATE_DISABLED;
            statuses.add(new CliMcpStatusSnapshot(
                    server.getName(),
                    server.getTransportType(),
                    state,
                    0,
                    server.getValidationError(),
                    server.isWorkspaceEnabled(),
                    server.isSessionPaused()
            ));
        }
        for (String missing : resolvedConfig.getUnknownEnabledServerNames()) {
            statuses.add(new CliMcpStatusSnapshot(
                    missing,
                    null,
                    CliMcpRuntimeManager.STATE_MISSING,
                    0,
                    "workspace references undefined MCP server",
                    true,
                    false
            ));
        }
        return statuses;
    }

    private ManagedCodingSession switchToProviderProfile(ManagedCodingSession session, String profileName) throws Exception {
        if (isBlank(profileName)) {
            emitError("Usage: /provider use <profile-name>");
            return session;
        }
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        String normalizedName = profileName.trim();
        if (!providersConfig.getProfiles().containsKey(normalizedName)) {
            emitError("Unknown provider profile: " + normalizedName);
            return session;
        }
        CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
        workspaceConfig.setActiveProfile(normalizedName);
        providerConfigManager.saveWorkspaceConfig(workspaceConfig);
        CodeCommandOptions nextOptions = resolveConfiguredRuntimeOptions();
        ManagedCodingSession rebound = switchSessionRuntime(session, nextOptions);
        emitOutput(renderCurrentProviderOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private void saveCurrentProviderProfile(String profileName) throws IOException {
        if (isBlank(profileName)) {
            emitError("Usage: /provider save <profile-name>");
            return;
        }
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        String normalizedName = profileName.trim();
        providersConfig.getProfiles().put(normalizedName, CliProviderProfile.builder()
                .provider(options.getProvider() == null ? null : options.getProvider().getPlatform())
                .protocol(protocol == null ? null : protocol.getValue())
                .model(options.getModel())
                .baseUrl(options.getBaseUrl())
                .apiKey(options.getApiKey())
                .build());
        if (isBlank(providersConfig.getDefaultProfile())) {
            providersConfig.setDefaultProfile(normalizedName);
        }
        providerConfigManager.saveProvidersConfig(providersConfig);
        emitOutput("provider saved: " + normalizedName + " -> " + providerConfigManager.globalProvidersPath());
    }

    private void addProviderProfile(String rawArguments) throws IOException {
        ProviderProfileMutation mutation = parseProviderProfileMutation(
                rawArguments,
                "Usage: /provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]"
        );
        if (mutation == null) {
            return;
        }
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        if (providersConfig.getProfiles().containsKey(mutation.profileName)) {
            emitError("Provider profile already exists: " + mutation.profileName + ". Use /provider edit " + mutation.profileName + " ...");
            return;
        }
        if (isBlank(mutation.provider)) {
            emitError("Usage: /provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]");
            return;
        }
        PlatformType provider = parseProviderType(mutation.provider);
        if (provider == null) {
            return;
        }
        String baseUrlValue = mutation.clearBaseUrl ? null : mutation.baseUrl;
        CliProtocol protocolValue = parseProviderProtocol(
                firstNonBlank(mutation.protocol, CliProtocol.defaultProtocol(provider, baseUrlValue).getValue())
        );
        if (protocolValue == null) {
            return;
        }
        if (!isSupportedProviderProtocol(provider, protocolValue)) {
            return;
        }

        providersConfig.getProfiles().put(mutation.profileName, CliProviderProfile.builder()
                .provider(provider.getPlatform())
                .protocol(protocolValue.getValue())
                .model(mutation.clearModel ? null : mutation.model)
                .baseUrl(mutation.clearBaseUrl ? null : mutation.baseUrl)
                .apiKey(mutation.clearApiKey ? null : mutation.apiKey)
                .build());
        if (isBlank(providersConfig.getDefaultProfile())) {
            providersConfig.setDefaultProfile(mutation.profileName);
        }
        providerConfigManager.saveProvidersConfig(providersConfig);
        emitOutput("provider added: " + mutation.profileName + " -> " + providerConfigManager.globalProvidersPath());
    }

    private ManagedCodingSession editProviderProfile(ManagedCodingSession session, String rawArguments) throws Exception {
        ProviderProfileMutation mutation = parseProviderProfileMutation(
                rawArguments,
                "Usage: /provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]"
        );
        if (mutation == null) {
            return session;
        }
        if (!mutation.hasAnyFieldChanges()) {
            emitError("Usage: /provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]");
            return session;
        }
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        CliProviderProfile existing = providersConfig.getProfiles().get(mutation.profileName);
        if (existing == null) {
            emitError("Unknown provider profile: " + mutation.profileName);
            return session;
        }

        PlatformType provider = parseProviderType(firstNonBlank(mutation.provider, existing.getProvider()));
        if (provider == null) {
            return session;
        }
        String baseUrlValue = mutation.clearBaseUrl
                ? null
                : firstNonBlank(mutation.baseUrl, existing.getBaseUrl());
        String protocolRaw = mutation.protocol;
        if (isBlank(protocolRaw)) {
            protocolRaw = firstNonBlank(
                    normalizeStoredProtocol(existing.getProtocol(), provider, baseUrlValue),
                    CliProtocol.defaultProtocol(provider, baseUrlValue).getValue()
            );
        }
        CliProtocol protocolValue = parseProviderProtocol(protocolRaw);
        if (protocolValue == null) {
            return session;
        }
        if (!isSupportedProviderProtocol(provider, protocolValue)) {
            return session;
        }

        existing.setProvider(provider.getPlatform());
        existing.setProtocol(protocolValue.getValue());
        if (mutation.clearModel) {
            existing.setModel(null);
        } else if (mutation.model != null) {
            existing.setModel(mutation.model);
        }
        if (mutation.clearBaseUrl) {
            existing.setBaseUrl(null);
        } else if (mutation.baseUrl != null) {
            existing.setBaseUrl(mutation.baseUrl);
        }
        if (mutation.clearApiKey) {
            existing.setApiKey(null);
        } else if (mutation.apiKey != null) {
            existing.setApiKey(mutation.apiKey);
        }

        String effectiveProfileBeforeEdit = providerConfigManager.resolve(null, null, null, null, null, env, properties).getEffectiveProfile();
        providersConfig.getProfiles().put(mutation.profileName, existing);
        providerConfigManager.saveProvidersConfig(providersConfig);
        emitOutput("provider updated: " + mutation.profileName + " -> " + providerConfigManager.globalProvidersPath());
        if (!mutation.profileName.equals(effectiveProfileBeforeEdit)) {
            return session;
        }
        CodeCommandOptions nextOptions = resolveConfiguredRuntimeOptions();
        ManagedCodingSession rebound = switchSessionRuntime(session, nextOptions);
        emitOutput(renderCurrentProviderOutput());
        persistSession(rebound, false);
        return rebound;
    }

    private void removeProviderProfile(String profileName) throws IOException {
        if (isBlank(profileName)) {
            emitError("Usage: /provider remove <profile-name>");
            return;
        }
        String normalizedName = profileName.trim();
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        if (providersConfig.getProfiles().remove(normalizedName) == null) {
            emitError("Unknown provider profile: " + normalizedName);
            return;
        }
        if (normalizedName.equals(providersConfig.getDefaultProfile())) {
            providersConfig.setDefaultProfile(null);
        }
        providerConfigManager.saveProvidersConfig(providersConfig);
        CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
        if (normalizedName.equals(workspaceConfig.getActiveProfile())) {
            workspaceConfig.setActiveProfile(null);
            providerConfigManager.saveWorkspaceConfig(workspaceConfig);
        }
        emitOutput("provider removed: " + normalizedName);
    }

    private void setDefaultProviderProfile(String profileName) throws IOException {
        if (isBlank(profileName)) {
            emitError("Usage: /provider default <profile-name|clear>");
            return;
        }
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        String normalizedName = profileName.trim();
        if ("clear".equalsIgnoreCase(normalizedName)) {
            providersConfig.setDefaultProfile(null);
            providerConfigManager.saveProvidersConfig(providersConfig);
            emitOutput("provider default cleared");
            return;
        }
        if (!providersConfig.getProfiles().containsKey(normalizedName)) {
            emitError("Unknown provider profile: " + normalizedName);
            return;
        }
        providersConfig.setDefaultProfile(normalizedName);
        providerConfigManager.saveProvidersConfig(providersConfig);
        emitOutput("provider default: " + normalizedName);
    }

    private ProviderProfileMutation parseProviderProfileMutation(String rawArguments, String usage) {
        if (isBlank(rawArguments)) {
            emitError(usage);
            return null;
        }
        String[] tokens = rawArguments.trim().split("\\s+");
        if (tokens.length == 0 || isBlank(tokens[0])) {
            emitError(usage);
            return null;
        }
        ProviderProfileMutation mutation = new ProviderProfileMutation(tokens[0].trim());
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if ("--provider".equalsIgnoreCase(token)) {
                String value = requireProviderMutationValue(tokens, ++i, token, usage);
                if (value == null) {
                    return null;
                }
                mutation.provider = value;
                continue;
            }
            if ("--protocol".equalsIgnoreCase(token)) {
                String value = requireProviderMutationValue(tokens, ++i, token, usage);
                if (value == null) {
                    return null;
                }
                mutation.protocol = value;
                continue;
            }
            if ("--model".equalsIgnoreCase(token)) {
                String value = requireProviderMutationValue(tokens, ++i, token, usage);
                if (value == null) {
                    return null;
                }
                mutation.model = value;
                mutation.clearModel = false;
                continue;
            }
            if ("--base-url".equalsIgnoreCase(token)) {
                String value = requireProviderMutationValue(tokens, ++i, token, usage);
                if (value == null) {
                    return null;
                }
                mutation.baseUrl = value;
                mutation.clearBaseUrl = false;
                continue;
            }
            if ("--api-key".equalsIgnoreCase(token)) {
                String value = requireProviderMutationValue(tokens, ++i, token, usage);
                if (value == null) {
                    return null;
                }
                mutation.apiKey = value;
                mutation.clearApiKey = false;
                continue;
            }
            if ("--clear-model".equalsIgnoreCase(token)) {
                mutation.model = null;
                mutation.clearModel = true;
                continue;
            }
            if ("--clear-base-url".equalsIgnoreCase(token)) {
                mutation.baseUrl = null;
                mutation.clearBaseUrl = true;
                continue;
            }
            if ("--clear-api-key".equalsIgnoreCase(token)) {
                mutation.apiKey = null;
                mutation.clearApiKey = true;
                continue;
            }
            emitError("Unknown provider option: " + token + ". " + usage);
            return null;
        }
        return mutation;
    }

    private String requireProviderMutationValue(String[] tokens, int index, String option, String usage) {
        if (tokens == null || index < 0 || index >= tokens.length || isBlank(tokens[index])) {
            emitError("Missing value for " + option + ". " + usage);
            return null;
        }
        return tokens[index].trim();
    }

    private PlatformType parseProviderType(String raw) {
        if (isBlank(raw)) {
            emitError("Provider is required");
            return null;
        }
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.getPlatform().equalsIgnoreCase(raw.trim())) {
                return platformType;
            }
        }
        emitError("Unsupported provider: " + raw);
        return null;
    }

    private CliProtocol parseProviderProtocol(String raw) {
        try {
            return CliProtocol.parse(raw);
        } catch (IllegalArgumentException ex) {
            emitError(ex.getMessage());
            return null;
        }
    }

    private boolean isSupportedProviderProtocol(PlatformType provider, CliProtocol protocol) {
        if (provider == null || protocol == null || protocol == CliProtocol.CHAT) {
            return true;
        }
        if (provider != PlatformType.OPENAI && provider != PlatformType.DOUBAO && provider != PlatformType.DASHSCOPE) {
            emitError("Provider " + provider.getPlatform() + " does not support responses protocol in ai4j-cli yet");
            return false;
        }
        return true;
    }

    private String normalizeStoredProtocol(String raw, PlatformType provider, String baseUrl) {
        if (isBlank(raw)) {
            return null;
        }
        return CliProtocol.resolveConfigured(raw, provider, baseUrl).getValue();
    }

    private CodeCommandOptions resolveConfiguredRuntimeOptions() {
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(
                null,
                null,
                null,
                null,
                null,
                env,
                properties
        );
        return options.withRuntime(
                resolved.getProvider(),
                resolved.getProtocol(),
                resolved.getModel(),
                resolved.getApiKey(),
                resolved.getBaseUrl()
        );
    }

    private ManagedCodingSession switchSessionRuntime(ManagedCodingSession session, CodeCommandOptions nextOptions) throws Exception {
        if (agentFactory == null) {
            throw new IllegalStateException("Runtime switching is unavailable in this shell");
        }
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(
                nextOptions,
                terminal,
                interactionState,
                pausedMcpServers
        );
        if (session == null || session.getSession() == null) {
            closeMcpRuntimeQuietly(mcpRuntimeManager);
            this.agent = prepared.getAgent();
            this.protocol = prepared.getProtocol();
            this.options = nextOptions;
            this.streamEnabled = nextOptions != null && nextOptions.isStream();
            this.mcpRuntimeManager = prepared.getMcpRuntimeManager();
            attachCodingTaskEventBridge();
            refreshSessionContext(null);
            return session;
        }

        io.github.lnyocly.ai4j.coding.CodingSessionState state = session.getSession().exportState();
        CodingSession nextSession = prepared.getAgent().newSession(session.getSessionId(), state);
        ManagedCodingSession rebound = new ManagedCodingSession(
                nextSession,
                nextOptions.getProvider().getPlatform(),
                prepared.getProtocol().getValue(),
                nextOptions.getModel(),
                nextOptions.getWorkspace(),
                nextOptions.getWorkspaceDescription(),
                nextOptions.getSystemPrompt(),
                nextOptions.getInstructions(),
                firstNonBlank(session.getRootSessionId(), session.getSessionId()),
                session.getParentSessionId(),
                session.getCreatedAtEpochMs(),
                session.getUpdatedAtEpochMs()
        );
        closeQuietly(session);
        closeMcpRuntimeQuietly(mcpRuntimeManager);
        this.agent = prepared.getAgent();
        this.protocol = prepared.getProtocol();
        this.options = nextOptions;
        this.streamEnabled = nextOptions != null && nextOptions.isStream();
        this.mcpRuntimeManager = prepared.getMcpRuntimeManager();
        attachCodingTaskEventBridge();
        refreshSessionContext(rebound);
        return rebound;
    }

    private void printCommands() {
        List<CustomCommandTemplate> commands = customCommandRegistry.list();
        if (commands.isEmpty()) {
            emitOutput("commands: (none)");
            return;
        }
        if (useMainBufferInteractiveShell()) {
            StringBuilder builder = new StringBuilder("commands:\n");
            for (CustomCommandTemplate command : commands) {
                builder.append("- ").append(command.getName())
                        .append(" | ").append(firstNonBlank(command.getDescription(), "(no description)"))
                        .append(" | ").append(command.getSource())
                        .append('\n');
            }
            emitOutput(builder.toString().trim());
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            StringBuilder builder = new StringBuilder();
            builder.append("commands:\n");
            for (CustomCommandTemplate command : commands) {
                builder.append("- ").append(command.getName())
                        .append(" | ").append(firstNonBlank(command.getDescription(), "(no description)"))
                        .append(" | ").append(command.getSource())
                        .append('\n');
            }
            setTuiAssistantOutput(builder.toString().trim());
            return;
        }
        terminal.println("commands:");
        for (CustomCommandTemplate command : commands) {
            terminal.println("  " + command.getName()
                    + " | " + firstNonBlank(command.getDescription(), "(no description)")
                    + " | " + command.getSource());
        }
    }

    private void runCustomCommand(ManagedCodingSession session, String rawArguments) throws Exception {
        if (session == null) {
            emitError("No current session.");
            return;
        }
        if (isBlank(rawArguments)) {
            emitError("Usage: /cmd <name> [args]");
            return;
        }
        String trimmed = rawArguments.trim();
        int firstSpace = trimmed.indexOf(' ');
        String name = firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace).trim();
        String args = firstSpace < 0 ? "" : trimmed.substring(firstSpace + 1).trim();
        CustomCommandTemplate command = customCommandRegistry.find(name);
        if (command == null) {
            emitError("Unknown custom command: " + name);
            return;
        }
        String rendered = renderCustomCommand(command, session, args);
        runAgentTurn(session, rendered);
    }

    private String renderCustomCommand(CustomCommandTemplate command, ManagedCodingSession session, String args) {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("ARGUMENTS", args);
        variables.put("WORKSPACE", options.getWorkspace());
        variables.put("SESSION_ID", session == null ? "" : session.getSessionId());
        variables.put("ROOT_SESSION_ID", session == null ? "" : firstNonBlank(session.getRootSessionId(), ""));
        variables.put("PARENT_SESSION_ID", session == null ? "" : firstNonBlank(session.getParentSessionId(), ""));
        String rendered = command.render(variables).trim();
        if (!isBlank(args) && (command.getTemplate() == null || !command.getTemplate().contains("$ARGUMENTS"))) {
            rendered = rendered + "\n\nArguments:\n" + args;
        }
        return rendered;
    }

    private void applyTheme(String themeName, ManagedCodingSession session) {
        if (isBlank(themeName)) {
            emitError("Usage: /theme <name>");
            return;
        }
        try {
            TuiConfig config = tuiConfigManager.switchTheme(themeName);
            TuiTheme theme = tuiConfigManager.resolveTheme(config.getTheme());
            if (terminal instanceof JlineShellTerminalIO) {
                ((JlineShellTerminalIO) terminal).updateTheme(theme);
            }
            if (tuiRenderer != null) {
                tuiConfig = config;
                tuiTheme = theme;
                tuiRenderer.updateTheme(config, theme);
                setTuiAssistantOutput("Theme switched to `" + theme.getName() + "`.");
            }
            if (options.getUiMode() != CliUiMode.TUI || useMainBufferInteractiveShell()) {
                terminal.println("theme switched to: " + theme.getName());
            }
            renderTui(session);
        } catch (Exception ex) {
            emitError("Failed to switch theme: " + safeMessage(ex));
        }
    }

    private void printCheckpoint(ManagedCodingSession session) {
        CodingSessionCheckpoint checkpoint = session == null || session.getSession() == null
                ? null
                : session.getSession().exportState().getCheckpoint();
        if (useMainBufferInteractiveShell()) {
            emitOutput(renderCheckpointOutput(checkpoint));
            return;
        }
        if (useAppendOnlyTranscriptTui()) {
            emitOutput(renderCheckpointOutput(checkpoint));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            renderTui(session);
            return;
        }
        if (checkpoint == null) {
            terminal.println(renderPanel("checkpoint", "(none)"));
            return;
        }
        terminal.println(renderPanel("checkpoint", toPanelLines(CodingSessionCheckpointFormatter.render(checkpoint), 180, 32)));
    }

    private void printClearMarker(ManagedCodingSession session) {
        if (useAppendOnlyTranscriptTui()) {
            terminal.println("");
            return;
        }
        if (useMainBufferInteractiveShell()) {
            mainBufferTurnPrinter.printSectionBreak();
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            renderTui(session);
            return;
        }
        terminal.println("");
    }

    private CodingSessionCompactResult resolveCompact(CodingSession session, String normalizedCommand) {
        String summary = null;
        if (normalizedCommand != null) {
            String trimmed = normalizedCommand.trim();
            if (trimmed.length() > "/compact".length()) {
                summary = trimmed.substring("/compact".length()).trim();
            }
        }
        return isBlank(summary) ? session.compact() : session.compact(summary);
    }

    private void printCompactResult(CodingSessionCompactResult result) {
        if (result == null) {
            return;
        }
        if (useMainBufferInteractiveShell()) {
            mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatCompact(result));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI) {
            setTuiAssistantOutput(result.getSummary());
            return;
        }
        terminal.println("compact: mode=" + (result.isAutomatic() ? "auto" : "manual")
                + ", strategy=" + firstNonBlank(result.getStrategy(), "checkpoint")
                + ", before=" + result.getBeforeItemCount()
                + ", after=" + result.getAfterItemCount()
                + ", tokens=" + result.getEstimatedTokensBefore() + "->" + result.getEstimatedTokensAfter()
                + ", split=" + result.isSplitTurn()
                + ", fallback=" + result.isFallbackSummary()
                + ", summary=" + clip(result.getSummary(), options.isVerbose() ? 320 : 180));
    }

    private void printAutoCompactOutcome(ManagedCodingSession session, String turnId) {
        List<CodingSessionCompactResult> results = session == null || session.getSession() == null
                ? Collections.<CodingSessionCompactResult>emptyList()
                : session.getSession().drainAutoCompactResults();
        for (CodingSessionCompactResult result : results) {
            if (result == null) {
                continue;
            }
            printCompactResult(result);
            appendCompactEvent(session, result, turnId);
        }
        List<Exception> errors = session == null || session.getSession() == null
                ? Collections.<Exception>emptyList()
                : session.getSession().drainAutoCompactErrors();
        for (Exception error : errors) {
            if (error == null) {
                continue;
            }
            appendEvent(session, SessionEventType.ERROR, turnId, null, safeMessage(error), payloadOf(
                    "error", safeMessage(error),
                    "source", "auto-compact"
            ));
            emitError("Auto compact failed: " + error.getMessage());
        }
    }

    private void appendLoopDecisionEvents(ManagedCodingSession session, String turnId, CodingAgentResult result) {
        List<CodingLoopDecision> decisions = session == null || session.getSession() == null
                ? Collections.<CodingLoopDecision>emptyList()
                : session.getSession().drainLoopDecisions();
        for (CodingLoopDecision decision : decisions) {
            if (decision == null) {
                continue;
            }
            SessionEventType eventType = decision.isContinueLoop()
                    ? SessionEventType.AUTO_CONTINUE
                    : decision.isBlocked() ? SessionEventType.BLOCKED : SessionEventType.AUTO_STOP;
            appendEvent(session, eventType, turnId, null,
                    firstNonBlank(decision.getSummary(), formatLoopDecisionSummary(decision, result)),
                    payloadOf(
                            "turnNumber", decision.getTurnNumber(),
                            "continueReason", decision.getContinueReason(),
                            "stopReason", decision.getStopReason() == null ? null : decision.getStopReason().name().toLowerCase(Locale.ROOT),
                            "compactApplied", decision.isCompactApplied()
                    ));
            emitLoopDecision(decision, result);
        }
    }

    private void emitLoopDecision(CodingLoopDecision decision, CodingAgentResult result) {
        String summary = firstNonBlank(decision == null ? null : decision.getSummary(), formatLoopDecisionSummary(decision, result));
        if (isBlank(summary)) {
            return;
        }
        if (useMainBufferInteractiveShell()) {
            String title = decision != null && decision.isContinueLoop()
                    ? "Auto continue"
                    : decision != null && decision.isBlocked() ? "Blocked" : "Auto stop";
            mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatInfoBlock(
                    title,
                    Collections.singletonList(summary)
            ));
            return;
        }
        if (options.getUiMode() != CliUiMode.TUI) {
            terminal.println("[loop] " + summary);
        }
    }

    private String formatLoopDecisionSummary(CodingLoopDecision decision, CodingAgentResult result) {
        if (decision == null) {
            return result == null || result.getStopReason() == null ? null : formatStopReason(result.getStopReason());
        }
        if (decision.isContinueLoop()) {
            return firstNonBlank(decision.getSummary(), "Auto continue.");
        }
        if (decision.isBlocked()) {
            return firstNonBlank(decision.getSummary(), "Blocked.");
        }
        return firstNonBlank(decision.getSummary(),
                result == null || result.getStopReason() == null ? "Stopped." : formatStopReason(result.getStopReason()));
    }

    private String formatStopReason(CodingStopReason stopReason) {
        if (stopReason == null) {
            return "Stopped.";
        }
        String normalized = stopReason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1) + ".";
    }

    private ManagedCodingSession openInitialSession() throws Exception {
        if (!isBlank(options.getResumeSessionId())) {
            return sessionManager.resume(agent, protocol, options, options.getResumeSessionId());
        }
        if (!isBlank(options.getForkSessionId())) {
            return sessionManager.fork(agent, protocol, options, options.getForkSessionId(), options.getSessionId());
        }
        return sessionManager.create(agent, protocol, options);
    }

    private ManagedCodingSession resumeSession(ManagedCodingSession currentSession, String sessionId) throws Exception {
        if (isBlank(sessionId)) {
            emitError("Usage: /resume <session-id>");
            return currentSession;
        }

        closeQuietly(currentSession);
        persistSession(currentSession, true);
        ManagedCodingSession resumed = sessionManager.resume(agent, protocol, options, sessionId);

        if (options.getUiMode() != CliUiMode.TUI || useMainBufferInteractiveShell()) {
            terminal.println("resumed session: " + resumed.getSessionId());
        }
        printSessionHeader(resumed);
        return resumed;
    }

    private ManagedCodingSession forkSession(ManagedCodingSession currentSession, String rawArguments) throws Exception {
        if (currentSession == null) {
            emitError("No current session to fork.");
            return null;
        }
        String[] parts = isBlank(rawArguments) ? new String[0] : rawArguments.trim().split("\\s+");
        String sourceSessionId;
        String targetSessionId;
        if (parts.length == 0) {
            sourceSessionId = currentSession.getSessionId();
            targetSessionId = null;
        } else if (parts.length == 1) {
            sourceSessionId = currentSession.getSessionId();
            targetSessionId = parts[0];
        } else {
            sourceSessionId = parts[0];
            targetSessionId = parts[1];
        }

        if (currentSession != null && sourceSessionId.equals(currentSession.getSessionId())) {
            persistSession(currentSession, true);
        }
        ManagedCodingSession forked = sessionManager.fork(agent, protocol, options, sourceSessionId, targetSessionId);
        closeQuietly(currentSession);
        persistSession(forked, true);
        if (options.getUiMode() != CliUiMode.TUI || useMainBufferInteractiveShell()) {
            terminal.println("forked session: " + forked.getSessionId() + " <- " + sourceSessionId);
        }
        printSessionHeader(forked);
        return forked;
    }

    private void persistSession(ManagedCodingSession session, boolean force) {
        if (session == null || (!force && !options.isAutoSaveSession())) {
            return;
        }
        try {
            StoredCodingSession stored = sessionManager.save(session);
            refreshTuiSessions();
            refreshTuiEvents(session);
            if (force && (options.getUiMode() != CliUiMode.TUI || useMainBufferInteractiveShell())) {
                terminal.println("saved session: " + stored.getSessionId() + " -> " + stored.getStorePath());
            }
        } catch (IOException ex) {
            emitError("Failed to save session: " + ex.getMessage());
        }
    }

    private void printSessions() {
        try {
            List<CodingSessionDescriptor> sessions = sessionManager.list();
            if (sessions.isEmpty()) {
                emitOutput("No saved sessions found in " + sessionManager.getDirectory());
                return;
            }

            if (useAppendOnlyTranscriptTui()) {
                emitOutput(renderSessionsOutput(sessions));
                return;
            }
            if (useMainBufferInteractiveShell()) {
                emitOutput(renderSessionsOutput(sessions));
                return;
            }
            if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
                setTuiCachedSessions(sessions);
                return;
            }

            terminal.println("sessions:");
            for (CodingSessionDescriptor session : sessions) {
                terminal.println("  " + session.getSessionId()
                        + " | root=" + clip(session.getRootSessionId(), 24)
                        + " | parent=" + clip(firstNonBlank(session.getParentSessionId(), "-"), 24)
                        + " | updated=" + formatTimestamp(session.getUpdatedAtEpochMs())
                        + " | memory=" + session.getMemoryItemCount()
                        + " | processes=" + session.getProcessCount()
                        + " | " + clip(session.getSummary(), 120));
            }
        } catch (IOException ex) {
            emitError("Failed to list sessions: " + ex.getMessage());
        }
    }

    private void printHistory(ManagedCodingSession currentSession, String targetSessionId) {
        try {
            List<CodingSessionDescriptor> sessions = mergeCurrentSession(sessionManager.list(), currentSession);
            CodingSessionDescriptor target = resolveTargetDescriptor(sessions, currentSession, targetSessionId);
            if (target == null) {
                emitOutput("history: (none)");
                return;
            }
            List<CodingSessionDescriptor> history = resolveHistory(sessions, target);
            if (history.isEmpty()) {
                emitOutput("history: (none)");
                return;
            }
            if (useMainBufferInteractiveShell()) {
                StringBuilder builder = new StringBuilder();
                builder.append("history:\n");
                for (CodingSessionDescriptor session : history) {
                    builder.append("- ")
                            .append(session.getSessionId())
                            .append(" | parent=").append(firstNonBlank(session.getParentSessionId(), "(root)"))
                            .append(" | updated=").append(formatTimestamp(session.getUpdatedAtEpochMs()))
                            .append(" | ").append(clip(session.getSummary(), 120))
                            .append('\n');
                }
                emitOutput(builder.toString().trim());
                return;
            }
            if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
                StringBuilder builder = new StringBuilder();
                builder.append("history:\n");
                for (CodingSessionDescriptor session : history) {
                    builder.append("- ")
                            .append(session.getSessionId())
                            .append(" | parent=").append(firstNonBlank(session.getParentSessionId(), "(root)"))
                            .append(" | updated=").append(formatTimestamp(session.getUpdatedAtEpochMs()))
                            .append(" | ").append(clip(session.getSummary(), 120))
                            .append('\n');
                }
                setTuiAssistantOutput(builder.toString().trim());
                return;
            }
            terminal.println("history:");
            for (CodingSessionDescriptor session : history) {
                terminal.println("  " + session.getSessionId()
                        + " | root=" + clip(session.getRootSessionId(), 24)
                        + " | parent=" + clip(firstNonBlank(session.getParentSessionId(), "(root)"), 24)
                        + " | updated=" + formatTimestamp(session.getUpdatedAtEpochMs())
                        + " | " + clip(session.getSummary(), 120));
            }
        } catch (IOException ex) {
            emitError("Failed to build history: " + ex.getMessage());
        }
    }

    private void printTree(ManagedCodingSession currentSession, String rootArgument) {
        try {
            List<CodingSessionDescriptor> sessions = mergeCurrentSession(sessionManager.list(), currentSession);
            List<String> lines = renderTreeLines(sessions, rootArgument, currentSession == null ? null : currentSession.getSessionId());
            if (lines.isEmpty()) {
                emitOutput("tree: (none)");
                return;
            }
            if (useMainBufferInteractiveShell()) {
                StringBuilder builder = new StringBuilder();
                builder.append("tree:\n");
                for (String line : lines) {
                    builder.append(line).append('\n');
                }
                emitOutput(builder.toString().trim());
                return;
            }
            if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
                StringBuilder builder = new StringBuilder();
                builder.append("tree:\n");
                for (String line : lines) {
                    builder.append(line).append('\n');
                }
                setTuiAssistantOutput(builder.toString().trim());
                return;
            }
            terminal.println("tree:");
            for (String line : lines) {
                terminal.println("  " + line);
            }
        } catch (IOException ex) {
            emitError("Failed to build session tree: " + ex.getMessage());
        }
    }

    private void printEvents(ManagedCodingSession session, String limitArgument) {
        if (session == null) {
            emitOutput("events: (no current session)");
            return;
        }
        Integer limit = parseLimit(limitArgument);
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), limit, null);
            if (useAppendOnlyTranscriptTui()) {
                emitOutput(renderEventsOutput(events));
                return;
            }
            if (useMainBufferInteractiveShell()) {
                emitOutput(renderEventsOutput(events));
                return;
            }
            if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
                setTuiCachedEvents(events);
                return;
            }
            if (events.isEmpty()) {
                terminal.println("events: (none)");
                return;
            }
            terminal.println("events:");
            for (SessionEvent event : events) {
                terminal.println("  " + formatTimestamp(event.getTimestamp())
                        + " | " + event.getType()
                        + (event.getStep() == null ? "" : " | step=" + event.getStep())
                        + " | " + clip(event.getSummary(), 160));
            }
        } catch (IOException ex) {
            emitError("Failed to list session events: " + ex.getMessage());
        }
    }

    private void printReplay(ManagedCodingSession session, String limitArgument) {
        if (session == null) {
            emitOutput("replay: (no current session)");
            return;
        }
        Integer limit = parseLimit(limitArgument);
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), limit, null);
            List<String> replayLines = buildReplayLines(events);
            if (useAppendOnlyTranscriptTui()) {
                emitOutput(renderReplayOutput(replayLines));
                return;
            }
            if (useMainBufferInteractiveShell()) {
                emitOutput(renderReplayOutput(replayLines));
                return;
            }
            if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
                setTuiCachedReplay(replayLines);
                if (!replayLines.isEmpty()) {
                    interactionState.openReplayViewer();
                    setTuiAssistantOutput("Replay viewer opened.");
                } else {
                    setTuiAssistantOutput("history: (none)");
                }
                return;
            }
            if (replayLines.isEmpty()) {
                emitOutput("replay: (none)");
                return;
            }
            StringBuilder builder = new StringBuilder("replay:\n");
            for (String replayLine : replayLines) {
                builder.append(replayLine).append('\n');
            }
            emitOutput(builder.toString().trim());
        } catch (IOException ex) {
            emitError("Failed to replay events: " + ex.getMessage());
        }
    }

    private void printTeamBoard(ManagedCodingSession session) {
        if (session == null) {
            emitOutput("team: (no current session)");
            return;
        }
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), null, null);
            List<String> teamBoardLines = TeamBoardRenderSupport.renderBoardLines(events);
            if (useAppendOnlyTranscriptTui()) {
                emitOutput(TeamBoardRenderSupport.renderBoardOutput(teamBoardLines));
                return;
            }
            if (useMainBufferInteractiveShell()) {
                emitOutput(TeamBoardRenderSupport.renderBoardOutput(teamBoardLines));
                return;
            }
            if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
                setTuiCachedTeamBoard(teamBoardLines);
                if (!teamBoardLines.isEmpty()) {
                    interactionState.openTeamBoard();
                    setTuiAssistantOutput("Team board opened.");
                } else {
                    setTuiAssistantOutput("team: (none)");
                }
                return;
            }
            emitOutput(TeamBoardRenderSupport.renderBoardOutput(teamBoardLines));
        } catch (IOException ex) {
            emitError("Failed to render team board: " + ex.getMessage());
        }
    }

    private void printCompacts(ManagedCodingSession session, String limitArgument) {
        if (session == null) {
            emitOutput("compacts: (no current session)");
            return;
        }
        int limit = parseLimit(limitArgument);
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), null, null);
            List<String> compactLines = buildCompactLines(events, limit);
            if (compactLines.isEmpty()) {
                emitOutput("compacts: (none)");
                return;
            }
            StringBuilder builder = new StringBuilder("compacts:\n");
            for (String compactLine : compactLines) {
                builder.append(compactLine).append('\n');
            }
            emitOutput(builder.toString().trim());
        } catch (IOException ex) {
            emitError("Failed to list compact history: " + ex.getMessage());
        }
    }

    private void printProcesses(ManagedCodingSession session) {
        CodingSessionSnapshot snapshot = session == null || session.getSession() == null ? null : session.getSession().snapshot();
        List<BashProcessInfo> processes = snapshot == null
                ? null
                : snapshot.getProcesses();
        if (processes == null || processes.isEmpty()) {
            emitOutput("processes: (none)");
            return;
        }

        if (useMainBufferInteractiveShell()) {
            emitOutput(renderProcessesOutput(processes));
            return;
        }
        if (useAppendOnlyTranscriptTui()) {
            emitOutput(renderProcessesOutput(processes));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            if (isBlank(interactionState.getSelectedProcessId())) {
                interactionState.selectProcess(processes.get(0).getProcessId());
            }
            return;
        }

        terminal.println("processes:");
        for (BashProcessInfo process : processes) {
            terminal.println("  " + process.getProcessId()
                    + " | status=" + process.getStatus()
                    + " | mode=" + (process.isControlAvailable() ? "live" : "metadata-only")
                    + " | restored=" + process.isRestored()
                    + " | cwd=" + clip(process.getWorkingDirectory(), 48)
                    + " | cmd=" + clip(process.getCommand(), 72));
        }
    }

    private void handleProcessCommand(ManagedCodingSession session, String rawArguments) {
        if (session == null || session.getSession() == null) {
            emitError("No current session.");
            return;
        }
        if (isBlank(rawArguments)) {
            emitError("Usage: /process <logs|write|stop> ...");
            return;
        }
        String[] parts = rawArguments.trim().split("\\s+", 3);
        String action = parts[0];
        try {
            if ("status".equalsIgnoreCase(action)) {
                if (parts.length < 2) {
                    emitError("Usage: /process status <process-id>");
                    return;
                }
                showProcessStatus(session, parts[1], false, DEFAULT_PROCESS_LOG_LIMIT);
                return;
            }
            if ("follow".equalsIgnoreCase(action)) {
                if (parts.length < 2) {
                    emitError("Usage: /process follow <process-id> [limit]");
                    return;
                }
                Integer limit = parts.length >= 3 ? parseLimit(parts[2]) : DEFAULT_PROCESS_LOG_LIMIT;
                showProcessStatus(session, parts[1], true, limit);
                return;
            }
            if ("logs".equalsIgnoreCase(action)) {
                if (parts.length < 2) {
                    emitError("Usage: /process logs <process-id> [limit]");
                    return;
                }
                Integer limit = parts.length >= 3 ? parseLimit(parts[2]) : DEFAULT_EVENT_LIMIT * 40;
                BashProcessLogChunk logs = session.getSession().processLogs(parts[1], null, limit);
                emitOutput("process logs:\n" + (logs == null ? "(none)" : firstNonBlank(logs.getContent(), "(none)")));
                return;
            }
            if ("write".equalsIgnoreCase(action)) {
                if (parts.length < 3) {
                    emitError("Usage: /process write <process-id> <text>");
                    return;
                }
                int bytesWritten = session.getSession().writeProcess(parts[1], parts[2]);
                emitOutput("process write: " + parts[1] + " bytes=" + bytesWritten);
                return;
            }
            if ("stop".equalsIgnoreCase(action)) {
                if (parts.length < 2) {
                    emitError("Usage: /process stop <process-id>");
                    return;
                }
                BashProcessInfo processInfo = session.getSession().stopProcess(parts[1]);
                emitOutput("process stopped: " + processInfo.getProcessId() + " status=" + processInfo.getStatus());
                return;
            }
            emitError("Unknown process action: " + action);
        } catch (Exception ex) {
            emitError("Process command failed: " + safeMessage(ex));
        }
    }

    private void openReplayViewer(ManagedCodingSession session, int limit) {
        if (session == null) {
            setTuiAssistantOutput("history: (no current session)");
            return;
        }
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), limit, null);
            List<String> replayLines = buildReplayLines(events);
            if (useAppendOnlyTranscriptTui()) {
                emitOutput(renderReplayOutput(replayLines));
                return;
            }
            setTuiCachedReplay(replayLines);
            interactionState.openReplayViewer();
            setTuiAssistantOutput(replayLines.isEmpty() ? "history: (none)" : "History opened.");
        } catch (IOException ex) {
            emitError("Failed to open replay viewer: " + ex.getMessage());
        }
    }

    private void openProcessInspector(ManagedCodingSession session, String processId) {
        if (session == null || session.getSession() == null || isBlank(processId)) {
            return;
        }
        if (useAppendOnlyTranscriptTui()) {
            try {
                BashProcessInfo processInfo = session.getSession().processStatus(processId);
                BashProcessLogChunk logs = session.getSession().processLogs(processId, null, DEFAULT_PROCESS_LOG_LIMIT);
                emitOutput(renderProcessDetailsOutput(processInfo, logs));
            } catch (Exception ex) {
                setTuiAssistantOutput("process inspector failed: " + safeMessage(ex));
            }
            return;
        }
        interactionState.selectProcess(processId);
        interactionState.openProcessInspector(processId);
        try {
            setTuiProcessInspector(
                    session.getSession().processStatus(processId),
                    session.getSession().processLogs(processId, null, DEFAULT_PROCESS_LOG_LIMIT)
            );
            setTuiAssistantOutput("Process inspector opened: " + processId);
        } catch (Exception ex) {
            setTuiAssistantOutput("process inspector failed: " + safeMessage(ex));
        }
    }

    private List<String> buildReplayLines(List<SessionEvent> events) {
        if (events == null || events.isEmpty()) {
            return new ArrayList<String>();
        }
        Map<String, List<String>> byTurn = new LinkedHashMap<String, List<String>>();
        Map<String, java.util.Set<String>> completedToolKeysByTurn = buildCompletedToolKeysByTurn(events);
        for (SessionEvent event : events) {
            if (event == null || event.getType() == null) {
                continue;
            }
            String turnId = isBlank(event.getTurnId()) ? "session" : event.getTurnId();
            if (event.getType() == SessionEventType.TOOL_CALL
                    && completedToolKeysByTurn.containsKey(turnId)
                    && completedToolKeysByTurn.get(turnId).contains(buildToolEventKey(event.getPayload()))) {
                continue;
            }
            List<String> eventLines = buildReplayEventLines(event);
            if (eventLines == null || eventLines.isEmpty()) {
                continue;
            }
            List<String> turnLines = byTurn.get(turnId);
            if (turnLines == null) {
                turnLines = new ArrayList<String>();
                byTurn.put(turnId, turnLines);
            }
            turnLines.addAll(eventLines);
        }

        List<String> lines = new ArrayList<String>();
        for (List<String> turnLines : byTurn.values()) {
            if (turnLines == null || turnLines.isEmpty()) {
                continue;
            }
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.addAll(turnLines);
        }
        return lines;
    }

    private Map<String, java.util.Set<String>> buildCompletedToolKeysByTurn(List<SessionEvent> events) {
        Map<String, java.util.Set<String>> keysByTurn = new LinkedHashMap<String, java.util.Set<String>>();
        if (events == null || events.isEmpty()) {
            return keysByTurn;
        }
        for (SessionEvent event : events) {
            if (event == null || event.getType() != SessionEventType.TOOL_RESULT) {
                continue;
            }
            String key = buildToolEventKey(event.getPayload());
            if (isBlank(key)) {
                continue;
            }
            String turnId = isBlank(event.getTurnId()) ? "session" : event.getTurnId();
            java.util.Set<String> turnKeys = keysByTurn.get(turnId);
            if (turnKeys == null) {
                turnKeys = new java.util.HashSet<String>();
                keysByTurn.put(turnId, turnKeys);
            }
            turnKeys.add(key);
        }
        return keysByTurn;
    }

    private String buildToolEventKey(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String callId = safeTrimToNull(payloadString(payload, "callId"));
        if (!isBlank(callId)) {
            return callId;
        }
        String toolName = payloadString(payload, "tool");
        JSONObject arguments = parseObject(payloadString(payload, "arguments"));
        return firstNonBlank(toolName, "tool") + "|"
                + firstNonBlank(safeTrimToNull(payloadString(payload, "title")), buildToolTitle(toolName, arguments));
    }

    private List<String> buildReplayEventLines(SessionEvent event) {
        List<String> lines = new ArrayList<String>();
        SessionEventType type = event == null ? null : event.getType();
        Map<String, Object> payload = event == null ? null : event.getPayload();
        if (type == SessionEventType.USER_MESSAGE) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatAssistant(
                    firstNonBlank(payloadString(payload, "input"), event.getSummary())
            ));
            return lines;
        }
        if (type == SessionEventType.ASSISTANT_MESSAGE) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatAssistant(
                    firstNonBlank(payloadString(payload, "output"), event.getSummary())
            ));
            return lines;
        }
        if (type == SessionEventType.TOOL_CALL) {
            appendReplayToolLines(lines, payload, true);
            lines.add("");
            return lines;
        }
        if (type == SessionEventType.TOOL_RESULT) {
            appendReplayToolLines(lines, payload, false);
            lines.add("");
            return lines;
        }
        if (type == SessionEventType.ERROR) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatError(
                    firstNonBlank(payloadString(payload, "error"), event.getSummary())
            ));
            return lines;
        }
        if (type == SessionEventType.COMPACT) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatCompact(buildReplayCompactResult(event)));
            return lines;
        }
        if (type == SessionEventType.AUTO_CONTINUE || type == SessionEventType.AUTO_STOP || type == SessionEventType.BLOCKED) {
            String title = type == SessionEventType.AUTO_CONTINUE
                    ? "Auto continue"
                    : type == SessionEventType.BLOCKED ? "Blocked" : "Auto stop";
            appendReplayBlock(lines, codexStyleBlockFormatter.formatInfoBlock(
                    title,
                    Collections.singletonList(firstNonBlank(event.getSummary(), title.toLowerCase(Locale.ROOT)))
            ));
            return lines;
        }
        if (type == SessionEventType.SESSION_RESUMED) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatInfoBlock(
                    "Session resumed",
                    Collections.singletonList(firstNonBlank(event.getSummary(), "session resumed"))
            ));
            return lines;
        }
        if (type == SessionEventType.SESSION_FORKED) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatInfoBlock(
                    "Session forked",
                    Collections.singletonList(firstNonBlank(event.getSummary(), "session forked"))
            ));
            return lines;
        }
        if (type == SessionEventType.TASK_CREATED || type == SessionEventType.TASK_UPDATED) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatInfoBlock(
                    "Delegate task",
                    buildReplayTaskLines(event)
            ));
            return lines;
        }
        if (type == SessionEventType.TEAM_MESSAGE) {
            appendReplayBlock(lines, codexStyleBlockFormatter.formatInfoBlock(
                    "Team message",
                    buildReplayTeamMessageLines(event)
            ));
            return lines;
        }
        return lines;
    }

    private List<String> buildReplayTaskLines(SessionEvent event) {
        List<String> lines = new ArrayList<String>();
        Map<String, Object> payload = event == null ? null : event.getPayload();
        lines.add(firstNonBlank(event == null ? null : event.getSummary(), "delegate task"));
        String detail = firstNonBlank(payloadString(payload, "detail"), payloadString(payload, "error"), payloadString(payload, "output"));
        if (!isBlank(detail)) {
            lines.add(detail);
        }
        String member = firstNonBlank(payloadString(payload, "memberName"), payloadString(payload, "memberId"));
        if (!isBlank(member)) {
            lines.add("member: " + member);
        }
        String childSessionId = payloadString(payload, "childSessionId");
        if (!isBlank(childSessionId)) {
            lines.add("child session: " + childSessionId);
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
            lines.add(stateLine.toString());
        }
        String heartbeatCount = payloadString(payload, "heartbeatCount");
        if (!isBlank(heartbeatCount) && !"0".equals(heartbeatCount)) {
            lines.add("heartbeats: " + heartbeatCount);
        }
        return lines;
    }

    private List<String> buildReplayTeamMessageLines(SessionEvent event) {
        List<String> lines = new ArrayList<String>();
        Map<String, Object> payload = event == null ? null : event.getPayload();
        lines.add(firstNonBlank(event == null ? null : event.getSummary(), "team message"));
        String taskId = payloadString(payload, "taskId");
        if (!isBlank(taskId)) {
            lines.add("task: " + taskId);
        }
        String detail = firstNonBlank(payloadString(payload, "content"), payloadString(payload, "detail"));
        if (!isBlank(detail)) {
            lines.add(detail);
        }
        return lines;
    }

    private void appendReplayBlock(List<String> lines, List<String> blockLines) {
        if (lines == null || blockLines == null || blockLines.isEmpty()) {
            return;
        }
        lines.addAll(blockLines);
        lines.add("");
    }

    private CodingSessionCompactResult buildReplayCompactResult(SessionEvent event) {
        Map<String, Object> payload = event == null ? null : event.getPayload();
        return CodingSessionCompactResult.builder()
                .automatic("auto".equalsIgnoreCase(resolveCompactMode(event)))
                .beforeItemCount(defaultInt(payloadInt(payload, "beforeItemCount")))
                .afterItemCount(defaultInt(payloadInt(payload, "afterItemCount")))
                .estimatedTokensBefore(defaultInt(payloadInt(payload, "estimatedTokensBefore")))
                .estimatedTokensAfter(defaultInt(payloadInt(payload, "estimatedTokensAfter")))
                .splitTurn(payloadBoolean(payload, "splitTurn"))
                .summary(firstNonBlank(payloadString(payload, "summary"), event == null ? null : event.getSummary()))
                .build();
    }

    private void appendReplayMultiline(List<String> lines, String prefix, String text) {
        if (lines == null || isBlank(text)) {
            return;
        }
        String[] rawLines = text.replace("\r", "").split("\n");
        String continuation = replayContinuation(prefix);
        for (int i = 0; i < rawLines.length; i++) {
            lines.add((i == 0 ? firstNonBlank(prefix, "") : continuation) + (rawLines[i] == null ? "" : rawLines[i]));
        }
    }

    private void appendReplayToolLines(List<String> lines, Map<String, Object> payload, boolean pending) {
        if (lines == null || payload == null) {
            return;
        }
        String toolName = payloadString(payload, "tool");
        JSONObject arguments = parseObject(payloadString(payload, "arguments"));
        JSONObject output = parseObject(payloadString(payload, "output"));
        String title = firstNonBlank(payloadString(payload, "title"), buildToolTitle(toolName, arguments));
        List<String> previewLines = payloadLines(payload, "previewLines");

        if (pending) {
            lines.addAll(buildToolPrimaryLines(toolName, title, "pending", 120));
            if (previewLines.isEmpty()) {
                previewLines = buildPendingToolPreviewLines(toolName, arguments);
            }
            previewLines = normalizeToolPreviewLines(toolName, "pending", previewLines);
            for (int i = 0; i < previewLines.size(); i++) {
                String prefix = i == 0 ? "  \u2514 " : "    ";
                lines.addAll(wrapPrefixedText(prefix, "    ", previewLines.get(i), 120));
            }
            return;
        }

        String rawOutput = payloadString(payload, "output");
        if (isApprovalRejectedToolError(rawOutput, output)) {
            lines.addAll(codexStyleBlockFormatter.formatInfoBlock(
                    "Rejected",
                    Collections.singletonList(firstNonBlank(
                            payloadString(payload, "detail"),
                            buildCompletedToolDetail(toolName, arguments, output, rawOutput),
                            normalizeToolPrimaryLabel(firstNonBlank(title, toolName))
                    ))
            ));
            return;
        }
        lines.addAll(buildToolPrimaryLines(toolName, title, isBlank(extractToolError(rawOutput, output)) ? "done" : "error", 120));
        String detail = firstNonBlank(payloadString(payload, "detail"),
                buildCompletedToolDetail(toolName, arguments, output, rawOutput));
        if (!isBlank(detail)) {
            lines.addAll(wrapPrefixedText("  \u2514 ", "    ", detail, 120));
        }
        if (previewLines.isEmpty()) {
            previewLines = buildToolPreviewLines(toolName, arguments, output, rawOutput);
        }
        previewLines = normalizeToolPreviewLines(toolName, "done", previewLines);
        for (int i = 0; i < previewLines.size(); i++) {
            String prefix = i == 0 && isBlank(detail) ? "  \u2514 " : "    ";
            lines.addAll(wrapPrefixedText(prefix, "    ", previewLines.get(i), 120));
        }
    }

    private List<String> buildToolPrimaryLines(String toolName, String title, String status, int width) {
        List<String> lines = new ArrayList<String>();
        String normalizedTool = firstNonBlank(toolName, "tool");
        String normalizedStatus = firstNonBlank(status, "done").toLowerCase(Locale.ROOT);
        String label = normalizeToolPrimaryLabel(firstNonBlank(title, normalizedTool));
        if ("error".equals(normalizedStatus)) {
            if ("bash".equals(normalizedTool)) {
                return wrapPrefixedText("\u2022 Command failed ", "  \u2502 ", label, width);
            }
            lines.add("\u2022 Tool failed " + clip(label, Math.max(24, width - 16)));
            return lines;
        }
        if ("apply_patch".equals(normalizedTool)) {
            lines.add("pending".equals(normalizedStatus) ? "\u2022 Applying patch" : "\u2022 Applied patch");
            return lines;
        }
        return wrapPrefixedText(resolveToolPrimaryPrefix(normalizedTool, title, normalizedStatus), "  \u2502 ", label, width);
    }

    private String resolveToolPrimaryPrefix(String toolName, String title, String status) {
        String normalizedTool = firstNonBlank(toolName, "tool");
        String normalizedTitle = firstNonBlank(title, normalizedTool);
        boolean pending = "pending".equalsIgnoreCase(status);
        if ("read_file".equals(normalizedTool)) {
            return pending ? "\u2022 Reading " : "\u2022 Read ";
        }
        if ("write_file".equals(normalizedTool)) {
            return pending ? "\u2022 Writing " : "\u2022 Wrote ";
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
        if (normalizedTitle.startsWith("write ")) {
            return normalizedTitle.substring(6).trim();
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

    private String stripToolTitlePrefix(String title, String prefix) {
        if (isBlank(title) || isBlank(prefix)) {
            return firstNonBlank(title, "");
        }
        return title.startsWith(prefix) ? title.substring(prefix.length()).trim() : title;
    }

    private String replayContinuation(String prefix) {
        int length = prefix == null ? 0 : prefix.length();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private List<String> buildCompactLines(List<SessionEvent> events, int limit) {
        List<SessionEvent> compactEvents = new ArrayList<SessionEvent>();
        if (events != null) {
            for (SessionEvent event : events) {
                if (event != null && event.getType() == SessionEventType.COMPACT) {
                    compactEvents.add(event);
                }
            }
        }
        if (compactEvents.isEmpty()) {
            return new ArrayList<String>();
        }

        int safeLimit = limit <= 0 ? DEFAULT_EVENT_LIMIT : limit;
        int from = Math.max(0, compactEvents.size() - safeLimit);
        List<String> lines = new ArrayList<String>();
        for (int i = from; i < compactEvents.size(); i++) {
            SessionEvent event = compactEvents.get(i);
            Map<String, Object> payload = event.getPayload();
            StringBuilder line = new StringBuilder();
            line.append(formatTimestamp(event.getTimestamp()))
                    .append(" | mode=").append(resolveCompactMode(event))
                    .append(" | tokens=").append(formatCompactDelta(
                            payloadInt(payload, "estimatedTokensBefore"),
                            payloadInt(payload, "estimatedTokensAfter")
                    ))
                    .append(" | items=").append(formatCompactDelta(
                            payloadInt(payload, "beforeItemCount"),
                            payloadInt(payload, "afterItemCount")
                    ));
            if (payloadBoolean(payload, "splitTurn")) {
                line.append(" | splitTurn");
            }
            if (payloadBoolean(payload, "fallbackSummary")) {
                line.append(" | fallback");
            }
            String checkpointGoal = clip(payloadString(payload, "checkpointGoal"), 64);
            if (!isBlank(checkpointGoal)) {
                line.append(" | goal=").append(checkpointGoal);
            }
            lines.add(line.toString());

            String summary = firstNonBlank(payloadString(payload, "summary"), event.getSummary());
            if (!isBlank(summary)) {
                lines.add("  - " + clip(summary, 140));
            }
        }
        return lines;
    }

    private void showProcessStatus(ManagedCodingSession session,
                                   String processId,
                                   boolean includeLogs,
                                   int logLimit) throws Exception {
        BashProcessInfo processInfo = session.getSession().processStatus(processId);
        if (useMainBufferInteractiveShell() && !includeLogs) {
            emitOutput(renderProcessStatusOutput(processInfo));
            return;
        }
        if (useAppendOnlyTranscriptTui() && !includeLogs) {
            emitOutput(renderProcessStatusOutput(processInfo));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI && !useMainBufferInteractiveShell()) {
            BashProcessLogChunk logs = includeLogs ? session.getSession().processLogs(processId, null, logLimit) : null;
            interactionState.selectProcess(processId);
            interactionState.openProcessInspector(processId);
            setTuiAssistantOutput(includeLogs
                    ? "Process inspector opened: " + processId
                    : "Selected process: " + processId);
            setTuiProcessInspector(processInfo, logs);
            return;
        }

        if (includeLogs) {
            followProcessLogs(session, processInfo, logLimit);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("process status:\n");
        appendProcessSummary(builder, processInfo);
        terminal.println(builder.toString());
    }

    private void followProcessLogs(ManagedCodingSession session,
                                   BashProcessInfo initialProcessInfo,
                                   int logLimit) throws Exception {
        BashProcessInfo processInfo = initialProcessInfo;
        long nextOffset = 0L;
        int idlePolls = 0;

        StringBuilder builder = new StringBuilder();
        builder.append("process status:\n");
        appendProcessSummary(builder, processInfo);
        builder.append('\n').append("process follow:");
        terminal.println(builder.toString());

        while (true) {
            BashProcessLogChunk logs = session.getSession().processLogs(processInfo.getProcessId(), Long.valueOf(nextOffset), logLimit);
            boolean advanced = logs != null && logs.getNextOffset() > nextOffset;
            if (advanced) {
                String content = logs.getContent();
                if (!isBlank(content)) {
                    terminal.println(content);
                }
                nextOffset = logs.getNextOffset();
                idlePolls = 0;
            }
            processInfo = session.getSession().processStatus(processInfo.getProcessId());
            BashProcessStatus status = logs != null && logs.getStatus() != null ? logs.getStatus() : processInfo.getStatus();
            if (status != BashProcessStatus.RUNNING) {
                if (advanced) {
                    continue;
                }
                terminal.println("process final: status=" + status + ", exitCode=" + processInfo.getExitCode());
                return;
            }
            if (advanced) {
                continue;
            }
            idlePolls++;
            if (idlePolls >= PROCESS_FOLLOW_MAX_IDLE_POLLS) {
                terminal.println("(follow paused: no new output yet, process still running; rerun /process follow to continue)");
                return;
            }
            try {
                Thread.sleep(PROCESS_FOLLOW_POLL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                terminal.println("(follow interrupted)");
                return;
            }
        }
    }

    private void appendProcessSummary(StringBuilder builder, BashProcessInfo processInfo) {
        builder.append("  id=").append(processInfo.getProcessId())
                .append(" | status=").append(processInfo.getStatus())
                .append(" | mode=").append(processInfo.isControlAvailable() ? "live" : "metadata-only")
                .append(" | restored=").append(processInfo.isRestored())
                .append('\n');
        builder.append("  cwd=").append(clip(processInfo.getWorkingDirectory(), 120)).append('\n');
        builder.append("  cmd=").append(clip(processInfo.getCommand(), 120));
    }

    private void writeSelectedProcessInput(ManagedCodingSession session) {
        String processId = interactionState.getSelectedProcessId();
        String input = interactionState.consumeProcessInputBuffer();
        if (isBlank(processId) || isBlank(input) || session == null || session.getSession() == null) {
            return;
        }
        try {
            int bytesWritten = session.getSession().writeProcess(processId, input);
            setTuiAssistantOutput("process write: " + processId + " bytes=" + bytesWritten);
            renderTui(session);
        } catch (Exception ex) {
            setTuiAssistantOutput("process write failed: " + safeMessage(ex));
        }
    }

    private List<String> resolveProcessIds(ManagedCodingSession session) {
        CodingSessionSnapshot snapshot = session == null || session.getSession() == null ? null : session.getSession().snapshot();
        List<BashProcessInfo> processes = snapshot == null ? null : snapshot.getProcesses();
        List<String> processIds = new ArrayList<String>();
        if (processes == null) {
            return processIds;
        }
        for (BashProcessInfo process : processes) {
            if (process != null && !isBlank(process.getProcessId())) {
                processIds.add(process.getProcessId());
            }
        }
        return processIds;
    }

    private List<SlashCommandController.ProcessCompletionCandidate> buildProcessCompletionCandidates() {
        CodingSessionSnapshot snapshot = activeSession == null || activeSession.getSession() == null
                ? null
                : activeSession.getSession().snapshot();
        List<BashProcessInfo> processes = snapshot == null ? null : snapshot.getProcesses();
        List<SlashCommandController.ProcessCompletionCandidate> candidates =
                new ArrayList<SlashCommandController.ProcessCompletionCandidate>();
        if (processes == null) {
            return candidates;
        }
        for (BashProcessInfo process : processes) {
            if (process == null || isBlank(process.getProcessId())) {
                continue;
            }
            candidates.add(new SlashCommandController.ProcessCompletionCandidate(
                    process.getProcessId(),
                    buildProcessCompletionDescription(process)
            ));
        }
        return candidates;
    }

    private List<SlashCommandController.ModelCompletionCandidate> buildModelCompletionCandidates() {
        LinkedHashMap<String, SlashCommandController.ModelCompletionCandidate> candidates =
                new LinkedHashMap<String, SlashCommandController.ModelCompletionCandidate>();
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(null, null, null, null, null, env, properties);
        String currentProvider = options.getProvider() == null
                ? (resolved.getProvider() == null ? null : resolved.getProvider().getPlatform())
                : options.getProvider().getPlatform();

        addModelCompletionCandidate(candidates, resolved.getModelOverride(), "Current workspace override");
        addModelCompletionCandidate(candidates, options.getModel(), "Current effective model");

        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
        addProfileModelCompletionCandidate(candidates, workspaceConfig.getActiveProfile(), currentProvider, "Active profile");
        addProfileModelCompletionCandidate(candidates, providersConfig.getDefaultProfile(), currentProvider, "Default profile");
        for (String profileName : providerConfigManager.listProfileNames()) {
            addProfileModelCompletionCandidate(candidates, profileName, currentProvider, "Saved profile");
        }
        return new ArrayList<SlashCommandController.ModelCompletionCandidate>(candidates.values());
    }

    private List<String> listKnownSkillNames() {
        WorkspaceContext workspaceContext = activeSession == null || activeSession.getSession() == null
                ? null
                : activeSession.getSession().getWorkspaceContext();
        List<CodingSkillDescriptor> skills = workspaceContext == null ? null : workspaceContext.getAvailableSkills();
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (CodingSkillDescriptor skill : skills) {
            if (skill != null && !isBlank(skill.getName())) {
                names.add(skill.getName().trim());
            }
        }
        return new ArrayList<String>(names);
    }

    private List<String> listKnownAgentNames() {
        CodingAgentDefinitionRegistry registry = agent == null ? null : agent.getDefinitionRegistry();
        List<CodingAgentDefinition> definitions = registry == null ? null : registry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (CodingAgentDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            if (!isBlank(definition.getName())) {
                names.add(definition.getName().trim());
            }
            if (!isBlank(definition.getToolName())) {
                names.add(definition.getToolName().trim());
            }
        }
        return new ArrayList<String>(names);
    }

    private List<String> listKnownMcpServerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        CliMcpConfig globalConfig = mcpConfigManager.loadGlobalConfig();
        if (globalConfig.getMcpServers() != null) {
            names.addAll(globalConfig.getMcpServers().keySet());
        }
        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        if (workspaceConfig.getEnabledMcpServers() != null) {
            names.addAll(workspaceConfig.getEnabledMcpServers());
        }
        names.addAll(pausedMcpServers);
        return new ArrayList<String>(names);
    }

    private void addProfileModelCompletionCandidate(LinkedHashMap<String, SlashCommandController.ModelCompletionCandidate> candidates,
                                                    String profileName,
                                                    String provider,
                                                    String label) {
        if (candidates == null || isBlank(profileName)) {
            return;
        }
        CliProviderProfile profile = providerConfigManager.getProfile(profileName);
        if (profile == null || isBlank(profile.getModel())) {
            return;
        }
        if (!isBlank(provider) && !provider.equalsIgnoreCase(firstNonBlank(profile.getProvider(), provider))) {
            return;
        }
        addModelCompletionCandidate(candidates, profile.getModel(), label + " " + profileName);
    }

    private void addModelCompletionCandidate(LinkedHashMap<String, SlashCommandController.ModelCompletionCandidate> candidates,
                                             String model,
                                             String description) {
        if (candidates == null || isBlank(model)) {
            return;
        }
        String key = model.trim().toLowerCase(Locale.ROOT);
        if (candidates.containsKey(key)) {
            return;
        }
        candidates.put(key, new SlashCommandController.ModelCompletionCandidate(model.trim(), description));
    }

    private boolean containsMcpServer(String name) {
        if (isBlank(name)) {
            return false;
        }
        String normalizedName = name.trim();
        CliMcpConfig globalConfig = mcpConfigManager.loadGlobalConfig();
        if (globalConfig.getMcpServers().containsKey(normalizedName)) {
            return true;
        }
        CliWorkspaceConfig workspaceConfig = mcpConfigManager.loadWorkspaceConfig();
        return containsIgnoreCase(workspaceConfig.getEnabledMcpServers(), normalizedName);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || values.isEmpty() || isBlank(target)) {
            return false;
        }
        for (String value : values) {
            if (!isBlank(value) && target.trim().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> addName(List<String> values, String name) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                if (!isBlank(value)) {
                    names.add(value.trim());
                }
            }
        }
        if (!isBlank(name)) {
            names.add(name.trim());
        }
        return names.isEmpty() ? null : new ArrayList<String>(names);
    }

    private List<String> removeName(List<String> values, String name) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> remaining = new ArrayList<String>();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            if (!value.trim().equalsIgnoreCase(firstNonBlank(name, ""))) {
                remaining.add(value.trim());
            }
        }
        return remaining.isEmpty() ? null : remaining;
    }

    private List<String> splitWhitespace(String value) {
        if (isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.trim().split("\\s+"));
    }

    private String normalizeMcpTransport(String rawTransport) {
        if (isBlank(rawTransport)) {
            return null;
        }
        String normalized = rawTransport.trim().toLowerCase(Locale.ROOT);
        if ("http".equals(normalized) || "streamable_http".equals(normalized) || "streamable-http".equals(normalized)) {
            return "streamable_http";
        }
        if ("sse".equals(normalized)) {
            return "sse";
        }
        if ("stdio".equals(normalized)) {
            return "stdio";
        }
        return null;
    }

    private String buildProcessCompletionDescription(BashProcessInfo process) {
        if (process == null) {
            return "Process";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(firstNonBlank(
                process.getStatus() == null ? null : String.valueOf(process.getStatus()).toLowerCase(Locale.ROOT),
                "unknown"
        ));
        builder.append(" | ").append(process.isControlAvailable() ? "live" : "metadata-only");
        if (process.isRestored()) {
            builder.append(" | restored");
        }
        String command = clip(process.getCommand(), 48);
        if (!isBlank(command)) {
            builder.append(" | ").append(command);
        }
        return builder.toString();
    }

    private String firstProcessId(ManagedCodingSession session) {
        List<String> processIds = resolveProcessIds(session);
        return processIds.isEmpty() ? null : processIds.get(0);
    }

    private List<CodingSessionDescriptor> mergeCurrentSession(List<CodingSessionDescriptor> sessions, ManagedCodingSession currentSession) {
        List<CodingSessionDescriptor> merged = sessions == null
                ? new ArrayList<CodingSessionDescriptor>()
                : new ArrayList<CodingSessionDescriptor>(sessions);
        if (currentSession == null) {
            return merged;
        }
        CodingSessionDescriptor currentDescriptor = currentSession.toDescriptor();
        for (int i = 0; i < merged.size(); i++) {
            CodingSessionDescriptor existing = merged.get(i);
            if (existing != null && currentDescriptor.getSessionId().equals(existing.getSessionId())) {
                merged.set(i, currentDescriptor);
                return merged;
            }
        }
        merged.add(0, currentDescriptor);
        return merged;
    }

    private CodingSessionDescriptor resolveTargetDescriptor(List<CodingSessionDescriptor> sessions,
                                                            ManagedCodingSession currentSession,
                                                            String targetSessionId) {
        if (isBlank(targetSessionId)) {
            return currentSession == null ? null : currentSession.toDescriptor();
        }
        for (CodingSessionDescriptor session : sessions) {
            if (session != null && targetSessionId.equals(session.getSessionId())) {
                return session;
            }
        }
        return null;
    }

    private List<CodingSessionDescriptor> resolveHistory(List<CodingSessionDescriptor> sessions, CodingSessionDescriptor target) {
        List<CodingSessionDescriptor> history = new ArrayList<CodingSessionDescriptor>();
        if (target == null) {
            return history;
        }
        Map<String, CodingSessionDescriptor> byId = new LinkedHashMap<String, CodingSessionDescriptor>();
        for (CodingSessionDescriptor session : sessions) {
            if (session != null && !isBlank(session.getSessionId())) {
                byId.put(session.getSessionId(), session);
            }
        }
        CodingSessionDescriptor cursor = target;
        while (cursor != null) {
            history.add(0, cursor);
            String parentSessionId = cursor.getParentSessionId();
            if (isBlank(parentSessionId)) {
                break;
            }
            CodingSessionDescriptor next = byId.get(parentSessionId);
            if (next == null || parentSessionId.equals(cursor.getSessionId())) {
                break;
            }
            cursor = next;
        }
        return history;
    }

    private List<String> resolveHistoryLines(List<CodingSessionDescriptor> sessions, CodingSessionDescriptor target) {
        List<CodingSessionDescriptor> history = resolveHistory(sessions, target);
        List<String> lines = new ArrayList<String>();
        for (CodingSessionDescriptor session : history) {
            lines.add(session.getSessionId()
                    + " | parent=" + firstNonBlank(session.getParentSessionId(), "(root)")
                    + " | " + clip(session.getSummary(), 84));
        }
        return lines;
    }

    private List<String> renderTreeLines(List<CodingSessionDescriptor> sessions, String rootArgument, String currentSessionId) {
        Map<String, CodingSessionDescriptor> byId = new LinkedHashMap<String, CodingSessionDescriptor>();
        Map<String, List<CodingSessionDescriptor>> children = new LinkedHashMap<String, List<CodingSessionDescriptor>>();
        List<CodingSessionDescriptor> roots = new ArrayList<CodingSessionDescriptor>();
        for (CodingSessionDescriptor session : sessions) {
            if (session == null || isBlank(session.getSessionId())) {
                continue;
            }
            byId.put(session.getSessionId(), session);
        }
        for (CodingSessionDescriptor session : byId.values()) {
            String parentSessionId = session.getParentSessionId();
            if (isBlank(parentSessionId) || !byId.containsKey(parentSessionId)) {
                roots.add(session);
                continue;
            }
            List<CodingSessionDescriptor> siblings = children.get(parentSessionId);
            if (siblings == null) {
                siblings = new ArrayList<CodingSessionDescriptor>();
                children.put(parentSessionId, siblings);
            }
            siblings.add(session);
        }
        java.util.Comparator<CodingSessionDescriptor> comparator = java.util.Comparator
                .comparingLong(CodingSessionDescriptor::getCreatedAtEpochMs)
                .thenComparing(CodingSessionDescriptor::getSessionId);
        java.util.Collections.sort(roots, comparator);
        for (List<CodingSessionDescriptor> siblings : children.values()) {
            java.util.Collections.sort(siblings, comparator);
        }

        List<String> lines = new ArrayList<String>();
        if (isBlank(rootArgument)) {
            for (CodingSessionDescriptor root : roots) {
                appendTreeLines(lines, children, root, 0, currentSessionId);
            }
            return lines;
        }

        CodingSessionDescriptor root = byId.get(rootArgument);
        if (root == null) {
            for (CodingSessionDescriptor candidate : byId.values()) {
                if (rootArgument.equals(candidate.getRootSessionId())) {
                    root = byId.get(candidate.getRootSessionId());
                    break;
                }
            }
        }
        if (root != null) {
            appendTreeLines(lines, children, root, 0, currentSessionId);
        }
        return lines;
    }

    private void appendTreeLines(List<String> lines,
                                 Map<String, List<CodingSessionDescriptor>> children,
                                 CodingSessionDescriptor session,
                                 int depth,
                                 String currentSessionId) {
        if (session == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(depth == 0 ? "* " : "- ");
        builder.append(session.getSessionId());
        if (session.getSessionId().equals(currentSessionId)) {
            builder.append(" [current]");
        }
        builder.append(" | updated=").append(formatTimestamp(session.getUpdatedAtEpochMs()));
        builder.append(" | ").append(clip(session.getSummary(), 96));
        lines.add(builder.toString());

        List<CodingSessionDescriptor> childSessions = children.get(session.getSessionId());
        if (childSessions == null) {
            return;
        }
        for (CodingSessionDescriptor child : childSessions) {
            appendTreeLines(lines, children, child, depth + 1, currentSessionId);
        }
    }

    private List<String> renderCommandLines(List<CustomCommandTemplate> commands) {
        List<String> lines = new ArrayList<String>();
        lines.add("/help /status /session /theme /save /providers /provider /model /skills /agents");
        lines.add("/provider use|save|add|edit|default|remove /mcp");
        lines.add("/mcp add|enable|disable|pause|resume|retry|remove");
        lines.add("/commands /palette /cmd <name> /sessions /history /tree /events /replay /team /compacts /checkpoint");
        lines.add("/processes /process status|follow|logs|write|stop");
        lines.add("/resume <id> /load <id> /fork ... /compact /clear /exit");
        if (commands != null) {
            int max = Math.min(commands.size(), 5);
            for (int i = 0; i < max; i++) {
                CustomCommandTemplate command = commands.get(i);
                lines.add("/cmd " + command.getName() + " | " + clip(firstNonBlank(command.getDescription(), "(no description)"), 72));
            }
        }
        return lines;
    }

    private List<TuiPaletteItem> buildPaletteItems(ManagedCodingSession session) {
        List<TuiPaletteItem> items = new ArrayList<TuiPaletteItem>(buildCommandPaletteItems());

        for (String themeName : tuiConfigManager.listThemeNames()) {
            items.add(new TuiPaletteItem("theme-" + themeName, "theme", "Theme: " + themeName, "/theme " + themeName, "/theme " + themeName));
        }
        for (String profileName : providerConfigManager.listProfileNames()) {
            items.add(new TuiPaletteItem(
                    "provider-" + profileName,
                    "provider",
                    "Provider: " + profileName,
                    "Switch to saved profile " + profileName,
                    "/provider use " + profileName
            ));
        }
        for (String serverName : listKnownMcpServerNames()) {
            items.add(new TuiPaletteItem(
                    "mcp-enable-" + serverName,
                    "mcp",
                    "MCP enable: " + serverName,
                    "Enable workspace MCP service " + serverName,
                    "/mcp enable " + serverName
            ));
            items.add(new TuiPaletteItem(
                    "mcp-pause-" + serverName,
                    "mcp",
                    "MCP pause: " + serverName,
                    "Pause MCP service " + serverName + " for this session",
                    "/mcp pause " + serverName
            ));
        }

        try {
            List<CodingSessionDescriptor> sessions = mergeCurrentSession(sessionManager.list(), session);
            int maxSessions = Math.min(sessions.size(), 8);
            for (int i = 0; i < maxSessions; i++) {
                CodingSessionDescriptor descriptor = sessions.get(i);
                items.add(new TuiPaletteItem(
                        "resume-" + descriptor.getSessionId(),
                        "session",
                        "Resume: " + descriptor.getSessionId(),
                        clip(firstNonBlank(descriptor.getSummary(), descriptor.getSessionId()), 96),
                        "/resume " + descriptor.getSessionId()
                ));
            }
        } catch (IOException ex) {
            if (options.isVerbose()) {
                terminal.errorln("Failed to build palette sessions: " + ex.getMessage());
            }
        }
        return items;
    }

    private List<TuiPaletteItem> buildCommandPaletteItems() {
        List<TuiPaletteItem> items = new ArrayList<TuiPaletteItem>();
        items.add(new TuiPaletteItem("help", "command", "/help", "Show help", "/help"));
        items.add(new TuiPaletteItem("status", "command", "/status", "Show current session status", "/status"));
        items.add(new TuiPaletteItem("session", "command", "/session", "Show current session metadata", "/session"));
        items.add(new TuiPaletteItem("theme", "command", "/theme", "Show or switch the active theme", "/theme"));
        items.add(new TuiPaletteItem("save", "command", "/save", "Persist the current session state", "/save"));
        items.add(new TuiPaletteItem("providers", "command", "/providers", "List saved provider profiles", "/providers"));
        items.add(new TuiPaletteItem("provider", "command", "/provider", "Show current provider/profile state", "/provider"));
        items.add(new TuiPaletteItem("provider-use", "command", "/provider use", "Switch to a saved provider profile", "/provider use"));
        items.add(new TuiPaletteItem("provider-save", "command", "/provider save", "Save the current runtime as a profile", "/provider save"));
        items.add(new TuiPaletteItem("provider-add", "command", "/provider add", "Create a provider profile from explicit fields", "/provider add"));
        items.add(new TuiPaletteItem("provider-edit", "command", "/provider edit", "Update a saved provider profile", "/provider edit"));
        items.add(new TuiPaletteItem("provider-default", "command", "/provider default", "Set the global default provider profile", "/provider default"));
        items.add(new TuiPaletteItem("provider-remove", "command", "/provider remove", "Delete a saved provider profile", "/provider remove"));
        items.add(new TuiPaletteItem("model", "command", "/model", "Show the current effective model", "/model"));
        items.add(new TuiPaletteItem("model-reset", "command", "/model reset", "Clear the workspace model override", "/model reset"));
        items.add(new TuiPaletteItem("skills", "command", "/skills", "List discovered coding skills", "/skills"));
        items.add(new TuiPaletteItem("agents", "command", "/agents", "List available coding agents", "/agents"));
        items.add(new TuiPaletteItem("mcp", "command", "/mcp", "Show current MCP services and status", "/mcp"));
        items.add(new TuiPaletteItem("mcp-add", "command", "/mcp add", "Add a global MCP service", "/mcp add --transport "));
        items.add(new TuiPaletteItem("mcp-enable", "command", "/mcp enable", "Enable an MCP service in this workspace", "/mcp enable "));
        items.add(new TuiPaletteItem("mcp-disable", "command", "/mcp disable", "Disable an MCP service in this workspace", "/mcp disable "));
        items.add(new TuiPaletteItem("mcp-pause", "command", "/mcp pause", "Pause an MCP service for this session", "/mcp pause "));
        items.add(new TuiPaletteItem("mcp-resume", "command", "/mcp resume", "Resume an MCP service for this session", "/mcp resume "));
        items.add(new TuiPaletteItem("mcp-retry", "command", "/mcp retry", "Reconnect an MCP service", "/mcp retry "));
        items.add(new TuiPaletteItem("mcp-remove", "command", "/mcp remove", "Delete a global MCP service", "/mcp remove "));
        items.add(new TuiPaletteItem("commands", "command", "/commands", "List available custom commands", "/commands"));
        items.add(new TuiPaletteItem("palette", "command", "/palette", "Alias of /commands", "/palette"));
        items.add(new TuiPaletteItem("cmd", "command", "/cmd", "Run a custom command template", "/cmd"));
        items.add(new TuiPaletteItem("sessions", "command", "/sessions", "List saved sessions", "/sessions"));
        items.add(new TuiPaletteItem("history", "command", "/history", "Show session lineage", "/history"));
        items.add(new TuiPaletteItem("tree", "command", "/tree", "Show the current session tree", "/tree"));
        items.add(new TuiPaletteItem("events", "command", "/events", "Show the latest session ledger events", "/events 20"));
        items.add(new TuiPaletteItem("replay", "command", "/replay", "Show recent history", "/replay 20"));
        items.add(new TuiPaletteItem("team", "command", "/team", "Open the current agent team board", "/team"));
        items.add(new TuiPaletteItem("compacts", "command", "/compacts", "Show compact history from the event ledger", "/compacts 20"));
        items.add(new TuiPaletteItem("processes", "command", "/processes", "List active and restored process metadata", "/processes"));
        items.add(new TuiPaletteItem("process-status", "command", "/process status", "Show metadata for one process", "/process status"));
        items.add(new TuiPaletteItem("process-follow", "command", "/process follow", "Follow buffered logs for a process", "/process follow"));
        items.add(new TuiPaletteItem("process-logs", "command", "/process logs", "Read buffered logs for a process", "/process logs"));
        items.add(new TuiPaletteItem("process-write", "command", "/process write", "Write text to process stdin", "/process write"));
        items.add(new TuiPaletteItem("process-stop", "command", "/process stop", "Stop a live process", "/process stop"));
        items.add(new TuiPaletteItem("checkpoint", "command", "/checkpoint", "Show the current structured checkpoint summary", "/checkpoint"));
        items.add(new TuiPaletteItem("resume", "command", "/resume", "Resume a saved session", "/resume"));
        items.add(new TuiPaletteItem("load", "command", "/load", "Alias of /resume", "/load"));
        items.add(new TuiPaletteItem("fork", "command", "/fork", "Fork a session branch", "/fork"));
        items.add(new TuiPaletteItem("compact", "command", "/compact", "Compact current session memory", "/compact"));
        items.add(new TuiPaletteItem("clear", "command", "/clear", "Print a new screen section", "/clear"));
        items.add(new TuiPaletteItem("exit", "command", "/exit", "Exit session", "/exit"));

        List<CustomCommandTemplate> commands = customCommandRegistry.list();
        for (CustomCommandTemplate command : commands) {
            items.add(new TuiPaletteItem(
                    "cmd-" + command.getName(),
                    "command",
                    "/cmd " + command.getName(),
                    firstNonBlank(command.getDescription(), "Run custom command " + command.getName()),
                    "/cmd " + command.getName()
            ));
        }
        return items;
    }

    private void emitOutput(String text) {
        if (useMainBufferInteractiveShell()) {
            mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatOutput(text));
            return;
        }
        if (options.getUiMode() == CliUiMode.TUI) {
            setTuiAssistantOutput(text);
            return;
        }
        terminal.println(text);
    }

    private void emitError(String text) {
        if (useAppendOnlyTranscriptTui()) {
            emitOutput("Error: " + firstNonBlank(text, "unknown error"));
            return;
        }
        if (useMainBufferInteractiveShell()) {
            emitMainBufferError(text);
            return;
        }
        terminal.errorln(text);
    }

    private boolean useMainBufferInteractiveShell() {
        if (options.getUiMode() != CliUiMode.TUI || useAlternateScreenTui()) {
            return false;
        }
        Boolean explicit = resolveMainBufferInteractiveOverride();
        if (explicit != null) {
            return explicit.booleanValue();
        }
        if (useAppendOnlyTranscriptTui()) {
            return false;
        }
        // Default built-in non-alt-screen TUI sessions to Codex-style transcript mode.
        return tuiRuntime instanceof AnsiTuiRuntime && tuiRenderer instanceof TuiSessionView;
    }

    private boolean useAppendOnlyTranscriptTui() {
        return options.getUiMode() == CliUiMode.TUI && tuiRuntime instanceof AppendOnlyTuiRuntime;
    }

    private boolean suppressMainBufferReasoningBlocks() {
        return false;
    }

    private boolean streamTranscriptEnabled() {
        return useMainBufferInteractiveShell() && streamEnabled;
    }

    private boolean renderMainBufferAssistantIncrementally() {
        return false;
    }

    private boolean renderMainBufferReasoningIncrementally() {
        return false;
    }

    private Boolean resolveMainBufferInteractiveOverride() {
        String value = System.getProperty("ai4j.tui.main-buffer");
        if (isBlank(value)) {
            value = System.getenv("AI4J_TUI_MAIN_BUFFER");
        }
        if (isBlank(value)) {
            return null;
        }
        return Boolean.valueOf("true".equalsIgnoreCase(value.trim()));
    }

    private List<String> buildMainBufferToolLines(TuiAssistantToolView toolView) {
        return codexStyleBlockFormatter.formatTool(toolView);
    }

    private String buildMainBufferRunningStatus(TuiAssistantToolView toolView) {
        return codexStyleBlockFormatter.formatRunningStatus(toolView);
    }

    private void emitMainBufferAssistant(String text) {
        if (!isBlank(text)) {
            mainBufferTurnPrinter.printAssistantBlock(text);
        }
    }

    private void emitMainBufferReasoning(String text) {
        if (isBlank(text)) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        String[] rawLines = text.replace("\r", "").split("\n", -1);
        int start = 0;
        int end = rawLines.length - 1;
        while (start <= end && isBlank(rawLines[start])) {
            start++;
        }
        while (end >= start && isBlank(rawLines[end])) {
            end--;
        }
        if (start > end) {
            return;
        }
        String continuationPrefix = repeat(' ', "Thinking: ".length());
        boolean previousBlank = false;
        for (int i = start; i <= end; i++) {
            String rawLine = rawLines[i] == null ? "" : rawLines[i];
            if (isBlank(rawLine)) {
                if (!lines.isEmpty() && !previousBlank) {
                    lines.add("");
                }
                previousBlank = true;
                continue;
            }
            lines.add((lines.isEmpty() ? "Thinking: " : continuationPrefix) + rawLine);
            previousBlank = false;
        }
        if (lines.isEmpty()) {
            return;
        }
        mainBufferTurnPrinter.printBlock(lines);
    }

    private void emitMainBufferError(String message) {
        mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatError(message));
    }

    private String lastPathSegment(String value) {
        if (isBlank(value)) {
            return ".";
        }
        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 && index + 1 < normalized.length() ? normalized.substring(index + 1) : normalized;
    }

    private void refreshTuiSessions() {
        if (tuiRenderer == null) {
            return;
        }
        try {
            List<CodingSessionDescriptor> sessions = mergeCurrentSession(sessionManager.list(), activeSession);
            setTuiCachedSessions(sessions);
            setTuiCachedHistory(resolveHistoryLines(sessions, activeSession == null ? null : activeSession.toDescriptor()));
            setTuiCachedTree(renderTreeLines(sessions, null, activeSession == null ? null : activeSession.getSessionId()));
            setTuiCachedCommands(renderCommandLines(customCommandRegistry.list()));
        } catch (IOException ex) {
            terminal.errorln("Failed to refresh sessions: " + ex.getMessage());
        }
    }

    private void refreshTuiEvents(ManagedCodingSession session) {
        if (tuiRenderer == null || session == null) {
            return;
        }
        try {
            setTuiCachedEvents(sessionManager.listEvents(session.getSessionId(), null, null));
        } catch (IOException ex) {
            terminal.errorln("Failed to refresh session events: " + ex.getMessage());
        }
    }

    private void refreshTuiReplay(ManagedCodingSession session) {
        if (tuiRenderer == null || session == null || !interactionState.isReplayViewerOpen()) {
            return;
        }
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), DEFAULT_REPLAY_LIMIT, null);
            setTuiCachedReplay(buildReplayLines(events));
        } catch (IOException ex) {
            terminal.errorln("Failed to refresh replay viewer: " + ex.getMessage());
        }
    }

    private void refreshTuiTeamBoard(ManagedCodingSession session) {
        if (tuiRenderer == null || session == null || !interactionState.isTeamBoardOpen()) {
            return;
        }
        try {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), null, null);
            setTuiCachedTeamBoard(TeamBoardRenderSupport.renderBoardLines(events));
        } catch (IOException ex) {
            terminal.errorln("Failed to refresh team board: " + ex.getMessage());
        }
    }

    private void refreshTuiProcessInspector(ManagedCodingSession session) {
        if (tuiRenderer == null || session == null || session.getSession() == null) {
            return;
        }
        String processId = firstNonBlank(interactionState.getSelectedProcessId(), firstProcessId(session));
        if (isBlank(processId)) {
            setTuiProcessInspector(null, null);
            return;
        }
        try {
            BashProcessInfo processInfo = session.getSession().processStatus(processId);
            BashProcessLogChunk logs = interactionState.isProcessInspectorOpen()
                    ? session.getSession().processLogs(processId, null, DEFAULT_PROCESS_LOG_LIMIT)
                    : null;
            setTuiProcessInspector(processInfo, logs);
        } catch (Exception ex) {
            setTuiAssistantOutput("process refresh failed: " + safeMessage(ex));
            setTuiProcessInspector(null, null);
        }
    }

    private void renderTui(ManagedCodingSession session) {
        renderTui(session, true);
    }

    private void renderTuiFromCache(ManagedCodingSession session) {
        renderTui(session, false);
    }

    private void renderTui(ManagedCodingSession session, boolean refreshCaches) {
        if (useMainBufferInteractiveShell()) {
            return;
        }
        if (tuiRenderer == null || tuiRuntime == null) {
            return;
        }
        activeSession = session;
        if (refreshCaches) {
            refreshTuiSessions();
            refreshTuiEvents(session);
            refreshTuiReplay(session);
            refreshTuiTeamBoard(session);
            refreshTuiProcessInspector(session);
        }
        tuiRuntime.render(buildTuiScreenModel(session));
    }

    private TuiScreenModel buildTuiScreenModel(ManagedCodingSession session) {
        CodingSessionSnapshot snapshot = session == null || session.getSession() == null ? null : session.getSession().snapshot();
        CodingSessionDescriptor descriptor = session == null ? null : session.toDescriptor();
        CodingSessionCheckpoint checkpoint = session == null || session.getSession() == null
                ? null
                : session.getSession().exportState().getCheckpoint();
        return TuiScreenModel.builder()
                .config(tuiConfig)
                .theme(tuiTheme)
                .descriptor(descriptor)
                .snapshot(snapshot)
                .checkpoint(checkpoint)
                .renderContext(buildTuiRenderContext())
                .interactionState(interactionState)
                .cachedSessions(tuiSessions)
                .cachedHistory(tuiHistory)
                .cachedTree(tuiTree)
                .cachedCommands(tuiCommands)
                .cachedEvents(tuiEvents)
                .cachedReplay(tuiReplay)
                .cachedTeamBoard(tuiTeamBoard)
                .inspectedProcess(tuiInspectedProcess)
                .inspectedProcessLogs(tuiInspectedProcessLogs)
                .assistantOutput(tuiAssistantOutput)
                .assistantViewModel(tuiLiveTurnState.toViewModel())
                .build();
    }

    private void setTuiAssistantOutput(String output) {
        this.tuiAssistantOutput = isBlank(output) ? null : output;
    }

    private void setTuiCachedSessions(List<CodingSessionDescriptor> sessions) {
        this.tuiSessions = sessions == null ? new ArrayList<CodingSessionDescriptor>() : new ArrayList<CodingSessionDescriptor>(sessions);
    }

    private void setTuiCachedHistory(List<String> historyLines) {
        this.tuiHistory = historyLines == null ? new ArrayList<String>() : new ArrayList<String>(historyLines);
    }

    private void setTuiCachedTree(List<String> treeLines) {
        this.tuiTree = treeLines == null ? new ArrayList<String>() : new ArrayList<String>(treeLines);
    }

    private void setTuiCachedCommands(List<String> commands) {
        this.tuiCommands = commands == null ? new ArrayList<String>() : new ArrayList<String>(commands);
    }

    private void setTuiCachedEvents(List<SessionEvent> events) {
        this.tuiEvents = events == null ? new ArrayList<SessionEvent>() : new ArrayList<SessionEvent>(events);
    }

    private void setTuiCachedReplay(List<String> replayLines) {
        this.tuiReplay = replayLines == null ? new ArrayList<String>() : new ArrayList<String>(replayLines);
    }

    private void setTuiCachedTeamBoard(List<String> teamBoardLines) {
        this.tuiTeamBoard = teamBoardLines == null ? new ArrayList<String>() : new ArrayList<String>(teamBoardLines);
    }

    private void setTuiProcessInspector(BashProcessInfo processInfo, BashProcessLogChunk logs) {
        this.tuiInspectedProcess = processInfo;
        this.tuiInspectedProcessLogs = logs;
    }

    private boolean shouldAutoRefresh(ManagedCodingSession session) {
        if (session == null || session.getSession() == null) {
            return false;
        }
        if (!useAlternateScreenTui()) {
            return false;
        }
        return interactionState.isProcessInspectorOpen() && tuiInspectedProcess != null && tuiInspectedProcess.isControlAvailable();
    }

    private boolean shouldAnimateAppendOnlyFooter() {
        return useAppendOnlyTranscriptTui()
                && terminal != null
                && terminal.supportsAnsi()
                && tuiLiveTurnState.isSpinnerActive();
    }

    private void beginTuiTurn(String input) {
        if (!isTuiMode()) {
            return;
        }
        setTuiAssistantOutput(null);
        tuiLiveTurnState.beginTurn(input);
    }

    private boolean isTuiMode() {
        return options.getUiMode() == CliUiMode.TUI;
    }

    private void renderTuiIfEnabled(ManagedCodingSession session) {
        if (isTuiMode()) {
            renderTui(session);
        }
    }

    private boolean shouldRunTurnsAsync() {
        return isTuiMode()
                && tuiRuntime != null
                && tuiRuntime.supportsRawInput()
                && !useMainBufferInteractiveShell();
    }

    private void runMainBufferTurn(final ManagedCodingSession session, final String input) throws Exception {
        if (session == null || isBlank(input)) {
            return;
        }
        final String turnId = newTurnId();
        final Exception[] failure = new Exception[1];
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runTurn(session, input, null, turnId);
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }
        }, "ai4j-main-buffer-turn");
        registerMainBufferTurn(turnId, worker);
        JlineShellTerminalIO shellTerminal = terminal instanceof JlineShellTerminalIO
                ? (JlineShellTerminalIO) terminal
                : null;
        worker.start();
        if (shellTerminal != null) {
            shellTerminal.beginTurnInterruptPolling();
        }
        try {
            while (worker.isAlive()) {
                if (shellTerminal != null && shellTerminal.pollTurnInterrupt(100L)) {
                    interruptActiveMainBufferTurn(turnId);
                }
                try {
                    worker.join(25L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    interruptActiveMainBufferTurn(turnId);
                    break;
                }
            }
            try {
                worker.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                interruptActiveMainBufferTurn(turnId);
                throw ex;
            }
        } finally {
            if (shellTerminal != null) {
                shellTerminal.endTurnInterruptPolling();
            }
        }
        boolean interrupted = isMainBufferTurnInterrupted(turnId);
        clearMainBufferTurnInterruptState(turnId);
        if (failure[0] != null && !interrupted) {
            throw failure[0];
        }
    }

    private boolean isTurnInterrupted(String turnId, ActiveTuiTurn activeTurn) {
        return (activeTurn != null && activeTurn.isInterrupted()) || isMainBufferTurnInterrupted(turnId);
    }

    private void registerMainBufferTurn(String turnId, Thread worker) {
        synchronized (mainBufferTurnInterruptLock) {
            activeMainBufferTurnId = turnId;
            activeMainBufferTurnThread = worker;
            activeMainBufferTurnInterrupted = false;
        }
    }

    private void clearMainBufferTurnInterruptState(String turnId) {
        synchronized (mainBufferTurnInterruptLock) {
            if (!sameTurnId(activeMainBufferTurnId, turnId)) {
                return;
            }
            activeMainBufferTurnId = null;
            activeMainBufferTurnThread = null;
            activeMainBufferTurnInterrupted = false;
        }
    }

    private boolean interruptActiveMainBufferTurn(String turnId) {
        Thread thread;
        synchronized (mainBufferTurnInterruptLock) {
            if (!sameTurnId(activeMainBufferTurnId, turnId)
                    || activeMainBufferTurnInterrupted
                    || activeMainBufferTurnThread == null) {
                return false;
            }
            activeMainBufferTurnInterrupted = true;
            thread = activeMainBufferTurnThread;
        }
        thread.interrupt();
        ChatModelClient.cancelActiveStream(thread);
        ResponsesModelClient.cancelActiveStream(thread);
        return true;
    }

    private boolean isMainBufferTurnInterrupted(String turnId) {
        synchronized (mainBufferTurnInterruptLock) {
            return activeMainBufferTurnInterrupted && sameTurnId(activeMainBufferTurnId, turnId);
        }
    }

    private String buildModelConnectionStatus(ManagedCodingSession session) {
        String provider = session == null ? null : session.getProvider();
        String model = session == null ? options.getModel() : firstNonBlank(session.getModel(), options.getModel());
        if (!isBlank(provider) && !isBlank(model)) {
            return "Connecting to " + clip(provider + "/" + model, 56);
        }
        if (!isBlank(model)) {
            return "Connecting to " + clip(model, 56);
        }
        if (!isBlank(provider)) {
            return "Connecting to " + clip(provider, 56);
        }
        return "Opening model stream";
    }

    private void handleMainBufferTurnInterrupted(ManagedCodingSession session, String turnId) {
        mainBufferTurnPrinter.clearTransient();
        tuiLiveTurnState.onError(null, TURN_INTERRUPTED_MESSAGE);
        appendEvent(session, SessionEventType.ERROR, turnId, null, TURN_INTERRUPTED_MESSAGE, payloadOf(
                "error", TURN_INTERRUPTED_MESSAGE
        ));
        renderTuiIfEnabled(session);
        emitMainBufferError(TURN_INTERRUPTED_MESSAGE);
        Thread.interrupted();
    }

    private boolean hasActiveTuiTurn() {
        ActiveTuiTurn turn = activeTuiTurn;
        return turn != null && !turn.isDone();
    }

    private void startAsyncTuiTurn(ManagedCodingSession session, String input) {
        if (session == null || isBlank(input) || hasActiveTuiTurn()) {
            return;
        }
        ActiveTuiTurn turn = new ActiveTuiTurn(session, input);
        activeTuiTurn = turn;
        turn.start();
    }

    private void interruptActiveTuiTurn(ManagedCodingSession session) {
        ActiveTuiTurn turn = activeTuiTurn;
        if (turn == null || !turn.requestInterrupt()) {
            return;
        }
        tuiLiveTurnState.onError(null, TURN_INTERRUPTED_MESSAGE);
        appendEvent(session, SessionEventType.ERROR, turn.getTurnId(), null, TURN_INTERRUPTED_MESSAGE, payloadOf(
                "error", TURN_INTERRUPTED_MESSAGE
        ));
        renderTuiIfEnabled(session);
    }

    private ManagedCodingSession reapCompletedTuiTurn(ManagedCodingSession fallbackSession) throws Exception {
        ActiveTuiTurn turn = activeTuiTurn;
        if (turn == null || !turn.isDone()) {
            return fallbackSession;
        }
        activeTuiTurn = null;
        ManagedCodingSession session = turn.getSession() == null ? fallbackSession : turn.getSession();
        persistSession(session, false);
        Exception failure = turn.getFailure();
        if (failure != null && !turn.isInterrupted()) {
            throw failure;
        }
        return session;
    }

    private void startTuiTurnAnimation(final ManagedCodingSession session) {
        if (!shouldAnimateTuiTurn()) {
            return;
        }
        stopTuiTurnAnimation();
        tuiTurnAnimationRunning = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (tuiTurnAnimationRunning) {
                    try {
                        renderTui(session);
                        Thread.sleep(TUI_TURN_ANIMATION_POLL_MS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ignored) {
                        break;
                    }
                }
            }
        }, "ai4j-tui-turn-animation");
        thread.setDaemon(true);
        tuiTurnAnimationThread = thread;
        thread.start();
    }

    private boolean shouldAnimateTuiTurn() {
        return isTuiMode()
                && tuiRuntime != null
                && !(tuiRuntime instanceof AppendOnlyTuiRuntime)
                && terminal != null
                && terminal.supportsAnsi();
    }

    private void stopTuiTurnAnimation() {
        tuiTurnAnimationRunning = false;
        Thread thread = tuiTurnAnimationThread;
        tuiTurnAnimationThread = null;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean useAlternateScreenTui() {
        return tuiConfig != null && tuiConfig.isUseAlternateScreen();
    }

    private boolean shouldRenderModelDelta(String delta) {
        if (useAlternateScreenTui()) {
            return true;
        }
        if (delta != null && (delta.indexOf('\n') >= 0 || delta.indexOf('\r') >= 0)) {
            lastNonAlternateScreenRenderAtMs = System.currentTimeMillis();
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastNonAlternateScreenRenderAtMs >= NON_ALTERNATE_SCREEN_RENDER_THROTTLE_MS) {
            lastNonAlternateScreenRenderAtMs = now;
            return true;
        }
        return false;
    }

    private TuiRenderContext buildTuiRenderContext() {
        return TuiRenderContext.builder()
                .provider(options.getProvider() == null ? null : options.getProvider().getPlatform())
                .protocol(protocol == null ? null : protocol.getValue())
                .model(options.getModel())
                .workspace(options.getWorkspace())
                .sessionStore(String.valueOf(sessionManager.getDirectory()))
                .sessionMode(options.isNoSession() ? "memory-only" : "persistent")
                .approvalMode(options.getApprovalMode() == null ? null : options.getApprovalMode().getValue())
                .terminalRows(terminal == null ? 0 : terminal.getTerminalRows())
                .terminalColumns(terminal == null ? 0 : terminal.getTerminalColumns())
                .build();
    }

    private void closeQuietly(ManagedCodingSession session) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (Exception ex) {
            terminal.errorln("Failed to close session: " + ex.getMessage());
        }
    }

    private void closeMcpRuntimeQuietly(CliMcpRuntimeManager runtimeManager) {
        if (runtimeManager == null) {
            return;
        }
        try {
            runtimeManager.close();
        } catch (Exception ex) {
            terminal.errorln("Failed to close MCP runtime: " + ex.getMessage());
        }
    }

    private void refreshSessionContext(ManagedCodingSession session) {
        if (!(terminal instanceof JlineShellTerminalIO)) {
            return;
        }
        ((JlineShellTerminalIO) terminal).updateSessionContext(
                session == null ? null : session.getSessionId(),
                session == null ? options.getModel() : session.getModel(),
                session == null ? options.getWorkspace() : session.getWorkspace()
        );
        ((JlineShellTerminalIO) terminal).showIdle("Enter a prompt or /command");
    }

    private void attachCodingTaskEventBridge() {
        CodingRuntime nextRuntime = agent == null ? null : agent.getRuntime();
        if (bridgedRuntime == nextRuntime) {
            return;
        }
        detachCodingTaskEventBridge();
        if (nextRuntime == null || sessionManager == null) {
            return;
        }
        codingTaskEventBridge = new CodingTaskSessionEventBridge(sessionManager, new CodingTaskSessionEventBridge.SessionEventConsumer() {
            @Override
            public void onEvent(SessionEvent event) {
                handleCodingTaskSessionEvent(event);
            }
        });
        nextRuntime.addListener(codingTaskEventBridge);
        bridgedRuntime = nextRuntime;
    }

    private void detachCodingTaskEventBridge() {
        if (bridgedRuntime != null && codingTaskEventBridge != null) {
            bridgedRuntime.removeListener(codingTaskEventBridge);
        }
        bridgedRuntime = null;
        codingTaskEventBridge = null;
    }

    private void handleCodingTaskSessionEvent(SessionEvent event) {
        if (event == null || activeSession == null) {
            return;
        }
        if (!safeEquals(activeSession.getSessionId(), event.getSessionId())) {
            return;
        }
        refreshTuiEvents(activeSession);
        if (isTuiMode()) {
            renderTuiFromCache(activeSession);
        }
    }

    private String maskSecret(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private void appendCompactEvent(ManagedCodingSession session, CodingSessionCompactResult result, String turnId) {
        if (result == null) {
            return;
        }
        appendEvent(session, SessionEventType.COMPACT, turnId, null,
                (result.isAutomatic() ? "auto" : "manual")
                        + " compact " + result.getEstimatedTokensBefore() + "->" + result.getEstimatedTokensAfter() + " tokens",
                payloadOf(
                        "automatic", result.isAutomatic(),
                        "strategy", result.getStrategy(),
                        "beforeItemCount", result.getBeforeItemCount(),
                        "afterItemCount", result.getAfterItemCount(),
                        "estimatedTokensBefore", result.getEstimatedTokensBefore(),
                        "estimatedTokensAfter", result.getEstimatedTokensAfter(),
                        "compactedToolResultCount", result.getCompactedToolResultCount(),
                        "deltaItemCount", result.getDeltaItemCount(),
                        "checkpointReused", result.isCheckpointReused(),
                        "fallbackSummary", result.isFallbackSummary(),
                        "splitTurn", result.isSplitTurn(),
                        "summary", clip(result.getSummary(), options.isVerbose() ? 4000 : 1200),
                        "checkpointGoal", result.getCheckpoint() == null ? null : result.getCheckpoint().getGoal()
                ));
    }

    private void appendEvent(ManagedCodingSession session,
                             SessionEventType type,
                             String turnId,
                             Integer step,
                             String summary,
                             Map<String, Object> payload) {
        if (session == null || type == null) {
            return;
        }
        try {
            sessionManager.appendEvent(session.getSessionId(), SessionEvent.builder()
                    .sessionId(session.getSessionId())
                    .type(type)
                    .turnId(turnId)
                    .step(step)
                    .summary(summary)
                    .payload(payload)
                    .build());
            refreshTuiEvents(session);
        } catch (IOException ex) {
            if (options.isVerbose()) {
                terminal.errorln("Failed to append session event: " + ex.getMessage());
            }
        }
    }

    private SessionEventType resolveProcessEventType(String action, String status) {
        if ("start".equals(action)) {
            return SessionEventType.PROCESS_STARTED;
        }
        if ("stop".equals(action)) {
            return SessionEventType.PROCESS_STOPPED;
        }
        if ("EXITED".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status)) {
            return SessionEventType.PROCESS_STOPPED;
        }
        if ("status".equals(action) || "write".equals(action)) {
            return SessionEventType.PROCESS_UPDATED;
        }
        return null;
    }

    private void appendProcessEvent(ManagedCodingSession session,
                                    String turnId,
                                    Integer step,
                                    AgentToolCall call,
                                    AgentToolResult result) {
        if (call == null || result == null || !"bash".equals(call.getName())) {
            return;
        }
        JSONObject arguments = parseObject(call.getArguments());
        String action = arguments == null || isBlank(arguments.getString("action")) ? "exec" : arguments.getString("action");
        if ("exec".equals(action) || "logs".equals(action) || "list".equals(action)) {
            return;
        }

        JSONObject process = extractProcessObject(action, result.getOutput());
        String processId = process == null ? null : process.getString("processId");
        String status = process == null ? null : process.getString("status");
        SessionEventType eventType = resolveProcessEventType(action, status);
        if (eventType == null) {
            return;
        }

        appendEvent(session, eventType, turnId, step,
                buildProcessSummary(eventType, processId, status),
                payloadOf(
                        "action", action,
                        "processId", processId,
                        "status", status,
                        "command", process == null ? arguments.getString("command") : process.getString("command"),
                        "workingDirectory", process == null ? arguments.getString("cwd") : process.getString("workingDirectory"),
                        "output", clip(result.getOutput(), options.isVerbose() ? 4000 : 1200)
                ));
    }

    private JSONObject extractProcessObject(String action, String output) {
        if (isBlank(output)) {
            return null;
        }
        try {
            if ("write".equals(action)) {
                JSONObject root = JSON.parseObject(output);
                return root == null ? null : root.getJSONObject("process");
            }
            if ("start".equals(action) || "status".equals(action) || "stop".equals(action)) {
                return JSON.parseObject(output);
            }
            if ("list".equals(action)) {
                JSONArray array = JSON.parseArray(output);
                return array == null || array.isEmpty() ? null : array.getJSONObject(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String buildProcessSummary(SessionEventType eventType, String processId, String status) {
        String normalizedProcessId = isBlank(processId) ? "unknown-process" : processId;
        switch (eventType) {
            case PROCESS_STARTED:
                return "process started: " + normalizedProcessId;
            case PROCESS_STOPPED:
                return "process stopped: " + normalizedProcessId;
            case PROCESS_UPDATED:
                return "process updated: " + normalizedProcessId + (isBlank(status) ? "" : " (" + status + ")");
            default:
                return normalizedProcessId;
        }
    }

    private Integer parseLimit(String raw) {
        if (isBlank(raw)) {
            return DEFAULT_EVENT_LIMIT;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value <= 0 ? DEFAULT_EVENT_LIMIT : value;
        } catch (NumberFormatException ex) {
            emitError("Invalid event limit: " + raw + ", using " + DEFAULT_EVENT_LIMIT);
            return DEFAULT_EVENT_LIMIT;
        }
    }

    private String resolveCompactMode(SessionEvent event) {
        if (event == null) {
            return "unknown";
        }
        Map<String, Object> payload = event.getPayload();
        if (hasPayloadKey(payload, "automatic")) {
            return payloadBoolean(payload, "automatic") ? "auto" : "manual";
        }
        String summary = event.getSummary();
        if (!isBlank(summary) && summary.toLowerCase(Locale.ROOT).startsWith("auto")) {
            return "auto";
        }
        return "manual";
    }

    private String formatCompactDelta(Integer before, Integer after) {
        if (before == null || after == null) {
            return "n/a";
        }
        return before + "->" + after;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    private Integer payloadInt(Map<String, Object> payload, String key) {
        if (payload == null || isBlank(key)) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(Integer.parseInt(((String) value).trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean payloadBoolean(Map<String, Object> payload, String key) {
        if (payload == null || isBlank(key)) {
            return false;
        }
        Object value = payload.get(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return false;
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
            String text = safeTrimToNull(paragraph);
            if (isBlank(text)) {
                continue;
            }
            while (!isBlank(text)) {
                int width = firstLine ? firstWidth : continuationWidth;
                int split = findWrapIndex(text, width);
                lines.add((firstLine ? first : continuation) + text.substring(0, split).trim());
                text = text.substring(split).trim();
                firstLine = false;
            }
        }
        return lines;
    }

    private String safeTrimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
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

    private List<String> normalizeToolPreviewLines(String toolName, String status, List<String> previewLines) {
        if (previewLines == null || previewLines.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> normalized = new ArrayList<String>();
        for (String previewLine : previewLines) {
            String candidate = stripPreviewLabel(previewLine);
            if (isBlank(candidate)) {
                continue;
            }
            if ("bash".equals(toolName)) {
                if ("pending".equalsIgnoreCase(status)) {
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
        if (isBlank(previewLine)) {
            return null;
        }
        String value = previewLine.trim();
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

    private boolean hasPayloadKey(Map<String, Object> payload, String key) {
        return payload != null && !isBlank(key) && payload.containsKey(key);
    }

    private JSONObject parseObject(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return JSON.parseObject(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String[] toPanelLines(String text, int maxChars, int maxLines) {
        if (isBlank(text)) {
            return new String[]{"(none)"};
        }
        String[] rawLines = text.replace("\r", "").split("\n");
        List<String> lines = new ArrayList<String>();
        for (String rawLine : rawLines) {
            if (lines.size() >= maxLines) {
                lines.add("...");
                break;
            }
            lines.add(clip(rawLine, maxChars));
        }
        return lines.toArray(new String[0]);
    }

    private Map<String, Object> payloadOf(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (pairs == null) {
            return payload;
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object key = pairs[i];
            if (key != null) {
                payload.put(String.valueOf(key), pairs[i + 1]);
            }
        }
        return payload;
    }

    private String newTurnId() {
        return "turn_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String formatTimestamp(long epochMs) {
        if (epochMs <= 0) {
            return "unknown";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMs));
    }

    private String extractCommandArgument(String command) {
        if (isBlank(command)) {
            return null;
        }
        int firstSpace = command.indexOf(' ');
        if (firstSpace < 0 || firstSpace + 1 >= command.length()) {
            return null;
        }
        return command.substring(firstSpace + 1).trim();
    }

    private String renderStatusOutput(ManagedCodingSession session, CodingSessionSnapshot snapshot) {
        if (session == null) {
            return "status: (none)";
        }
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(null, null, null, null, null, env, properties);
        StringBuilder builder = new StringBuilder();
        builder.append("status:\n");
        builder.append("- session=").append(session.getSessionId()).append('\n');
        builder.append("- provider=").append(session.getProvider())
                .append(", protocol=").append(session.getProtocol())
                .append(", model=").append(session.getModel()).append('\n');
        builder.append("- profile=").append(firstNonBlank(resolved.getActiveProfile(), resolved.getEffectiveProfile(), "(none)"))
                .append(", modelOverride=").append(firstNonBlank(resolved.getModelOverride(), "(none)")).append('\n');
        builder.append("- workspace=").append(session.getWorkspace()).append('\n');
        builder.append("- mode=").append(options.isNoSession() ? "memory-only" : "persistent")
                .append(", memory=").append(snapshot == null ? 0 : snapshot.getMemoryItemCount())
                .append(", activeProcesses=").append(snapshot == null ? 0 : snapshot.getActiveProcessCount())
                .append(", restoredProcesses=").append(snapshot == null ? 0 : snapshot.getRestoredProcessCount())
                .append(", tokens=").append(snapshot == null ? 0 : snapshot.getEstimatedContextTokens()).append('\n');
        builder.append("- stream=").append(streamEnabled ? "on" : "off").append('\n');
        String mcpSummary = renderMcpSummary();
        if (!isBlank(mcpSummary)) {
            builder.append("- mcp=").append(mcpSummary).append('\n');
        }
        builder.append("- checkpointGoal=").append(clip(snapshot == null ? null : snapshot.getCheckpointGoal(), 120)).append('\n');
        builder.append("- compact=").append(firstNonBlank(snapshot == null ? null : snapshot.getLastCompactMode(), "none"));
        return builder.toString().trim();
    }

    private ManagedCodingSession handleStreamCommand(ManagedCodingSession session, String argument) throws Exception {
        String normalized = argument == null ? "" : argument.trim().toLowerCase(Locale.ROOT);
        if (isBlank(normalized)) {
            emitOutput(renderStreamOutput());
            return session;
        }
        if ("on".equals(normalized)) {
            if (!streamEnabled) {
                CodeCommandOptions nextOptions = options.withStream(true);
                ManagedCodingSession rebound = switchSessionRuntime(session, nextOptions);
                emitOutput(renderStreamOutput());
                persistSession(rebound, false);
                return rebound;
            }
            emitOutput(renderStreamOutput());
            return session;
        }
        if ("off".equals(normalized)) {
            if (streamEnabled) {
                CodeCommandOptions nextOptions = options.withStream(false);
                ManagedCodingSession rebound = switchSessionRuntime(session, nextOptions);
                emitOutput(renderStreamOutput());
                persistSession(rebound, false);
                return rebound;
            }
            emitOutput(renderStreamOutput());
            return session;
        }
        emitError("Unknown /stream option: " + argument + ". Use /stream, /stream on, or /stream off.");
        return session;
    }

    private String renderStreamOutput() {
        StringBuilder builder = new StringBuilder();
        builder.append("stream:\n");
        builder.append("- status=").append(streamEnabled ? "on" : "off").append('\n');
        builder.append("- scope=current CLI session\n");
        builder.append("- request=").append(streamEnabled ? "stream=true" : "stream=false").append('\n');
        builder.append("- behavior=").append(streamEnabled
                ? "provider responses stream incrementally and assistant text renders incrementally"
                : "provider responses return as completed payloads and assistant text renders as completed blocks");
        return builder.toString().trim();
    }

    private String renderSessionOutput(ManagedCodingSession session) {
        CodingSessionDescriptor descriptor = session == null ? null : session.toDescriptor();
        if (descriptor == null) {
            return "session: (none)";
        }
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(null, null, null, null, null, env, properties);
        CodingSessionSnapshot snapshot = session.getSession() == null ? null : session.getSession().snapshot();
        StringBuilder builder = new StringBuilder();
        builder.append("session:\n");
        builder.append("- id=").append(descriptor.getSessionId()).append('\n');
        builder.append("- root=").append(descriptor.getRootSessionId())
                .append(", parent=").append(firstNonBlank(descriptor.getParentSessionId(), "(none)")).append('\n');
        builder.append("- provider=").append(descriptor.getProvider())
                .append(", protocol=").append(descriptor.getProtocol())
                .append(", model=").append(descriptor.getModel()).append('\n');
        builder.append("- profile=").append(firstNonBlank(resolved.getActiveProfile(), resolved.getEffectiveProfile(), "(none)"))
                .append(", modelOverride=").append(firstNonBlank(resolved.getModelOverride(), "(none)")).append('\n');
        builder.append("- workspace=").append(descriptor.getWorkspace())
                .append(", mode=").append(options.isNoSession() ? "memory-only" : "persistent").append('\n');
        builder.append("- created=").append(formatTimestamp(descriptor.getCreatedAtEpochMs()))
                .append(", updated=").append(formatTimestamp(descriptor.getUpdatedAtEpochMs())).append('\n');
        builder.append("- memory=").append(descriptor.getMemoryItemCount())
                .append(", processes=").append(descriptor.getProcessCount())
                .append(" (active=").append(descriptor.getActiveProcessCount())
                .append(", restored=").append(descriptor.getRestoredProcessCount()).append(")").append('\n');
        String mcpSummary = renderMcpSummary();
        if (!isBlank(mcpSummary)) {
            builder.append("- mcp=").append(mcpSummary).append('\n');
        }
        builder.append("- tokens=").append(snapshot == null ? 0 : snapshot.getEstimatedContextTokens()).append('\n');
        builder.append("- checkpoint=").append(clip(snapshot == null ? null : snapshot.getCheckpointGoal(), 160)).append('\n');
        builder.append("- compact=").append(firstNonBlank(snapshot == null ? null : snapshot.getLastCompactMode(), "none")).append('\n');
        builder.append("- summary=").append(clip(descriptor.getSummary(), 220));
        return builder.toString().trim();
    }

    private String renderMcpSummary() {
        if (mcpRuntimeManager == null || !mcpRuntimeManager.hasStatuses()) {
            return null;
        }
        int connected = 0;
        int errors = 0;
        int paused = 0;
        int disabled = 0;
        int missing = 0;
        int tools = 0;
        for (CliMcpStatusSnapshot status : mcpRuntimeManager.getStatuses()) {
            if (status == null) {
                continue;
            }
            tools += Math.max(0, status.getToolCount());
            if (CliMcpRuntimeManager.STATE_CONNECTED.equals(status.getState())) {
                connected++;
            } else if (CliMcpRuntimeManager.STATE_ERROR.equals(status.getState())) {
                errors++;
            } else if (CliMcpRuntimeManager.STATE_PAUSED.equals(status.getState())) {
                paused++;
            } else if (CliMcpRuntimeManager.STATE_DISABLED.equals(status.getState())) {
                disabled++;
            } else if (CliMcpRuntimeManager.STATE_MISSING.equals(status.getState())) {
                missing++;
            }
        }
        return "connected " + connected
                + ", errors " + errors
                + ", paused " + paused
                + ", disabled " + disabled
                + ", missing " + missing
                + ", tools " + tools;
    }

    private String renderProvidersOutput() {
        CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
        CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
        if (providersConfig.getProfiles().isEmpty()) {
            return "providers: (none)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("providers:\n");
        List<String> names = new ArrayList<String>(providersConfig.getProfiles().keySet());
        Collections.sort(names);
        for (String name : names) {
            CliProviderProfile profile = providersConfig.getProfiles().get(name);
            builder.append("- ").append(name);
            if (name.equals(workspaceConfig.getActiveProfile())) {
                builder.append(" [active]");
            }
            if (name.equals(providersConfig.getDefaultProfile())) {
                builder.append(" [default]");
            }
            builder.append(" | provider=").append(profile == null ? null : profile.getProvider());
            builder.append(", protocol=").append(profile == null ? null : profile.getProtocol());
            builder.append(", model=").append(profile == null ? null : profile.getModel());
            if (!isBlank(profile == null ? null : profile.getBaseUrl())) {
                builder.append(", baseUrl=").append(profile.getBaseUrl());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String renderCurrentProviderOutput() {
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(null, null, null, null, null, env, properties);
        StringBuilder builder = new StringBuilder();
        builder.append("provider:\n");
        builder.append("- activeProfile=").append(firstNonBlank(resolved.getActiveProfile(), "(none)")).append('\n');
        builder.append("- defaultProfile=").append(firstNonBlank(resolved.getDefaultProfile(), "(none)")).append('\n');
        builder.append("- effectiveProfile=").append(firstNonBlank(resolved.getEffectiveProfile(), "(none)")).append('\n');
        builder.append("- provider=").append(options.getProvider() == null ? null : options.getProvider().getPlatform())
                .append(", protocol=").append(protocol == null ? null : protocol.getValue())
                .append(", model=").append(options.getModel()).append('\n');
        builder.append("- baseUrl=").append(firstNonBlank(options.getBaseUrl(), "(default)")).append('\n');
        builder.append("- apiKey=").append(isBlank(options.getApiKey()) ? "(missing)" : maskSecret(options.getApiKey())).append('\n');
        builder.append("- store=").append(providerConfigManager.globalProvidersPath());
        return builder.toString().trim();
    }

    private String renderModelOutput() {
        CliResolvedProviderConfig resolved = providerConfigManager.resolve(null, null, null, null, null, env, properties);
        StringBuilder builder = new StringBuilder();
        builder.append("model:\n");
        builder.append("- current=").append(options.getModel()).append('\n');
        builder.append("- override=").append(firstNonBlank(resolved.getModelOverride(), "(none)")).append('\n');
        builder.append("- profile=").append(firstNonBlank(resolved.getEffectiveProfile(), "(none)")).append('\n');
        builder.append("- workspaceConfig=").append(providerConfigManager.workspaceConfigPath());
        return builder.toString().trim();
    }

    private String renderSkillsOutput(ManagedCodingSession session, String argument) {
        WorkspaceContext workspaceContext = session == null || session.getSession() == null
                ? null
                : session.getSession().getWorkspaceContext();
        if (workspaceContext == null) {
            return "skills: (none)";
        }
        List<CodingSkillDescriptor> skills = workspaceContext.getAvailableSkills();
        if (!isBlank(argument)) {
            CodingSkillDescriptor selected = findSkill(skills, argument);
            if (selected == null) {
                return "skills: unknown skill `" + argument.trim() + "`";
            }
            return renderSkillDetailOutput(selected, workspaceContext);
        }
        List<String> roots = resolveSkillRoots(workspaceContext);
        StringBuilder builder = new StringBuilder();
        builder.append("skills:\n");
        builder.append("- count=").append(skills == null ? 0 : skills.size()).append('\n');
        builder.append("- workspaceConfig=").append(providerConfigManager.workspaceConfigPath()).append('\n');
        builder.append("- roots=").append(joinWithComma(roots)).append('\n');
        if (skills == null || skills.isEmpty()) {
            builder.append("- entries=(none)");
            return builder.toString().trim();
        }
        for (CodingSkillDescriptor skill : skills) {
            if (skill == null) {
                continue;
            }
            builder.append("- ").append(firstNonBlank(skill.getName(), "skill"));
            builder.append(" | source=").append(firstNonBlank(skill.getSource(), "unknown"));
            builder.append(" | path=").append(firstNonBlank(skill.getSkillFilePath(), "(missing)"));
            builder.append(" | description=").append(firstNonBlank(skill.getDescription(), "No description available."));
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private CodingSkillDescriptor findSkill(List<CodingSkillDescriptor> skills, String name) {
        if (skills == null || isBlank(name)) {
            return null;
        }
        String normalized = name.trim();
        for (CodingSkillDescriptor skill : skills) {
            if (skill == null || isBlank(skill.getName())) {
                continue;
            }
            if (skill.getName().trim().equalsIgnoreCase(normalized)) {
                return skill;
            }
        }
        return null;
    }

    private String renderSkillDetailOutput(CodingSkillDescriptor skill, WorkspaceContext workspaceContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("skill:\n");
        builder.append("- name=").append(firstNonBlank(skill == null ? null : skill.getName(), "skill")).append('\n');
        builder.append("- source=").append(firstNonBlank(skill == null ? null : skill.getSource(), "unknown")).append('\n');
        builder.append("- path=").append(firstNonBlank(skill == null ? null : skill.getSkillFilePath(), "(missing)")).append('\n');
        builder.append("- description=").append(firstNonBlank(skill == null ? null : skill.getDescription(), "No description available.")).append('\n');
        builder.append("- roots=").append(joinWithComma(resolveSkillRoots(workspaceContext)));
        return builder.toString().trim();
    }

    private String renderAgentsOutput(ManagedCodingSession session, String argument) {
        CodingAgentDefinitionRegistry registry = agent == null ? null : agent.getDefinitionRegistry();
        List<CodingAgentDefinition> definitions = registry == null ? null : registry.listDefinitions();
        if (!isBlank(argument)) {
            CodingAgentDefinition selected = findAgent(definitions, argument);
            if (selected == null) {
                return "agents: unknown agent `" + argument.trim() + "`";
            }
            return renderAgentDetailOutput(selected);
        }
        List<String> roots = resolveAgentRoots();
        StringBuilder builder = new StringBuilder();
        builder.append("agents:\n");
        builder.append("- count=").append(definitions == null ? 0 : definitions.size()).append('\n');
        builder.append("- workspaceConfig=").append(providerConfigManager.workspaceConfigPath()).append('\n');
        builder.append("- roots=").append(joinWithComma(roots)).append('\n');
        if (definitions == null || definitions.isEmpty()) {
            builder.append("- entries=(none)");
            return builder.toString().trim();
        }
        for (CodingAgentDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            builder.append("- ").append(firstNonBlank(definition.getName(), "agent"));
            builder.append(" | tool=").append(firstNonBlank(definition.getToolName(), "(none)"));
            builder.append(" | model=").append(firstNonBlank(definition.getModel(), "(inherit)"));
            builder.append(" | background=").append(definition.isBackground());
            builder.append(" | tools=").append(renderAllowedTools(definition));
            builder.append(" | description=").append(firstNonBlank(definition.getDescription(), "No description available."));
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private List<String> resolveSkillRoots(WorkspaceContext workspaceContext) {
        if (workspaceContext == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> roots = new LinkedHashSet<String>();
        roots.add(workspaceContext.getRoot().resolve(".ai4j").resolve("skills").toAbsolutePath().normalize().toString());
        String userHome = System.getProperty("user.home");
        if (!isBlank(userHome)) {
            roots.add(Paths.get(userHome).resolve(".ai4j").resolve("skills").toAbsolutePath().normalize().toString());
        }
        if (workspaceContext.getSkillDirectories() != null) {
            for (String configuredRoot : workspaceContext.getSkillDirectories()) {
                if (isBlank(configuredRoot)) {
                    continue;
                }
                Path root = Paths.get(configuredRoot);
                if (!root.isAbsolute()) {
                    root = workspaceContext.getRoot().resolve(configuredRoot);
                }
                roots.add(root.toAbsolutePath().normalize().toString());
            }
        }
        return new ArrayList<String>(roots);
    }

    private CodingAgentDefinition findAgent(List<CodingAgentDefinition> definitions, String nameOrToolName) {
        if (definitions == null || isBlank(nameOrToolName)) {
            return null;
        }
        String normalized = nameOrToolName.trim();
        for (CodingAgentDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            if ((!isBlank(definition.getName()) && definition.getName().trim().equalsIgnoreCase(normalized))
                    || (!isBlank(definition.getToolName()) && definition.getToolName().trim().equalsIgnoreCase(normalized))) {
                return definition;
            }
        }
        return null;
    }

    private String renderAgentDetailOutput(CodingAgentDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append("agent:\n");
        builder.append("- name=").append(firstNonBlank(definition == null ? null : definition.getName(), "agent")).append('\n');
        builder.append("- tool=").append(firstNonBlank(definition == null ? null : definition.getToolName(), "(none)")).append('\n');
        builder.append("- model=").append(firstNonBlank(definition == null ? null : definition.getModel(), "(inherit)")).append('\n');
        builder.append("- sessionMode=").append(definition == null ? null : definition.getSessionMode()).append('\n');
        builder.append("- isolationMode=").append(definition == null ? null : definition.getIsolationMode()).append('\n');
        builder.append("- memoryScope=").append(definition == null ? null : definition.getMemoryScope()).append('\n');
        builder.append("- approvalMode=").append(definition == null ? null : definition.getApprovalMode()).append('\n');
        builder.append("- background=").append(definition != null && definition.isBackground()).append('\n');
        builder.append("- tools=").append(renderAllowedTools(definition)).append('\n');
        builder.append("- description=").append(firstNonBlank(definition == null ? null : definition.getDescription(), "No description available.")).append('\n');
        builder.append("- roots=").append(joinWithComma(resolveAgentRoots())).append('\n');
        builder.append("- instructions=").append(firstNonBlank(definition == null ? null : definition.getInstructions(), "(none)"));
        return builder.toString().trim();
    }

    private List<String> resolveAgentRoots() {
        CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
        CliCodingAgentRegistry registry = new CliCodingAgentRegistry(
                Paths.get(firstNonBlank(options == null ? null : options.getWorkspace(), ".")),
                workspaceConfig == null ? null : workspaceConfig.getAgentDirectories()
        );
        List<Path> roots = registry.listRoots();
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>(roots.size());
        for (Path root : roots) {
            if (root != null) {
                values.add(root.toAbsolutePath().normalize().toString());
            }
        }
        return values;
    }

    private String renderAllowedTools(CodingAgentDefinition definition) {
        if (definition == null || definition.getAllowedToolNames() == null || definition.getAllowedToolNames().isEmpty()) {
            return "(inherit/all available)";
        }
        StringBuilder builder = new StringBuilder();
        for (String toolName : definition.getAllowedToolNames()) {
            if (isBlank(toolName)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(toolName.trim());
        }
        return builder.length() == 0 ? "(inherit/all available)" : builder.toString();
    }

    private String joinWithComma(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.length() == 0 ? "(none)" : builder.toString();
    }

    private String renderCheckpointOutput(CodingSessionCheckpoint checkpoint) {
        if (checkpoint == null) {
            return "checkpoint: (none)";
        }
        return "checkpoint:\n" + firstNonBlank(CodingSessionCheckpointFormatter.render(checkpoint), "(none)");
    }

    private String renderSessionsOutput(List<CodingSessionDescriptor> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "sessions: (none)";
        }
        StringBuilder builder = new StringBuilder("sessions:\n");
        for (CodingSessionDescriptor session : sessions) {
            builder.append("- ").append(session.getSessionId())
                    .append(" | root=").append(clip(session.getRootSessionId(), 24))
                    .append(" | parent=").append(clip(firstNonBlank(session.getParentSessionId(), "-"), 24))
                    .append(" | updated=").append(formatTimestamp(session.getUpdatedAtEpochMs()))
                    .append(" | memory=").append(session.getMemoryItemCount())
                    .append(" | processes=").append(session.getProcessCount())
                    .append(" | ").append(clip(session.getSummary(), 120))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String renderEventsOutput(List<SessionEvent> events) {
        if (events == null || events.isEmpty()) {
            return "events: (none)";
        }
        StringBuilder builder = new StringBuilder("events:\n");
        for (SessionEvent event : events) {
            builder.append("- ").append(formatTimestamp(event.getTimestamp()))
                    .append(" | ").append(event.getType())
                    .append(event.getStep() == null ? "" : " | step=" + event.getStep())
                    .append(" | ").append(clip(event.getSummary(), 160))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String renderReplayOutput(List<String> replayLines) {
        if (replayLines == null || replayLines.isEmpty()) {
            return "replay: (none)";
        }
        StringBuilder builder = new StringBuilder("replay:\n");
        for (String replayLine : replayLines) {
            builder.append(replayLine).append('\n');
        }
        return builder.toString().trim();
    }

    private String renderProcessesOutput(List<BashProcessInfo> processes) {
        if (processes == null || processes.isEmpty()) {
            return "processes: (none)";
        }
        StringBuilder builder = new StringBuilder("processes:\n");
        for (BashProcessInfo process : processes) {
            builder.append("- ").append(process.getProcessId())
                    .append(" | status=").append(process.getStatus())
                    .append(" | mode=").append(process.isControlAvailable() ? "live" : "metadata-only")
                    .append(" | restored=").append(process.isRestored())
                    .append(" | cwd=").append(clip(process.getWorkingDirectory(), 48))
                    .append(" | cmd=").append(clip(process.getCommand(), 72))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String renderProcessStatusOutput(BashProcessInfo processInfo) {
        if (processInfo == null) {
            return "process status: (none)";
        }
        StringBuilder builder = new StringBuilder("process status:\n");
        appendProcessSummary(builder, processInfo);
        return builder.toString().trim();
    }

    private String renderProcessDetailsOutput(BashProcessInfo processInfo, BashProcessLogChunk logs) {
        StringBuilder builder = new StringBuilder(renderProcessStatusOutput(processInfo));
        String content = logs == null ? null : logs.getContent();
        if (!isBlank(content)) {
            builder.append('\n').append('\n').append("process logs:\n").append(content.trim());
        }
        return builder.toString().trim();
    }

    private String renderPanel(String title, String... lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("----------------------------------------------------------------").append('\n');
        builder.append(title).append('\n');
        if (lines != null) {
            for (String line : lines) {
                if (!isBlank(line)) {
                    builder.append(line).append('\n');
                }
            }
        }
        builder.append("----------------------------------------------------------------");
        return builder.toString();
    }

    private String resolveToolCallKey(AgentToolCall call) {
        if (call == null) {
            return UUID.randomUUID().toString();
        }
        if (!isBlank(call.getCallId())) {
            return call.getCallId();
        }
        return firstNonBlank(call.getName(), "tool") + "::" + firstNonBlank(call.getArguments(), "");
    }

    private String resolveToolResultKey(AgentToolResult result) {
        if (result == null) {
            return UUID.randomUUID().toString();
        }
        if (!isBlank(result.getCallId())) {
            return result.getCallId();
        }
        return firstNonBlank(result.getName(), "tool");
    }

    private TuiAssistantToolView buildPendingToolView(AgentToolCall call) {
        JSONObject arguments = parseObject(call == null ? null : call.getArguments());
        String toolName = call == null ? null : call.getName();
        String title = buildToolTitle(toolName, arguments);
        String detail = buildPendingToolDetail(toolName, arguments);
        return TuiAssistantToolView.builder()
                .callId(call == null ? null : call.getCallId())
                .toolName(toolName)
                .status("pending")
                .title(title)
                .detail(detail)
                .previewLines(buildPendingToolPreviewLines(toolName, arguments))
                .build();
    }

    private TuiAssistantToolView buildCompletedToolView(AgentToolCall call, AgentToolResult result) {
        String toolName = call != null && !isBlank(call.getName()) ? call.getName() : (result == null ? null : result.getName());
        JSONObject arguments = parseObject(call == null ? null : call.getArguments());
        JSONObject output = parseObject(result == null ? null : result.getOutput());
        return TuiAssistantToolView.builder()
                .callId(call == null ? null : call.getCallId())
                .toolName(toolName)
                .status(isToolError(result, output) ? "error" : "done")
                .title(buildToolTitle(toolName, arguments))
                .detail(buildCompletedToolDetail(toolName, arguments, output, result == null ? null : result.getOutput()))
                .previewLines(buildToolPreviewLines(toolName, arguments, output, result == null ? null : result.getOutput()))
                .build();
    }

    private boolean isToolError(AgentToolResult result, JSONObject output) {
        return !isBlank(extractToolError(result == null ? null : result.getOutput(), output));
    }

    private boolean isApprovalRejectedToolResult(AgentToolResult result) {
        return isApprovalRejectedToolError(result == null ? null : result.getOutput(), null);
    }

    private boolean isApprovalRejectedToolError(String rawOutput, JSONObject output) {
        String error = extractRawToolError(rawOutput, output);
        return !isBlank(error) && error.startsWith(CliToolApprovalDecorator.APPROVAL_REJECTED_PREFIX);
    }

    private String extractToolError(String rawOutput, JSONObject output) {
        String rawError = extractRawToolError(rawOutput, output);
        if (isBlank(rawError)) {
            return null;
        }
        return stripApprovalRejectedPrefix(rawError);
    }

    private String extractRawToolError(String rawOutput, JSONObject output) {
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
        if (output == null) {
            return null;
        }
        String error = output.getString("error");
        return isBlank(error) ? null : error.trim();
    }

    private String stripApprovalRejectedPrefix(String error) {
        if (isBlank(error)) {
            return null;
        }
        if (!error.startsWith(CliToolApprovalDecorator.APPROVAL_REJECTED_PREFIX)) {
            return error;
        }
        String stripped = error.substring(CliToolApprovalDecorator.APPROVAL_REJECTED_PREFIX.length()).trim();
        return isBlank(stripped) ? "Tool call rejected by user" : stripped;
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
        if ("write_file".equals(toolName)) {
            return "write " + firstNonBlank(arguments == null ? null : arguments.getString("path"), "(path)");
        }
        if ("apply_patch".equals(toolName)) {
            return "apply_patch";
        }
        String qualifiedToolName = qualifyMcpToolName(toolName);
        String argumentSummary = formatToolArgumentSummary(arguments);
        if (isBlank(argumentSummary)) {
            return qualifiedToolName;
        }
        return qualifiedToolName + "(" + argumentSummary + ")";
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
        if ("write_file".equals(toolName)) {
            return "Writing file content...";
        }
        if ("apply_patch".equals(toolName)) {
            return "Applying workspace patch...";
        }
        if (isMcpToolName(toolName)) {
            return "Calling MCP tool...";
        }
        return "Running tool...";
    }

    private List<String> buildPendingToolPreviewLines(String toolName, JSONObject arguments) {
        List<String> previewLines = new ArrayList<String>();
        if ("bash".equals(toolName)) {
            return previewLines;
        }
        if ("apply_patch".equals(toolName)) {
            previewLines.addAll(PatchSummaryFormatter.summarizePatchRequest(
                    arguments == null ? null : arguments.getString("patch"),
                    6
            ));
            return previewLines;
        }
        return previewLines;
    }

    private String buildCompletedToolDetail(String toolName,
                                            JSONObject arguments,
                                            JSONObject output,
                                            String rawOutput) {
        String toolError = extractToolError(rawOutput, output);
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
                String status = safeTrimToNull(output.getString("status"));
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
        if ("write_file".equals(toolName) && output != null) {
            String status = output.getBooleanValue("appended")
                    ? "appended"
                    : (output.getBooleanValue("created") ? "created" : "overwritten");
            return status + " | " + output.getIntValue("bytesWritten") + " bytes";
        }
        if ("apply_patch".equals(toolName) && output != null) {
            int filesChanged = output.getIntValue("filesChanged");
            int operationsApplied = output.getIntValue("operationsApplied");
            if (filesChanged <= 0 && operationsApplied <= 0) {
                return null;
            }
            if (operationsApplied > 0 && operationsApplied != filesChanged) {
                return filesChanged + " files changed, " + operationsApplied + " operations";
            }
            return filesChanged == 1 ? "1 file changed" : filesChanged + " files changed";
        }
        if (isMcpToolName(toolName)) {
            return null;
        }
        return clip(rawOutput, 96);
    }

    private boolean isMcpToolName(String toolName) {
        return mcpRuntimeManager != null && !isBlank(mcpRuntimeManager.findServerNameByToolName(toolName));
    }

    private String qualifyMcpToolName(String toolName) {
        String normalizedToolName = firstNonBlank(toolName, "tool");
        if (mcpRuntimeManager == null || isBlank(toolName)) {
            return normalizedToolName;
        }
        String serverName = mcpRuntimeManager.findServerNameByToolName(toolName);
        if (isBlank(serverName)) {
            return normalizedToolName;
        }
        return serverName + "." + normalizedToolName;
    }

    private String formatToolArgumentSummary(JSONObject arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<String>();
        int count = 0;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (entry == null || isBlank(entry.getKey())) {
                continue;
            }
            parts.add(entry.getKey() + "=" + formatInlineToolArgumentValue(entry.getValue()));
            count++;
            if (count >= 2) {
                break;
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (arguments.size() > count) {
            parts.add("...");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parts.get(i));
        }
        return clip(builder.toString(), 88);
    }

    private String formatInlineToolArgumentValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + clip((String) value, 48) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return clip(JSON.toJSONString(value), 48);
    }

    private List<String> buildToolPreviewLines(String toolName,
                                               JSONObject arguments,
                                               JSONObject output,
                                               String rawOutput) {
        List<String> previewLines = new ArrayList<String>();
        if (!isBlank(extractToolError(rawOutput, output))) {
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
        if ("write_file".equals(toolName) && output != null) {
            addPreviewLine(previewLines, "path", output.getString("resolvedPath"));
            return previewLines;
        }
        if ("apply_patch".equals(toolName) && output != null) {
            previewLines.addAll(PatchSummaryFormatter.summarizePatchResult(output, 8));
            if (!previewLines.isEmpty()) {
                return previewLines;
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
            return new ArrayList<String>();
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

    private final class TuiLiveTurnState {

        private TuiAssistantPhase phase = TuiAssistantPhase.IDLE;
        private Integer step;
        private String phaseDetail;
        private final StringBuilder reasoningBuffer = new StringBuilder();
        private final StringBuilder textBuffer = new StringBuilder();
        private final Map<String, TuiAssistantToolView> toolViews = new LinkedHashMap<String, TuiAssistantToolView>();
        private String textBeforeFinalOutput;
        private int persistedReasoningLength;
        private int persistedTextLength;
        private int liveReasoningLength;
        private int liveTextLength;
        private long updatedAtEpochMs;
        private int animationTick;

        private synchronized void beginTurn(String input) {
            phase = TuiAssistantPhase.THINKING;
            step = null;
            phaseDetail = isBlank(input) ? "Waiting for model output..." : "Thinking about: " + clip(input, 72);
            reasoningBuffer.setLength(0);
            textBuffer.setLength(0);
            toolViews.clear();
            textBeforeFinalOutput = null;
            persistedReasoningLength = 0;
            persistedTextLength = 0;
            liveReasoningLength = 0;
            liveTextLength = 0;
            touch();
        }

        private synchronized void onStepStart(Integer step) {
            this.step = step;
            if (phase != TuiAssistantPhase.COMPLETE && phase != TuiAssistantPhase.ERROR) {
                phase = TuiAssistantPhase.THINKING;
                phaseDetail = "Waiting for model output...";
                touch();
            }
        }

        private synchronized void onModelDelta(Integer step, String delta) {
            this.step = step;
            if (!isBlank(delta)) {
                textBuffer.append(delta);
            }
            phase = TuiAssistantPhase.GENERATING;
            phaseDetail = "Streaming model output...";
            touch();
        }

        private synchronized void onReasoningDelta(Integer step, String delta) {
            this.step = step;
            if (!isBlank(delta)) {
                reasoningBuffer.append(delta);
            }
            if (phase != TuiAssistantPhase.GENERATING) {
                phase = TuiAssistantPhase.THINKING;
            }
            phaseDetail = "Streaming reasoning...";
            touch();
        }

        private synchronized void onRetry(Integer step, String detail) {
            this.step = step;
            phase = TuiAssistantPhase.THINKING;
            phaseDetail = firstNonBlank(detail, "Retrying model request...");
            touch();
        }

        private synchronized void onToolCall(Integer step, TuiAssistantToolView toolView) {
            this.step = step;
            if (toolView != null) {
                toolViews.put(firstNonBlank(toolView.getCallId(), toolView.getToolName(), UUID.randomUUID().toString()), toolView);
                phaseDetail = firstNonBlank(toolView.getDetail(), "Waiting for tool result...");
            }
            phase = TuiAssistantPhase.WAITING_TOOL_RESULT;
            touch();
        }

        private synchronized void onToolResult(Integer step, TuiAssistantToolView toolView) {
            this.step = step;
            if (toolView != null) {
                toolViews.put(firstNonBlank(toolView.getCallId(), toolView.getToolName(), UUID.randomUUID().toString()), toolView);
            }
            phase = toolView != null && "error".equalsIgnoreCase(toolView.getStatus())
                    ? TuiAssistantPhase.ERROR
                    : TuiAssistantPhase.THINKING;
            phaseDetail = phase == TuiAssistantPhase.ERROR
                    ? firstNonBlank(toolView == null ? null : toolView.getDetail(), "Tool execution failed.")
                    : "Tool finished, continuing...";
            touch();
        }

        private synchronized void onStepEnd(Integer step) {
            this.step = step;
            if (phase == TuiAssistantPhase.WAITING_TOOL_RESULT) {
                phase = TuiAssistantPhase.THINKING;
                phaseDetail = "Preparing next step...";
                touch();
            }
        }

        private synchronized void onFinalOutput(Integer step, String output) {
            this.step = step;
            if (!isBlank(output)) {
                String current = textBuffer.toString();
                textBeforeFinalOutput = current;
                if (isBlank(current)) {
                    textBuffer.setLength(0);
                    textBuffer.append(output);
                } else if (!output.equals(current)) {
                    if (output.startsWith(current)) {
                        textBuffer.setLength(0);
                        textBuffer.append(output);
                    } else {
                        textBuffer.setLength(0);
                        textBuffer.append(output);
                        persistedTextLength = 0;
                        if (liveTextLength > 0) {
                            liveTextLength = textBuffer.length();
                        } else {
                            liveTextLength = 0;
                        }
                    }
                }
            }
            phase = TuiAssistantPhase.COMPLETE;
            phaseDetail = "Turn complete.";
            touch();
        }

        private synchronized void onError(Integer step, String errorMessage) {
            this.step = step;
            phase = TuiAssistantPhase.ERROR;
            phaseDetail = firstNonBlank(errorMessage, "Agent run failed.");
            touch();
        }

        private synchronized void finishTurn(Integer step, String output) {
            this.step = step == null ? this.step : step;
            if (phase == TuiAssistantPhase.ERROR || phase == TuiAssistantPhase.COMPLETE) {
                return;
            }
            if (!isBlank(output)) {
                onFinalOutput(this.step, output);
                return;
            }
            phase = TuiAssistantPhase.COMPLETE;
            phaseDetail = "Turn complete.";
            touch();
        }

        private synchronized String flushPendingText() {
            String pending = pendingText();
            persistedTextLength = textBuffer.length();
            return pending;
        }

        private synchronized String flushPendingReasoning() {
            String pending = pendingReasoning();
            persistedReasoningLength = reasoningBuffer.length();
            return pending;
        }

        private synchronized String flushLiveReasoning() {
            String pending = pendingLiveReasoning();
            liveReasoningLength = reasoningBuffer.length();
            return pending;
        }

        private synchronized String flushLiveText() {
            String pending = pendingLiveText();
            liveTextLength = textBuffer.length();
            return pending;
        }

        private synchronized String pendingReasoning() {
            if (persistedReasoningLength >= reasoningBuffer.length()) {
                return "";
            }
            return reasoningBuffer.substring(Math.max(0, persistedReasoningLength));
        }

        private synchronized String pendingText() {
            if (persistedTextLength >= textBuffer.length()) {
                return "";
            }
            return textBuffer.substring(Math.max(0, persistedTextLength));
        }

        private synchronized String pendingLiveReasoning() {
            if (liveReasoningLength >= reasoningBuffer.length()) {
                return "";
            }
            return reasoningBuffer.substring(Math.max(0, liveReasoningLength));
        }

        private synchronized String pendingLiveText() {
            if (liveTextLength >= textBuffer.length()) {
                return "";
            }
            return textBuffer.substring(Math.max(0, liveTextLength));
        }

        private synchronized boolean hasPendingReasoning() {
            return !isBlank(pendingReasoning());
        }

        private synchronized boolean hasPendingText() {
            return !isBlank(pendingText());
        }

        private synchronized String currentText() {
            return textBuffer.toString();
        }

        private synchronized String textBeforeFinalOutput() {
            return textBeforeFinalOutput == null ? textBuffer.toString() : textBeforeFinalOutput;
        }

        private synchronized TuiAssistantViewModel toViewModel() {
            return TuiAssistantViewModel.builder()
                    .phase(phase)
                    .step(step)
                    .phaseDetail(phaseDetail)
                    .reasoningText(pendingReasoning())
                    .text(pendingText())
                    .updatedAtEpochMs(updatedAtEpochMs)
                    .animationTick(animationTick)
                    .tools(new ArrayList<TuiAssistantToolView>(toolViews.values()))
                    .build();
        }

        private synchronized boolean advanceAnimationTick() {
            if (!isSpinnerActive()) {
                return false;
            }
            if (animationTick == Integer.MAX_VALUE) {
                animationTick = 0;
            } else {
                animationTick++;
            }
            return true;
        }

        private synchronized boolean isSpinnerActive() {
            return phase == TuiAssistantPhase.THINKING
                    || phase == TuiAssistantPhase.GENERATING
                    || phase == TuiAssistantPhase.WAITING_TOOL_RESULT;
        }

        private void touch() {
            updatedAtEpochMs = System.currentTimeMillis();
            animationTick = 0;
        }
    }

    private final class MainBufferTurnPrinter {
        private TranscriptPrinter transcriptPrinter;
        private LiveTranscriptKind liveTranscriptKind;
        private boolean liveTranscriptAtLineStart = true;
        private final StringBuilder assistantLineBuffer = new StringBuilder();
        private boolean assistantInsideCodeBlock;
        private String assistantCodeBlockLanguage;

        private synchronized void beginTurn(String input) {
            finishLiveTranscript();
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.beginTurn(input);
            }
        }

        private synchronized void finishTurn() {
            finishLiveTranscript();
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.finishTurn();
            }
        }

        private synchronized void showThinking() {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.showThinking();
            }
        }

        private synchronized void showConnecting(String text) {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.showConnecting(text);
            }
        }

        private synchronized void showRetrying(String text, int attempt, int maxAttempts) {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.showRetrying(text, attempt, maxAttempts);
            }
        }

        private synchronized void showResponding() {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.showResponding();
            }
        }

        private synchronized void showStatus(String text) {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.showWorking(text);
            }
        }

        private synchronized void clearTransient() {
            finishLiveTranscript();
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.clearTransient();
            }
        }

        private synchronized void printSectionBreak() {
            finishLiveTranscript();
            transcriptPrinter().printSectionBreak();
        }

        private synchronized void printBlock(List<String> lines) {
            finishLiveTranscript();
            transcriptPrinter().printBlock(lines);
        }

        private synchronized void printAssistantBlock(String text) {
            finishLiveTranscript();
            JlineShellTerminalIO shellTerminal = shellTerminal();
            transcriptPrinter().beginStreamingBlock();
            if (shellTerminal != null) {
                beginAssistantBlockTracking();
                shellTerminal.printAssistantMarkdownBlock(text);
                return;
            }
            List<AssistantTranscriptRenderer.Line> lines = assistantTranscriptRenderer.render(text);
            if (lines.isEmpty()) {
                return;
            }
            CliThemeStyler fallbackStyler = new CliThemeStyler(tuiTheme, terminal.supportsAnsi());
            for (AssistantTranscriptRenderer.Line line : lines) {
                String renderedLine = line == null
                        ? ""
                        : (line.code()
                        ? fallbackStyler.styleTranscriptCodeLine(line.text(), line.language())
                        : fallbackStyler.styleTranscriptLine(line.text()));
                terminal.println(renderedLine);
            }
        }

        private synchronized boolean replaceAssistantBlock(String previousText, String replacementText) {
            finishLiveTranscript();
            JlineShellTerminalIO shellTerminal = shellTerminal();
            return shellTerminal != null && shellTerminal.rewriteAssistantBlock(shellTerminal.assistantBlockRows(), replacementText);
        }

        private synchronized void discardAssistantBlock() {
            if (liveTranscriptKind == LiveTranscriptKind.ASSISTANT && assistantInsideCodeBlock) {
                exitTranscriptCodeBlock();
            }
            liveTranscriptKind = null;
            liveTranscriptAtLineStart = true;
            assistantLineBuffer.setLength(0);
            assistantInsideCodeBlock = false;
            assistantCodeBlockLanguage = null;
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                // Do not rewrite already-printed transcript lines in the main
                // buffer. Once the terminal has scrolled, cursor-up clears can
                // erase visible history and make the viewport look like it
                // "refreshed". Keep the pre-tool assistant text and only
                // forget the tracked block so later output appends normally.
                shellTerminal.forgetAssistantBlock();
            }
        }

        private synchronized void streamAssistant(String delta) {
            streamLiveTranscript(LiveTranscriptKind.ASSISTANT, delta);
        }

        private synchronized void streamReasoning(String delta) {
            streamLiveTranscript(LiveTranscriptKind.REASONING, delta);
        }

        private JlineShellTerminalIO shellTerminal() {
            return terminal instanceof JlineShellTerminalIO ? (JlineShellTerminalIO) terminal : null;
        }

        private TranscriptPrinter transcriptPrinter() {
            if (transcriptPrinter == null) {
                transcriptPrinter = new TranscriptPrinter(terminal);
            }
            return transcriptPrinter;
        }

        private void streamLiveTranscript(LiveTranscriptKind kind, String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            ensureLiveTranscript(kind);
            if (kind == LiveTranscriptKind.ASSISTANT) {
                streamAssistantMarkdown(delta);
                return;
            }
            String normalized = delta.replace("\r", "");
            int start = 0;
            while (start <= normalized.length()) {
                int newlineIndex = normalized.indexOf('\n', start);
                String fragment = newlineIndex >= 0
                        ? normalized.substring(start, newlineIndex)
                        : normalized.substring(start);
                if (!fragment.isEmpty()) {
                    if (kind == LiveTranscriptKind.REASONING && liveTranscriptAtLineStart) {
                        emitLiveTranscriptFragment(kind, "Thinking: ");
                    }
                    emitLiveTranscriptFragment(kind, fragment);
                    liveTranscriptAtLineStart = false;
                }
                if (newlineIndex < 0) {
                    break;
                }
                terminal.println("");
                liveTranscriptAtLineStart = true;
                start = newlineIndex + 1;
            }
        }

        private void ensureLiveTranscript(LiveTranscriptKind kind) {
            if (kind == null) {
                return;
            }
            if (liveTranscriptKind != null && liveTranscriptKind != kind) {
                finishLiveTranscript();
            }
            if (liveTranscriptKind == null) {
                if (kind == LiveTranscriptKind.ASSISTANT) {
                    beginAssistantBlockTracking();
                }
                transcriptPrinter().beginStreamingBlock();
                liveTranscriptKind = kind;
                liveTranscriptAtLineStart = true;
            }
        }

        private void finishLiveTranscript() {
            if (liveTranscriptKind == null) {
                return;
            }
            if (liveTranscriptKind == LiveTranscriptKind.ASSISTANT) {
                flushAssistantRemainder();
            }
            if (assistantInsideCodeBlock) {
                exitTranscriptCodeBlock();
            }
            if (!liveTranscriptAtLineStart) {
                terminal.println("");
            }
            liveTranscriptKind = null;
            liveTranscriptAtLineStart = true;
            assistantLineBuffer.setLength(0);
            assistantInsideCodeBlock = false;
            assistantCodeBlockLanguage = null;
        }

        private void emitLiveTranscriptFragment(LiveTranscriptKind kind, String fragment) {
            if (fragment == null || fragment.isEmpty()) {
                return;
            }
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                if (kind == LiveTranscriptKind.REASONING) {
                    shellTerminal.printReasoningFragment(fragment);
                } else {
                    shellTerminal.printAssistantFragment(fragment);
                }
                return;
            }
            terminal.print(fragment);
        }

        private void streamAssistantMarkdown(String delta) {
            String normalized = delta.replace("\r", "");
            int index = 0;
            while (index < normalized.length()) {
                int newlineIndex = normalized.indexOf('\n', index);
                if (newlineIndex < 0) {
                    assistantLineBuffer.append(normalized.substring(index));
                    return;
                }
                assistantLineBuffer.append(normalized.substring(index, newlineIndex));
                emitCompletedAssistantLine();
                index = newlineIndex + 1;
            }
        }

        private void emitCompletedAssistantLine() {
            if (codexStyleBlockFormatter.isCodeFenceLine(assistantLineBuffer.toString())) {
                if (!assistantInsideCodeBlock) {
                    assistantInsideCodeBlock = true;
                    assistantCodeBlockLanguage = codexStyleBlockFormatter.codeFenceLanguage(assistantLineBuffer.toString());
                    enterTranscriptCodeBlock(assistantCodeBlockLanguage);
                } else {
                    assistantInsideCodeBlock = false;
                    assistantCodeBlockLanguage = null;
                    exitTranscriptCodeBlock();
                }
            } else if (assistantInsideCodeBlock) {
                emitLiveTranscriptLine(codexStyleBlockFormatter.formatCodeContentLine(assistantLineBuffer.toString()));
            } else {
                emitLiveTranscriptAssistantLine(assistantLineBuffer.toString(), true);
            }
            assistantLineBuffer.setLength(0);
            liveTranscriptAtLineStart = true;
        }

        private void flushAssistantRemainder() {
            if (assistantLineBuffer.length() == 0) {
                assistantInsideCodeBlock = false;
                assistantCodeBlockLanguage = null;
                return;
            }
            if (assistantInsideCodeBlock) {
                emitLiveTranscriptLine(codexStyleBlockFormatter.formatCodeContentLine(assistantLineBuffer.toString()));
                assistantInsideCodeBlock = false;
                assistantCodeBlockLanguage = null;
                exitTranscriptCodeBlock();
                return;
            }
            if (codexStyleBlockFormatter.isCodeFenceLine(assistantLineBuffer.toString())) {
                assistantInsideCodeBlock = true;
                assistantCodeBlockLanguage = codexStyleBlockFormatter.codeFenceLanguage(assistantLineBuffer.toString());
                enterTranscriptCodeBlock(assistantCodeBlockLanguage);
                return;
            }
            emitLiveTranscriptAssistantLine(assistantLineBuffer.toString(), false);
        }

        private void emitLiveTranscriptLine(String line) {
            if (line == null) {
                line = "";
            }
            if (!liveTranscriptAtLineStart) {
                terminal.println("");
            }
            terminal.println(line);
            liveTranscriptAtLineStart = true;
        }

        private void emitLiveTranscriptAssistantLine(String line, boolean newline) {
            String safe = line == null ? "" : line;
            if (newline) {
                if (!liveTranscriptAtLineStart) {
                    terminal.println("");
                }
                JlineShellTerminalIO shellTerminal = shellTerminal();
                if (shellTerminal != null) {
                    shellTerminal.printTranscriptLine(safe, true);
                } else {
                    terminal.println(safe);
                }
                liveTranscriptAtLineStart = true;
                return;
            }
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.printTranscriptLine(safe, false);
            } else {
                terminal.print(safe);
            }
            liveTranscriptAtLineStart = false;
        }

        private void enterTranscriptCodeBlock(String language) {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.enterTranscriptCodeBlock(language);
            }
        }

        private void beginAssistantBlockTracking() {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.beginAssistantBlockTracking();
            }
        }

        private void exitTranscriptCodeBlock() {
            JlineShellTerminalIO shellTerminal = shellTerminal();
            if (shellTerminal != null) {
                shellTerminal.exitTranscriptCodeBlock();
            }
        }

        private List<String> trimBlankEdges(String[] rawLines) {
            List<String> lines = new ArrayList<String>();
            if (rawLines == null || rawLines.length == 0) {
                return lines;
            }
            int start = 0;
            int end = rawLines.length - 1;
            while (start <= end && isBlank(rawLines[start])) {
                start++;
            }
            while (end >= start && isBlank(rawLines[end])) {
                end--;
            }
            for (int index = start; index <= end; index++) {
                lines.add(rawLines[index] == null ? "" : rawLines[index]);
            }
            return lines;
        }

        private boolean sameText(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    private enum LiveTranscriptKind {
        REASONING,
        ASSISTANT
    }

    private static final class DispatchResult {

        private final ManagedCodingSession session;
        private final boolean exitRequested;

        private DispatchResult(ManagedCodingSession session, boolean exitRequested) {
            this.session = session;
            this.exitRequested = exitRequested;
        }

        private static DispatchResult stay(ManagedCodingSession session) {
            return new DispatchResult(session, false);
        }

        private static DispatchResult exit(ManagedCodingSession session) {
            return new DispatchResult(session, true);
        }

        private ManagedCodingSession getSession() {
            return session;
        }

        private boolean isExitRequested() {
            return exitRequested;
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (isBlank(message)) {
            return throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
        }
        return message;
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

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameTurnId(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean isExitCommand(String input) {
        String normalized = input == null ? "" : input.trim();
        return "/exit".equalsIgnoreCase(normalized) || "/quit".equalsIgnoreCase(normalized);
    }

    private String clip(String value, int maxChars) {
        return CliDisplayWidth.clip(value, maxChars);
    }

    private final class CliAgentListener implements AgentListener {

        private final ManagedCodingSession session;
        private final String turnId;
        private final ActiveTuiTurn activeTurn;
        private final Map<String, AgentToolCall> toolCalls = new LinkedHashMap<String, AgentToolCall>();
        private String finalOutput;
        private boolean errorOccurred;
        private boolean suppressAssistantStreamingAfterTool;
        private volatile boolean closed;

        private CliAgentListener(ManagedCodingSession session, String turnId, ActiveTuiTurn activeTurn) {
            this.session = session;
            this.turnId = turnId;
            this.activeTurn = activeTurn;
        }

        @Override
        public void onEvent(AgentEvent event) {
            if (shouldIgnoreEvents()) {
                return;
            }
            if (event == null || event.getType() == null) {
                return;
            }
            AgentEventType type = event.getType();
            if (type == AgentEventType.STEP_START) {
                tuiLiveTurnState.onStepStart(event.getStep());
                if (useMainBufferInteractiveShell()) {
                    mainBufferTurnPrinter.showThinking();
                }
                renderTuiIfEnabled(session);
                if (options.isVerbose() && !isTuiMode()) {
                    terminal.println("[step] " + type.name().toLowerCase(Locale.ROOT) + " #" + event.getStep());
                }
                return;
            }
            if (type == AgentEventType.MODEL_REQUEST) {
                if (useMainBufferInteractiveShell()) {
                    mainBufferTurnPrinter.showConnecting(buildModelConnectionStatus(session));
                }
                renderTuiIfEnabled(session);
                return;
            }
            if (type == AgentEventType.MODEL_RETRY) {
                String detail = firstNonBlank(event.getMessage(), "Retrying model request");
                int attempt = retryPayloadInt(event.getPayload(), "attempt");
                int maxAttempts = retryPayloadInt(event.getPayload(), "maxAttempts");
                tuiLiveTurnState.onRetry(event.getStep(), detail);
                if (useMainBufferInteractiveShell()) {
                    if (attempt > 0 && maxAttempts > 0) {
                        mainBufferTurnPrinter.showRetrying(detail, attempt, maxAttempts);
                    } else {
                        mainBufferTurnPrinter.showStatus(detail);
                    }
                } else if (options.isVerbose() && !isTuiMode()) {
                    terminal.println("[model] " + detail);
                }
                renderTuiIfEnabled(session);
                return;
            }
            if (type == AgentEventType.MODEL_RESPONSE) {
                if (!isBlank(event.getMessage())) {
                    if (tuiLiveTurnState.hasPendingReasoning()) {
                        flushPendingReasoning(event.getStep());
                    }
                    tuiLiveTurnState.onModelDelta(event.getStep(), event.getMessage());
                    if (streamTranscriptEnabled() && renderMainBufferAssistantIncrementally()) {
                        streamMainBufferAssistantDelta();
                    }
                    if (useMainBufferInteractiveShell()) {
                        mainBufferTurnPrinter.showResponding();
                    }
                    if (shouldRenderModelDelta(event.getMessage())) {
                        renderTuiIfEnabled(session);
                    }
                }
                return;
            }
            if (type == AgentEventType.MODEL_REASONING) {
                if (!isBlank(event.getMessage())) {
                    if (tuiLiveTurnState.hasPendingText()) {
                        flushPendingText(event.getStep());
                    }
                    tuiLiveTurnState.onReasoningDelta(event.getStep(), event.getMessage());
                    if (streamTranscriptEnabled() && renderMainBufferReasoningIncrementally()) {
                        streamMainBufferReasoningDelta();
                    }
                    if (useMainBufferInteractiveShell()) {
                        mainBufferTurnPrinter.showThinking();
                    }
                    if (shouldRenderModelDelta(event.getMessage())) {
                        renderTuiIfEnabled(session);
                    }
                }
                return;
            }
            if (type == AgentEventType.TOOL_CALL) {
                handleToolCall(event);
                return;
            }
            if (type == AgentEventType.TOOL_RESULT) {
                handleToolResult(event);
                return;
            }
            if (AgentHandoffSessionEventSupport.supports(event)) {
                handleHandoffEvent(event);
                return;
            }
            if (AgentTeamSessionEventSupport.supports(event)) {
                handleTeamTaskEvent(event);
                return;
            }
            if (AgentTeamMessageSessionEventSupport.supports(event)) {
                handleTeamMessageEvent(event);
                return;
            }
            if (type == AgentEventType.FINAL_OUTPUT) {
                if (errorOccurred && isBlank(event.getMessage())) {
                    return;
                }
                finalOutput = event.getMessage();
                tuiLiveTurnState.onFinalOutput(event.getStep(), finalOutput);
                if (streamTranscriptEnabled() && renderMainBufferAssistantIncrementally()) {
                    streamMainBufferAssistantDelta();
                }
                renderTuiIfEnabled(session);
                return;
            }
            if (type == AgentEventType.ERROR) {
                errorOccurred = true;
                if (useMainBufferInteractiveShell()) {
                    mainBufferTurnPrinter.clearTransient();
                } else if (!isTuiMode()) {
                    terminal.errorln("[error] " + clip(event.getMessage(), 320));
                }
                flushPendingAssistantText(event.getStep());
                tuiLiveTurnState.onError(event.getStep(), event.getMessage());
                renderTuiIfEnabled(session);
                appendEvent(session, SessionEventType.ERROR, turnId, event.getStep(), clip(event.getMessage(), 320), payloadOf(
                        "error", clip(event.getMessage(), options.isVerbose() ? 4000 : 1200)
                ));
                if (useMainBufferInteractiveShell()) {
                    emitMainBufferError(event.getMessage());
                }
                return;
            }
            if (type == AgentEventType.STEP_END) {
                tuiLiveTurnState.onStepEnd(event.getStep());
                if (useMainBufferInteractiveShell() && isBlank(finalOutput)) {
                    mainBufferTurnPrinter.showThinking();
                }
                renderTuiIfEnabled(session);
            }
            if (options.isVerbose() && !isTuiMode() && type == AgentEventType.STEP_END) {
                terminal.println("[step] " + type.name().toLowerCase(Locale.ROOT) + " #" + event.getStep());
            }
        }

        private void close() {
            closed = true;
        }

        private boolean shouldIgnoreEvents() {
            return closed || isTurnInterrupted(turnId, activeTurn);
        }

        private int retryPayloadInt(Object payload, String key) {
            if (!(payload instanceof Map) || isBlank(key)) {
                return 0;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) payload;
            Object value = values.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String) {
                try {
                    return Integer.parseInt(((String) value).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            return 0;
        }

        private void handleToolCall(AgentEvent event) {
            AgentToolCall call = event.getPayload() instanceof AgentToolCall ? (AgentToolCall) event.getPayload() : null;
            if (call == null) {
                return;
            }
            boolean firstToolCallInTurn = !suppressAssistantStreamingAfterTool;
            suppressAssistantStreamingAfterTool = true;
            String callKey = resolveToolCallKey(call);
            if (toolCalls.containsKey(callKey)) {
                return;
            }
            toolCalls.put(callKey, call);
            if (!isTuiMode()) {
                terminal.println("[tool] " + call.getName() + " " + clip(call.getArguments(), options.isVerbose() ? 320 : 160));
            }
            flushPendingAssistantText(event.getStep());
            if (firstToolCallInTurn && useMainBufferInteractiveShell()) {
                mainBufferTurnPrinter.discardAssistantBlock();
            }
            TuiAssistantToolView toolView = buildPendingToolView(call);
            appendEvent(session, SessionEventType.TOOL_CALL, turnId, event.getStep(),
                    call.getName() + " " + clip(call.getArguments(), 120),
                    payloadOf(
                            "tool", call.getName(),
                            "callId", call.getCallId(),
                            "arguments", clip(call.getArguments(), options.isVerbose() ? 4000 : 1200),
                            "title", toolView.getTitle(),
                            "detail", toolView.getDetail(),
                            "previewLines", toolView.getPreviewLines()
                    ));
            tuiLiveTurnState.onToolCall(event.getStep(), toolView);
            if (useMainBufferInteractiveShell()) {
                mainBufferTurnPrinter.showStatus(buildMainBufferRunningStatus(toolView));
            }
            renderTuiIfEnabled(session);
        }

        private void handleToolResult(AgentEvent event) {
            AgentToolResult result = event.getPayload() instanceof AgentToolResult ? (AgentToolResult) event.getPayload() : null;
            if (result == null) {
                return;
            }
            AgentToolCall call = toolCalls.remove(resolveToolResultKey(result));
            if (!isTuiMode()) {
                terminal.println("[tool-result] " + result.getName() + " " + clip(result.getOutput(), options.isVerbose() ? 320 : 160));
            }
            TuiAssistantToolView toolView = buildCompletedToolView(call, result);
            appendEvent(session, SessionEventType.TOOL_RESULT, turnId, event.getStep(),
                    result.getName() + " " + clip(result.getOutput(), 120),
                    payloadOf(
                            "tool", result.getName(),
                            "callId", result.getCallId(),
                            "arguments", call == null ? null : clip(call.getArguments(), options.isVerbose() ? 4000 : 1200),
                            "output", clip(result.getOutput(), options.isVerbose() ? 4000 : 1200),
                            "title", toolView.getTitle(),
                            "detail", toolView.getDetail(),
                            "previewLines", toolView.getPreviewLines()
                    ));
            tuiLiveTurnState.onToolResult(event.getStep(), toolView);
            if (useMainBufferInteractiveShell()) {
                if (!isApprovalRejectedToolResult(result)) {
                    mainBufferTurnPrinter.printBlock(buildMainBufferToolLines(toolView));
                }
            }
            renderTuiIfEnabled(session);
            appendProcessEvent(session, turnId, event.getStep(), call, result);
        }

        private void handleHandoffEvent(AgentEvent event) {
            SessionEvent sessionEvent = AgentHandoffSessionEventSupport.toSessionEvent(session.getSessionId(), turnId, event);
            if (sessionEvent == null) {
                return;
            }
            appendEvent(session, sessionEvent.getType(), turnId, sessionEvent.getStep(), sessionEvent.getSummary(), sessionEvent.getPayload());
            if (useMainBufferInteractiveShell() && sessionEvent.getType() == SessionEventType.TASK_UPDATED) {
                mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatInfoBlock(
                        "Subagent task",
                        buildReplayTaskLines(sessionEvent)
                ));
            }
            renderTuiIfEnabled(session);
        }

        private void handleTeamTaskEvent(AgentEvent event) {
            SessionEvent sessionEvent = AgentTeamSessionEventSupport.toSessionEvent(session.getSessionId(), turnId, event);
            if (sessionEvent == null) {
                return;
            }
            appendEvent(session, sessionEvent.getType(), turnId, sessionEvent.getStep(), sessionEvent.getSummary(), sessionEvent.getPayload());
            if (useMainBufferInteractiveShell() && sessionEvent.getType() == SessionEventType.TASK_UPDATED) {
                mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatInfoBlock(
                        "Team task",
                        buildReplayTaskLines(sessionEvent)
                ));
            }
            renderTuiIfEnabled(session);
        }

        private void handleTeamMessageEvent(AgentEvent event) {
            SessionEvent sessionEvent = AgentTeamMessageSessionEventSupport.toSessionEvent(session.getSessionId(), turnId, event);
            if (sessionEvent == null) {
                return;
            }
            appendEvent(session, sessionEvent.getType(), turnId, sessionEvent.getStep(), sessionEvent.getSummary(), sessionEvent.getPayload());
            if (useMainBufferInteractiveShell()) {
                mainBufferTurnPrinter.printBlock(codexStyleBlockFormatter.formatInfoBlock(
                        "Team message",
                        buildReplayTeamMessageLines(sessionEvent)
                ));
            }
            renderTuiIfEnabled(session);
        }

        private void flushFinalOutput() {
            if (shouldIgnoreEvents()) {
                return;
            }
            if (useMainBufferInteractiveShell()) {
                flushPendingAssistantText(null);
                // When transcript streaming is off, flushPendingAssistantText()
                // has already emitted the completed assistant block. Printing
                // finalOutput again duplicates the same answer at turn end.
                if (streamEnabled && !isBlank(finalOutput)) {
                    mainBufferTurnPrinter.printAssistantBlock(finalOutput);
                }
                return;
            }
            // In one-shot CLI mode, onFinalOutput() has already reconciled the
            // streamed deltas with the final provider payload inside the live
            // text buffer. Flushing the pending assistant text emits the final
            // answer once; printing finalOutput directly here duplicates it.
            flushPendingAssistantText(null);
            tuiLiveTurnState.finishTurn(null, finalOutput);
            renderTuiIfEnabled(session);
        }

        private void flushPendingAssistantText(Integer step) {
            if (streamTranscriptEnabled() && renderMainBufferReasoningIncrementally()) {
                streamMainBufferReasoningDelta();
            }
            if (streamTranscriptEnabled() && renderMainBufferAssistantIncrementally()) {
                streamMainBufferAssistantDelta();
            }
            flushPendingReasoning(step);
            flushPendingText(step);
        }

        private void flushPendingReasoning(Integer step) {
            String pendingReasoning = tuiLiveTurnState.flushPendingReasoning();
            if (isBlank(pendingReasoning)) {
                return;
            }
            appendEvent(session, SessionEventType.ASSISTANT_MESSAGE, turnId, step, clip(pendingReasoning, 200), payloadOf(
                    "kind", "reasoning",
                    "output", clipPreserveNewlines(pendingReasoning, options.isVerbose() ? 4000 : 1200)
            ));
            if ((!useMainBufferInteractiveShell()
                    || !streamEnabled
                    || !renderMainBufferReasoningIncrementally()) && !suppressMainBufferReasoningBlocks()) {
                emitMainBufferReasoning(pendingReasoning);
            }
        }

        private void flushPendingText(Integer step) {
            String pendingText = tuiLiveTurnState.flushPendingText();
            if (isBlank(pendingText)) {
                return;
            }
            appendEvent(session, SessionEventType.ASSISTANT_MESSAGE, turnId, step, clip(pendingText, 200), payloadOf(
                    "kind", "assistant",
                    "output", clipPreserveNewlines(pendingText, options.isVerbose() ? 4000 : 1200)
            ));
            if (!useMainBufferInteractiveShell() || !streamEnabled) {
                emitMainBufferAssistant(pendingText);
            }
        }

        private void streamMainBufferReasoningDelta() {
            String delta = tuiLiveTurnState.flushLiveReasoning();
            if (!isBlank(delta)) {
                mainBufferTurnPrinter.streamReasoning(delta);
            }
        }

        private void streamMainBufferAssistantDelta() {
            if (!renderMainBufferAssistantIncrementally() || suppressAssistantStreamingAfterTool) {
                return;
            }
            String delta = tuiLiveTurnState.flushLiveText();
            if (!isBlank(delta)) {
                mainBufferTurnPrinter.streamAssistant(delta);
            }
        }
    }

    private final class ActiveTuiTurn {

        private final ManagedCodingSession session;
        private final String input;
        private final String turnId = newTurnId();
        private volatile Thread thread;
        private volatile boolean interrupted;
        private volatile boolean done;
        private volatile Exception failure;

        private ActiveTuiTurn(ManagedCodingSession session, String input) {
            this.session = session;
            this.input = input;
        }

        private void start() {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        runTurn(session, input, ActiveTuiTurn.this);
                    } catch (Exception ex) {
                        failure = ex;
                    } finally {
                        done = true;
                    }
                }
            }, "ai4j-tui-turn");
            thread = worker;
            worker.start();
        }

        private boolean requestInterrupt() {
            if (done || interrupted) {
                return false;
            }
            interrupted = true;
            Thread worker = thread;
            if (worker != null) {
                worker.interrupt();
            }
            return true;
        }

        private ManagedCodingSession getSession() {
            return session;
        }

        private String getTurnId() {
            return turnId;
        }

        private boolean isInterrupted() {
            return interrupted;
        }

        private boolean isDone() {
            return done;
        }

        private Exception getFailure() {
            return failure;
        }
    }

    private AssistantReplayPlan computeAssistantReplayPlan(String currentText, String finalText) {
        List<String> finalLines = assistantTranscriptRenderer.plainLines(finalText);
        if (finalLines.isEmpty()) {
            return AssistantReplayPlan.none();
        }
        List<String> currentLines = assistantTranscriptRenderer.plainLines(currentText);
        if (currentLines.isEmpty()) {
            return AssistantReplayPlan.append(finalLines);
        }
        if (normalizeAssistantComparisonText(currentLines).equals(normalizeAssistantComparisonText(finalLines))) {
            return AssistantReplayPlan.none();
        }
        int prefix = commonAssistantPrefixLength(currentLines, finalLines);
        if (prefix >= currentLines.size()) {
            return AssistantReplayPlan.append(finalLines.subList(prefix, finalLines.size()));
        }
        return AssistantReplayPlan.replace(computeRenderedSupplementalReplayLines(currentLines, finalLines));
    }

    private List<String> computeRenderedSupplementalReplayLines(List<String> currentLines, List<String> finalLines) {
        if (finalLines == null || finalLines.isEmpty()) {
            return Collections.emptyList();
        }
        if (currentLines == null || currentLines.isEmpty()) {
            return new ArrayList<String>(finalLines);
        }
        int prefix = commonAssistantPrefixLength(currentLines, finalLines);
        int suffix = 0;
        while (suffix < currentLines.size() - prefix
                && suffix < finalLines.size() - prefix
                && assistantComparisonLine(currentLines.get(currentLines.size() - 1 - suffix))
                .equals(assistantComparisonLine(finalLines.get(finalLines.size() - 1 - suffix)))) {
            suffix++;
        }
        int finalStart = prefix;
        int finalEnd = finalLines.size() - suffix;
        if (finalStart >= finalEnd) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(finalLines.subList(finalStart, finalEnd));
    }

    private int commonAssistantPrefixLength(List<String> currentLines, List<String> finalLines) {
        int prefix = 0;
        while (prefix < currentLines.size()
                && prefix < finalLines.size()
                && assistantComparisonLine(currentLines.get(prefix)).equals(assistantComparisonLine(finalLines.get(prefix)))) {
            prefix++;
        }
        return prefix;
    }

    private String normalizeAssistantComparisonText(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(assistantComparisonLine(line));
        }
        return builder.toString().trim();
    }

    private boolean assistantTextMatches(String currentText, String finalText) {
        return normalizeAssistantComparisonText(assistantTranscriptRenderer.plainLines(currentText))
                .equals(normalizeAssistantComparisonText(assistantTranscriptRenderer.plainLines(finalText)));
    }

    private String assistantComparisonLine(String line) {
        String safe = line == null ? "" : line.trim();
        if (codexStyleBlockFormatter.isCodeFenceLine(safe)) {
            return "```";
        }
        safe = safe.replace("`", "");
        safe = safe.replace("**", "");
        safe = safe.replace("__", "");
        safe = safe.replace("*", "");
        safe = safe.replace("_", "");
        return safe;
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index) == null ? "" : lines.get(index));
        }
        return builder.toString();
    }

    private String clipPreserveNewlines(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", "").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private static final class McpAddCommand {

        private final String name;
        private final CliMcpServerDefinition definition;

        private McpAddCommand(String name, CliMcpServerDefinition definition) {
            this.name = name;
            this.definition = definition;
        }
    }

    private static final class ProviderProfileMutation {

        private final String profileName;
        private String provider;
        private String protocol;
        private String model;
        private String baseUrl;
        private String apiKey;
        private boolean clearModel;
        private boolean clearBaseUrl;
        private boolean clearApiKey;

        private ProviderProfileMutation(String profileName) {
            this.profileName = profileName;
        }

        private boolean hasAnyFieldChanges() {
            return provider != null
                    || protocol != null
                    || model != null
                    || baseUrl != null
                    || apiKey != null
                    || clearModel
                    || clearBaseUrl
                    || clearApiKey;
        }
    }

    private static final class AssistantReplayPlan {

        private final boolean replaceBlock;
        private final List<String> supplementalLines;

        private AssistantReplayPlan(boolean replaceBlock, List<String> supplementalLines) {
            this.replaceBlock = replaceBlock;
            this.supplementalLines = supplementalLines == null
                    ? Collections.<String>emptyList()
                    : new ArrayList<String>(supplementalLines);
        }

        private static AssistantReplayPlan none() {
            return new AssistantReplayPlan(false, Collections.<String>emptyList());
        }

        private static AssistantReplayPlan append(List<String> supplementalLines) {
            return new AssistantReplayPlan(false, supplementalLines);
        }

        private static AssistantReplayPlan replace(List<String> supplementalLines) {
            return new AssistantReplayPlan(true, supplementalLines);
        }

        private boolean replaceBlock() {
            return replaceBlock;
        }

        private List<String> supplementalLines() {
            return supplementalLines;
        }
    }
}
