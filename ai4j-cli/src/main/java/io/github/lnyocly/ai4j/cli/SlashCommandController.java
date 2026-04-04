package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.command.CustomCommandRegistry;
import io.github.lnyocly.ai4j.cli.command.CustomCommandTemplate;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;
import org.jline.keymap.KeyMap;
import org.jline.reader.Buffer;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Reference;
import org.jline.reader.Widget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class SlashCommandController implements Completer {

    private static final Runnable NOOP_STATUS_REFRESH = new Runnable() {
        @Override
        public void run() {
        }
    };

    private static final List<SlashCommandSpec> BUILT_IN_COMMANDS = Arrays.asList(
            new SlashCommandSpec("/help", "Show help", false),
            new SlashCommandSpec("/status", "Show current session status", false),
            new SlashCommandSpec("/session", "Show current session metadata", false),
            new SlashCommandSpec("/theme", "Show or switch the active theme", true),
            new SlashCommandSpec("/save", "Persist the current session state", false),
            new SlashCommandSpec("/providers", "List saved provider profiles", false),
            new SlashCommandSpec("/provider", "Show or switch the active provider profile", true),
            new SlashCommandSpec("/model", "Show or switch the active model override", true),
            new SlashCommandSpec("/experimental", "Show or switch experimental runtime feature flags", true),
            new SlashCommandSpec("/skills", "List or inspect discovered coding skills", true),
            new SlashCommandSpec("/agents", "List or inspect available coding agents", true),
            new SlashCommandSpec("/commands", "List available custom commands", false),
            new SlashCommandSpec("/palette", "Alias of /commands", false),
            new SlashCommandSpec("/cmd", "Run a custom command template", true),
            new SlashCommandSpec("/sessions", "List saved sessions", false),
            new SlashCommandSpec("/history", "Show session lineage", true),
            new SlashCommandSpec("/tree", "Show the current session tree", true),
            new SlashCommandSpec("/events", "Show the latest session ledger events", true),
            new SlashCommandSpec("/replay", "Replay recent turns grouped from the event ledger", true),
            new SlashCommandSpec("/team", "Show the current agent team board", false),
            new SlashCommandSpec("/compacts", "Show recent compact history", true),
            new SlashCommandSpec("/stream", "Show or switch model request streaming", true),
            new SlashCommandSpec("/mcp", "Show or manage MCP services", true),
            new SlashCommandSpec("/processes", "List active and restored process metadata", false),
            new SlashCommandSpec("/process", "Inspect or control a process", true),
            new SlashCommandSpec("/checkpoint", "Show the current structured checkpoint summary", false),
            new SlashCommandSpec("/resume", "Resume a saved session", true),
            new SlashCommandSpec("/load", "Alias of /resume", true),
            new SlashCommandSpec("/fork", "Fork a session branch", true),
            new SlashCommandSpec("/compact", "Compact current session memory", true),
            new SlashCommandSpec("/clear", "Print a new screen section", false),
            new SlashCommandSpec("/exit", "Exit the session", false),
            new SlashCommandSpec("/quit", "Exit the session", false)
    );

    private static final List<ProcessCommandSpec> PROCESS_COMMANDS = Arrays.asList(
            new ProcessCommandSpec("status", "Show metadata for one process", false, false),
            new ProcessCommandSpec("follow", "Show process metadata with buffered logs", true, false),
            new ProcessCommandSpec("logs", "Read buffered logs for a process", true, false),
            new ProcessCommandSpec("write", "Write text to a live process stdin", false, true),
            new ProcessCommandSpec("stop", "Stop a live process", false, false)
    );

    private static final List<String> PROCESS_FOLLOW_LIMITS = Arrays.asList("200", "400", "800", "1600");
    private static final List<String> PROCESS_LOG_LIMITS = Arrays.asList("200", "480", "800", "1600");
    private static final List<String> STREAM_OPTIONS = Arrays.asList("on", "off");
    private static final List<String> EXPERIMENTAL_FEATURES = Arrays.asList("subagent", "agent-teams");
    private static final List<String> TEAM_ACTIONS = Arrays.asList("list", "status", "messages", "resume");
    private static final List<String> TEAM_MESSAGE_LIMITS = Arrays.asList("10", "20", "50", "100");
    private static final List<String> MCP_ACTIONS = Arrays.asList("list", "add", "enable", "disable", "pause", "resume", "retry", "remove");
    private static final String MCP_TRANSPORT_FLAG = "--transport";
    private static final List<String> MCP_TRANSPORT_OPTIONS = Arrays.asList("stdio", "sse", "http");
    private static final List<String> PROVIDER_ACTIONS = Arrays.asList("use", "save", "add", "edit", "default", "remove");
    private static final String MODEL_RESET = "reset";
    private static final List<String> PROVIDER_DEFAULT_OPTIONS = Arrays.asList("clear");
    private static final List<String> PROVIDER_MUTATION_OPTIONS = Arrays.asList(
            "--provider",
            "--protocol",
            "--model",
            "--base-url",
            "--api-key",
            "--clear-model",
            "--clear-base-url",
            "--clear-api-key"
    );
    private static final List<String> EXECUTABLE_ROOT_COMMANDS = Arrays.asList(
            "/help",
            "/status",
            "/session",
            "/theme",
            "/save",
            "/providers",
            "/provider",
            "/model",
            "/experimental",
            "/skills",
            "/agents",
            "/commands",
            "/palette",
            "/sessions",
            "/history",
            "/tree",
            "/events",
            "/replay",
            "/team",
            "/compacts",
            "/stream",
            "/mcp",
            "/processes",
            "/checkpoint",
            "/fork",
            "/compact",
            "/clear",
            "/exit",
            "/quit"
    );

    private final CustomCommandRegistry customCommandRegistry;
    private final TuiConfigManager tuiConfigManager;
    private final Object paletteLock = new Object();
    private final java.util.Map<String, Widget> wrappedWidgets = new java.util.HashMap<String, Widget>();
    private volatile CodingSessionManager sessionManager;
    private volatile Supplier<List<ProcessCompletionCandidate>> processCandidateSupplier =
            new Supplier<List<ProcessCompletionCandidate>>() {
                @Override
                public List<ProcessCompletionCandidate> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Supplier<List<String>> profileCandidateSupplier =
            new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Supplier<List<ModelCompletionCandidate>> modelCandidateSupplier =
            new Supplier<List<ModelCompletionCandidate>>() {
                @Override
                public List<ModelCompletionCandidate> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Supplier<List<String>> mcpServerCandidateSupplier =
            new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Supplier<List<String>> skillCandidateSupplier =
            new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Supplier<List<String>> agentCandidateSupplier =
            new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Supplier<List<String>> teamCandidateSupplier =
            new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
    private volatile Runnable statusRefresh = NOOP_STATUS_REFRESH;
    private PaletteSnapshot paletteSnapshot = PaletteSnapshot.closed();

    public SlashCommandController(CustomCommandRegistry customCommandRegistry, TuiConfigManager tuiConfigManager) {
        this.customCommandRegistry = customCommandRegistry;
        this.tuiConfigManager = tuiConfigManager;
    }

    public void setSessionManager(CodingSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setProcessCandidateSupplier(Supplier<List<ProcessCompletionCandidate>> processCandidateSupplier) {
        if (processCandidateSupplier == null) {
            this.processCandidateSupplier = new Supplier<List<ProcessCompletionCandidate>>() {
                @Override
                public List<ProcessCompletionCandidate> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.processCandidateSupplier = processCandidateSupplier;
    }

    public void setProfileCandidateSupplier(Supplier<List<String>> profileCandidateSupplier) {
        if (profileCandidateSupplier == null) {
            this.profileCandidateSupplier = new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.profileCandidateSupplier = profileCandidateSupplier;
    }

    public void setModelCandidateSupplier(Supplier<List<ModelCompletionCandidate>> modelCandidateSupplier) {
        if (modelCandidateSupplier == null) {
            this.modelCandidateSupplier = new Supplier<List<ModelCompletionCandidate>>() {
                @Override
                public List<ModelCompletionCandidate> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.modelCandidateSupplier = modelCandidateSupplier;
    }

    public void setMcpServerCandidateSupplier(Supplier<List<String>> mcpServerCandidateSupplier) {
        if (mcpServerCandidateSupplier == null) {
            this.mcpServerCandidateSupplier = new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.mcpServerCandidateSupplier = mcpServerCandidateSupplier;
    }

    public void setSkillCandidateSupplier(Supplier<List<String>> skillCandidateSupplier) {
        if (skillCandidateSupplier == null) {
            this.skillCandidateSupplier = new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.skillCandidateSupplier = skillCandidateSupplier;
    }

    public void setAgentCandidateSupplier(Supplier<List<String>> agentCandidateSupplier) {
        if (agentCandidateSupplier == null) {
            this.agentCandidateSupplier = new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.agentCandidateSupplier = agentCandidateSupplier;
    }

    public void setTeamCandidateSupplier(Supplier<List<String>> teamCandidateSupplier) {
        if (teamCandidateSupplier == null) {
            this.teamCandidateSupplier = new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return Collections.emptyList();
                }
            };
            return;
        }
        this.teamCandidateSupplier = teamCandidateSupplier;
    }

    public void setStatusRefresh(Runnable statusRefresh) {
        this.statusRefresh = statusRefresh == null ? NOOP_STATUS_REFRESH : statusRefresh;
    }

    public void configure(LineReader lineReader) {
        if (lineReader == null) {
            return;
        }
        lineReader.setOpt(LineReader.Option.CASE_INSENSITIVE);
        lineReader.setOpt(LineReader.Option.AUTO_MENU);
        lineReader.setOpt(LineReader.Option.AUTO_MENU_LIST);
        lineReader.setOpt(LineReader.Option.AUTO_GROUP);
        lineReader.setOpt(LineReader.Option.LIST_PACKED);

        lineReader.getWidgets().put("ai4j-slash-menu", new Widget() {
            @Override
            public boolean apply() {
                return openSlashMenu(lineReader);
            }
        });
        lineReader.getWidgets().put("ai4j-command-palette", new Widget() {
            @Override
            public boolean apply() {
                return openCommandPalette(lineReader);
            }
        });
        lineReader.getWidgets().put("ai4j-accept-line", new Widget() {
            @Override
            public boolean apply() {
                return acceptLine(lineReader);
            }
        });
        lineReader.getWidgets().put("ai4j-accept-selection", new Widget() {
            @Override
            public boolean apply() {
                return acceptSlashSelection(lineReader, true);
            }
        });
        lineReader.getWidgets().put("ai4j-palette-up", new Widget() {
            @Override
            public boolean apply() {
                return navigateSlashPalette(lineReader, -1, LineReader.UP_LINE_OR_HISTORY);
            }
        });
        lineReader.getWidgets().put("ai4j-palette-down", new Widget() {
            @Override
            public boolean apply() {
                return navigateSlashPalette(lineReader, 1, LineReader.DOWN_LINE_OR_HISTORY);
            }
        });

        wrapWidget(lineReader, LineReader.SELF_INSERT, new Widget() {
            @Override
            public boolean apply() {
                return delegateAndRefreshSlashPalette(lineReader, LineReader.SELF_INSERT);
            }
        });
        wrapWidget(lineReader, LineReader.BACKWARD_DELETE_CHAR, new Widget() {
            @Override
            public boolean apply() {
                return delegateAndRefreshSlashPalette(lineReader, LineReader.BACKWARD_DELETE_CHAR);
            }
        });
        wrapWidget(lineReader, LineReader.DELETE_CHAR, new Widget() {
            @Override
            public boolean apply() {
                return delegateAndRefreshSlashPalette(lineReader, LineReader.DELETE_CHAR);
            }
        });
        wrapWidget(lineReader, LineReader.UP_LINE_OR_HISTORY, new Widget() {
            @Override
            public boolean apply() {
                return navigateSlashPalette(lineReader, -1, LineReader.UP_LINE_OR_HISTORY);
            }
        });
        wrapWidget(lineReader, LineReader.DOWN_LINE_OR_HISTORY, new Widget() {
            @Override
            public boolean apply() {
                return navigateSlashPalette(lineReader, 1, LineReader.DOWN_LINE_OR_HISTORY);
            }
        });
        wrapWidget(lineReader, LineReader.UP_HISTORY, new Widget() {
            @Override
            public boolean apply() {
                return navigateSlashPalette(lineReader, -1, LineReader.UP_HISTORY);
            }
        });
        wrapWidget(lineReader, LineReader.DOWN_HISTORY, new Widget() {
            @Override
            public boolean apply() {
                return navigateSlashPalette(lineReader, 1, LineReader.DOWN_HISTORY);
            }
        });

        bindWidget(lineReader, "ai4j-slash-menu", "/");
        bindWidget(lineReader, "ai4j-command-palette", KeyMap.ctrl('P'));
        bindWidget(lineReader, "ai4j-accept-line", "\r");
        bindWidget(lineReader, "ai4j-accept-line", "\n");
        bindWidget(lineReader, "ai4j-accept-selection", "\t");
        bindWidget(lineReader, "ai4j-palette-up", "\u001b[A");
        bindWidget(lineReader, "ai4j-palette-up", "\u001bOA");
        bindWidget(lineReader, "ai4j-palette-down", "\u001b[B");
        bindWidget(lineReader, "ai4j-palette-down", "\u001bOB");
    }

    @Override
    public void complete(LineReader lineReader, ParsedLine parsedLine, List<Candidate> candidates) {
        if (parsedLine == null || candidates == null) {
            return;
        }
        candidates.addAll(suggest(parsedLine.line(), parsedLine.cursor()));
    }

    List<Candidate> suggest(String line, int cursor) {
        String raw = line == null ? "" : line;
        int safeCursor = Math.max(0, Math.min(cursor, raw.length()));
        String prefix = raw.substring(0, safeCursor);
        if (!prefix.startsWith("/")) {
            return Collections.emptyList();
        }

        boolean endsWithSpace = !prefix.isEmpty() && Character.isWhitespace(prefix.charAt(prefix.length() - 1));
        List<String> tokens = splitTokens(prefix);
        if (tokens.isEmpty()) {
            return rootCandidates("");
        }

        String command = tokens.get(0);
        if (tokens.size() == 1 && !endsWithSpace) {
            if (isExecutableRootCommand(command)) {
                return rootCandidates(command);
            }
            List<Candidate> exactCommandCandidates = exactCommandArgumentCandidates(command);
            if (!exactCommandCandidates.isEmpty()) {
                return exactCommandCandidates;
            }
            return rootCandidates(command);
        }

        if ("/cmd".equalsIgnoreCase(command)) {
            return customCommandCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/theme".equalsIgnoreCase(command)) {
            return themeCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/stream".equalsIgnoreCase(command)) {
            return streamCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/experimental".equalsIgnoreCase(command)) {
            return experimentalCandidates(tokens, endsWithSpace);
        }
        if ("/skills".equalsIgnoreCase(command)) {
            return skillCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/agents".equalsIgnoreCase(command)) {
            return agentCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/provider".equalsIgnoreCase(command)) {
            return providerCandidates(tokens, endsWithSpace);
        }
        if ("/mcp".equalsIgnoreCase(command)) {
            return mcpCandidates(tokens, endsWithSpace);
        }
        if ("/model".equalsIgnoreCase(command)) {
            return modelCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/resume".equalsIgnoreCase(command)
                || "/load".equalsIgnoreCase(command)
                || "/history".equalsIgnoreCase(command)
                || "/tree".equalsIgnoreCase(command)
                || "/fork".equalsIgnoreCase(command)) {
            return sessionCandidates(tokenFragment(tokens, endsWithSpace));
        }
        if ("/process".equalsIgnoreCase(command)) {
            return processCandidates(tokens, endsWithSpace);
        }
        if ("/team".equalsIgnoreCase(command)) {
            return teamCandidates(tokens, endsWithSpace);
        }
        return Collections.emptyList();
    }

    private List<Candidate> exactCommandArgumentCandidates(String command) {
        if (isBlank(command)) {
            return Collections.emptyList();
        }
        if ("/cmd".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", customCommandCandidates(""));
        }
        if ("/theme".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", themeCandidates(""));
        }
        if ("/stream".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", streamCandidates(""));
        }
        if ("/experimental".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", experimentalFeatureCandidates(""));
        }
        if ("/skills".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", skillCandidates(""));
        }
        if ("/agents".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", agentCandidates(""));
        }
        if ("/provider".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", providerActionCandidates(""));
        }
        if ("/model".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", modelCandidates(""));
        }
        if ("/resume".equalsIgnoreCase(command)
                || "/load".equalsIgnoreCase(command)
                || "/history".equalsIgnoreCase(command)
                || "/tree".equalsIgnoreCase(command)
                || "/fork".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", sessionCandidates(""));
        }
        if ("/process".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", processSubcommandCandidates(""));
        }
        if ("/team".equalsIgnoreCase(command)) {
            return prefixCandidates(command + " ", teamActionCandidates(""));
        }
        return Collections.emptyList();
    }

    boolean openSlashMenu(LineReader lineReader) {
        Buffer buffer = lineReader.getBuffer();
        if (buffer == null) {
            return true;
        }
        String line = buffer.toString();
        SlashMenuAction action = resolveSlashMenuAction(line, buffer.cursor());
        if (action == SlashMenuAction.INSERT_AND_MENU) {
            buffer.write("/");
            syncSlashPalette(lineReader);
            refreshStatusAndDisplay(lineReader);
            return true;
        }
        if (action == SlashMenuAction.MENU_ONLY) {
            syncSlashPalette(lineReader);
            refreshStatusAndDisplay(lineReader);
            return true;
        }
        buffer.write("/");
        return true;
    }

    private boolean openCommandPalette(LineReader lineReader) {
        Buffer buffer = lineReader.getBuffer();
        if (buffer == null) {
            return true;
        }
        if (buffer.length() == 0) {
            buffer.write("/");
            syncSlashPalette(lineReader);
            refreshStatusAndDisplay(lineReader);
            return true;
        }
        if (buffer.toString().startsWith("/")) {
            syncSlashPalette(lineReader);
            refreshStatusAndDisplay(lineReader);
            return true;
        }
        return false;
    }

    private boolean acceptLine(LineReader lineReader) {
        Buffer buffer = lineReader == null ? null : lineReader.getBuffer();
        String current = buffer == null ? null : buffer.toString();
        if (!shouldExecuteRawInputOnEnter(current) && acceptSlashSelection(lineReader, false)) {
            return true;
        }
        EnterAction action = resolveAcceptLineAction(current);
        if (action == EnterAction.IGNORE_EMPTY) {
            if (buffer != null) {
                buffer.clear();
            }
            clearPalette();
            notifyStatusRefresh();
            lineReader.callWidget(LineReader.REDRAW_LINE);
            lineReader.callWidget(LineReader.REDISPLAY);
            return true;
        }
        clearPalette();
        notifyStatusRefresh();
        lineReader.callWidget(LineReader.ACCEPT_LINE);
        return true;
    }

    private boolean shouldExecuteRawInputOnEnter(String line) {
        if (isBlank(line)) {
            return false;
        }
        String normalized = line.trim();
        if (!normalized.startsWith("/") || normalized.indexOf(' ') >= 0) {
            return false;
        }
        for (String command : EXECUTABLE_ROOT_COMMANDS) {
            if (command.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    SlashMenuAction resolveSlashMenuAction(String line, int cursor) {
        String value = line == null ? "" : line;
        if (value.isEmpty()) {
            return SlashMenuAction.INSERT_AND_MENU;
        }
        if (value.startsWith("/") && value.indexOf(' ') < 0) {
            return SlashMenuAction.MENU_ONLY;
        }
        return SlashMenuAction.INSERT_ONLY;
    }

    EnterAction resolveAcceptLineAction(String line) {
        return isBlank(line) ? EnterAction.IGNORE_EMPTY : EnterAction.ACCEPT;
    }

    public PaletteSnapshot getPaletteSnapshot() {
        synchronized (paletteLock) {
            return paletteSnapshot.copy();
        }
    }

    void movePaletteSelection(int delta) {
        synchronized (paletteLock) {
            if (!paletteSnapshot.isOpen() || paletteSnapshot.items.isEmpty()) {
                return;
            }
            int size = paletteSnapshot.items.size();
            int nextIndex = Math.floorMod(paletteSnapshot.selectedIndex + delta, size);
            paletteSnapshot = paletteSnapshot.withSelectedIndex(nextIndex);
        }
        notifyStatusRefresh();
    }

    boolean acceptSlashSelection(LineReader lineReader, boolean alwaysAccept) {
        if (lineReader == null) {
            return false;
        }
        Buffer buffer = lineReader.getBuffer();
        if (buffer == null) {
            return false;
        }
        PaletteItemSnapshot selected = getSelectedPaletteItem();
        if (selected == null) {
            return false;
        }
        String current = buffer.toString();
        String replacement = applySelectedValue(current, buffer.cursor(), selected.value);
        if (!alwaysAccept && sameText(current, replacement)) {
            clearPalette();
            notifyStatusRefresh();
            return false;
        }
        buffer.clear();
        buffer.write(replacement);
        if (shouldContinuePaletteAfterAccept(replacement, selected)) {
            syncSlashPalette(lineReader);
            refreshStatusAndDisplay(lineReader);
            return true;
        }
        clearPalette();
        refreshStatusAndDisplay(lineReader);
        return true;
    }

    private String applySelectedValue(String current, int cursor, String selectedValue) {
        String line = current == null ? "" : current;
        String replacement = selectedValue == null ? "" : selectedValue;
        int safeCursor = Math.max(0, Math.min(cursor, line.length()));
        int tokenStart = safeCursor;
        while (tokenStart > 0 && !Character.isWhitespace(line.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        int tokenEnd = safeCursor;
        while (tokenEnd < line.length() && !Character.isWhitespace(line.charAt(tokenEnd))) {
            tokenEnd++;
        }
        return line.substring(0, tokenStart) + replacement + line.substring(tokenEnd);
    }

    private boolean shouldContinuePaletteAfterAccept(String replacement, PaletteItemSnapshot selected) {
        if (selected == null || isBlank(replacement)) {
            return false;
        }
        if (!replacement.endsWith(" ")) {
            return false;
        }
        String normalized = replacement.trim();
        if (commandRequiresArgument(normalized)) {
            return true;
        }
        return !suggest(replacement, replacement.length()).isEmpty();
    }

    private boolean commandRequiresArgument(String command) {
        if (isBlank(command)) {
            return false;
        }
        for (SlashCommandSpec spec : BUILT_IN_COMMANDS) {
            if (sameText(spec.command, command)) {
                return spec.requiresArgument;
            }
        }
        return false;
    }

    private boolean isExecutableRootCommand(String value) {
        if (isBlank(value)) {
            return false;
        }
        for (String command : EXECUTABLE_ROOT_COMMANDS) {
            if (command.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void bindWidget(LineReader lineReader, String widgetName, String keySequence) {
        if (lineReader == null || keySequence == null) {
            return;
        }
        Reference reference = new Reference(widgetName);
        bind(lineReader, LineReader.MAIN, reference, keySequence);
        bind(lineReader, LineReader.EMACS, reference, keySequence);
        bind(lineReader, LineReader.VIINS, reference, keySequence);
    }

    private void bind(LineReader lineReader, String keymapName, Reference reference, String keySequence) {
        if (lineReader == null || reference == null || keySequence == null) {
            return;
        }
        java.util.Map<String, org.jline.keymap.KeyMap<org.jline.reader.Binding>> keyMaps = lineReader.getKeyMaps();
        if (keyMaps == null) {
            return;
        }
        org.jline.keymap.KeyMap<org.jline.reader.Binding> keyMap = keyMaps.get(keymapName);
        if (keyMap != null) {
            keyMap.bind(reference, keySequence);
        }
    }

    private void wrapWidget(LineReader lineReader, String widgetName, Widget widget) {
        if (lineReader == null || widget == null || isBlank(widgetName)) {
            return;
        }
        java.util.Map<String, Widget> widgets = lineReader.getWidgets();
        if (widgets == null) {
            return;
        }
        Widget original = widgets.get(widgetName);
        if (original == null) {
            return;
        }
        wrappedWidgets.put(widgetName, original);
        widgets.put(widgetName, widget);
    }

    private boolean delegateAndRefreshSlashPalette(LineReader lineReader, String widgetName) {
        if (!delegateWidget(lineReader, widgetName)) {
            return false;
        }
        refreshSlashPaletteIfNeeded(lineReader);
        return true;
    }

    private boolean delegateWidget(LineReader lineReader, String widgetName) {
        if (isBlank(widgetName)) {
            return false;
        }
        Widget widget = wrappedWidgets.get(widgetName);
        if (widget == null) {
            return false;
        }
        return widget.apply();
    }

    private boolean navigateSlashPalette(LineReader lineReader, int delta, String fallbackWidgetName) {
        PaletteSnapshot snapshot = getPaletteSnapshot();
        if (!snapshot.isOpen() || snapshot.items.isEmpty()) {
            return delegateWidget(lineReader, fallbackWidgetName);
        }
        movePaletteSelection(delta);
        refreshStatusAndDisplay(lineReader);
        return true;
    }

    private void refreshSlashPaletteIfNeeded(LineReader lineReader) {
        if (lineReader == null) {
            return;
        }
        Buffer buffer = lineReader.getBuffer();
        String line = buffer == null ? null : buffer.toString();
        if (!getPaletteSnapshot().isOpen() && (line == null || !line.startsWith("/"))) {
            return;
        }
        syncSlashPalette(lineReader);
        refreshStatusAndDisplay(lineReader);
    }

    private void syncSlashPalette(LineReader lineReader) {
        if (lineReader == null) {
            clearPalette();
            return;
        }
        Buffer buffer = lineReader.getBuffer();
        if (buffer == null) {
            clearPalette();
            return;
        }
        String line = buffer.toString();
        if (isBlank(line) || !line.startsWith("/")) {
            clearPalette();
            return;
        }
        updatePalette(line, suggest(line, buffer.cursor()));
    }

    private void updatePalette(String query, List<Candidate> candidates) {
        List<PaletteItemSnapshot> items = new ArrayList<PaletteItemSnapshot>();
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate == null) {
                    continue;
                }
                items.add(new PaletteItemSnapshot(
                        candidate.value(),
                        firstNonBlank(candidate.displ(), candidate.value()),
                        candidate.descr(),
                        candidate.group()
                ));
            }
        }
        synchronized (paletteLock) {
            String selectedValue = paletteSnapshot.selectedValue();
            int selectedIndex = resolveSelectedIndex(items, query, selectedValue);
            paletteSnapshot = new PaletteSnapshot(true, query, selectedIndex, items);
        }
        notifyStatusRefresh();
    }

    private int resolveSelectedIndex(List<PaletteItemSnapshot> items, String query, String selectedValue) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        if (!isBlank(selectedValue)) {
            for (int i = 0; i < items.size(); i++) {
                if (sameText(selectedValue, items.get(i).value)) {
                    return i;
                }
            }
        }
        if (!isBlank(query)) {
            for (int i = 0; i < items.size(); i++) {
                if (sameText(query, items.get(i).value)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private void clearPalette() {
        synchronized (paletteLock) {
            paletteSnapshot = PaletteSnapshot.closed();
        }
    }

    private PaletteItemSnapshot getSelectedPaletteItem() {
        synchronized (paletteLock) {
            if (!paletteSnapshot.isOpen() || paletteSnapshot.selectedIndex < 0 || paletteSnapshot.selectedIndex >= paletteSnapshot.items.size()) {
                return null;
            }
            return paletteSnapshot.items.get(paletteSnapshot.selectedIndex);
        }
    }

    private void refreshStatusAndDisplay(LineReader lineReader) {
        notifyStatusRefresh();
        if (lineReader == null) {
            return;
        }
        lineReader.callWidget(LineReader.REDRAW_LINE);
        lineReader.callWidget(LineReader.REDISPLAY);
    }

    private void notifyStatusRefresh() {
        Runnable refresh = statusRefresh;
        if (refresh != null) {
            refresh.run();
        }
    }

    private List<Candidate> rootCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (SlashCommandSpec spec : BUILT_IN_COMMANDS) {
            if (matches(spec.command, partial)) {
                candidates.add(commandCandidate(spec.command, spec.command, "Commands", spec.description, spec.requiresArgument));
            }
        }
        return candidates;
    }

    private List<Candidate> prefixCandidates(String prefix, List<Candidate> candidates) {
        if (isBlank(prefix) || candidates == null || candidates.isEmpty()) {
            return candidates == null ? Collections.<Candidate>emptyList() : candidates;
        }
        List<Candidate> prefixed = new ArrayList<Candidate>(candidates.size());
        for (Candidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            prefixed.add(new Candidate(
                    prefix + firstNonBlank(candidate.value(), ""),
                    firstNonBlank(candidate.displ(), candidate.value()),
                    candidate.group(),
                    candidate.descr(),
                    candidate.suffix(),
                    candidate.key(),
                    candidate.complete()
            ));
        }
        return prefixed;
    }

    private List<Candidate> customCommandCandidates(String partial) {
        if (customCommandRegistry == null) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (CustomCommandTemplate template : customCommandRegistry.list()) {
            if (template == null || !matches(template.getName(), partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    template.getName(),
                    template.getName(),
                    "Templates",
                    firstNonBlank(template.getDescription(), "Run custom command " + template.getName()),
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> themeCandidates(String partial) {
        if (tuiConfigManager == null) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String themeName : tuiConfigManager.listThemeNames()) {
            if (!matches(themeName, partial)) {
                continue;
            }
            candidates.add(new Candidate(themeName, themeName, "Themes", "Switch to theme " + themeName, null, null, true));
        }
        return candidates;
    }

    private List<Candidate> streamCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String option : STREAM_OPTIONS) {
            if (!matches(option, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    option,
                    option,
                    "Stream",
                    "Turn transcript streaming " + option,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> experimentalCandidates(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        if (tokens.size() == 1) {
            return experimentalFeatureCandidates("");
        }
        if (tokens.size() == 2 && !endsWithSpace) {
            return experimentalFeatureCandidates(tokens.get(1));
        }
        if (tokens.size() == 2 && endsWithSpace) {
            return experimentalToggleCandidates("");
        }
        if (tokens.size() == 3 && !endsWithSpace) {
            return experimentalToggleCandidates(tokens.get(2));
        }
        return Collections.emptyList();
    }

    private List<Candidate> experimentalFeatureCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String feature : EXPERIMENTAL_FEATURES) {
            if (!matches(feature, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    feature,
                    feature,
                    "Experimental",
                    describeExperimentalFeature(feature),
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private String describeExperimentalFeature(String feature) {
        if ("subagent".equalsIgnoreCase(feature)) {
            return "Toggle experimental subagent tool injection";
        }
        if ("agent-teams".equalsIgnoreCase(feature)) {
            return "Toggle experimental agent team tool injection";
        }
        return "Experimental runtime feature";
    }

    private List<Candidate> experimentalToggleCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String option : STREAM_OPTIONS) {
            if (!matches(option, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    option,
                    option,
                    "Experimental",
                    "Set experimental feature " + option,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> teamCandidates(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        if (tokens.size() == 1) {
            return teamActionCandidates("");
        }
        if (tokens.size() == 2 && !endsWithSpace) {
            return teamActionCandidates(tokens.get(1));
        }
        String action = tokens.get(1);
        if ("list".equalsIgnoreCase(action)) {
            return Collections.emptyList();
        }
        if ("status".equalsIgnoreCase(action) || "resume".equalsIgnoreCase(action)) {
            if (tokens.size() == 2 && endsWithSpace) {
                return teamIdCandidates("");
            }
            if (tokens.size() == 3 && !endsWithSpace) {
                return teamIdCandidates(tokens.get(2));
            }
            return Collections.emptyList();
        }
        if ("messages".equalsIgnoreCase(action)) {
            if (tokens.size() == 2 && endsWithSpace) {
                return teamIdCandidates("");
            }
            if (tokens.size() == 3 && !endsWithSpace) {
                return teamIdCandidates(tokens.get(2));
            }
            if (tokens.size() == 3 && endsWithSpace) {
                return teamMessageLimitCandidates("");
            }
            if (tokens.size() == 4 && !endsWithSpace) {
                return teamMessageLimitCandidates(tokens.get(3));
            }
        }
        return Collections.emptyList();
    }

    private List<Candidate> teamActionCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String action : TEAM_ACTIONS) {
            if (!matches(action, partial)) {
                continue;
            }
            candidates.add(commandCandidate(action, action, "Team", describeTeamAction(action), !"list".equalsIgnoreCase(action)));
        }
        return candidates;
    }

    private List<Candidate> teamIdCandidates(String partial) {
        Supplier<List<String>> supplier = teamCandidateSupplier;
        List<String> teamIds = supplier == null ? null : supplier.get();
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String teamId : teamIds) {
            if (isBlank(teamId) || !matches(teamId, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    teamId,
                    teamId,
                    "Team",
                    "Inspect persisted team " + teamId,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> teamMessageLimitCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String option : TEAM_MESSAGE_LIMITS) {
            if (!matches(option, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    option,
                    option,
                    "Team",
                    "Read up to " + option + " persisted team messages",
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private String describeTeamAction(String action) {
        if ("list".equalsIgnoreCase(action)) {
            return "List persisted teams in the current workspace";
        }
        if ("status".equalsIgnoreCase(action)) {
            return "Show one persisted team's current snapshot";
        }
        if ("messages".equalsIgnoreCase(action)) {
            return "Show recent messages from a persisted team mailbox";
        }
        if ("resume".equalsIgnoreCase(action)) {
            return "Load a persisted team snapshot and reopen its board view";
        }
        return "Team action";
    }

    private List<Candidate> skillCandidates(String partial) {
        Supplier<List<String>> supplier = skillCandidateSupplier;
        List<String> skills = supplier == null ? null : supplier.get();
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String skill : skills) {
            if (isBlank(skill) || !matches(skill, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    skill,
                    skill,
                    "Skills",
                    "Inspect coding skill " + skill,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> agentCandidates(String partial) {
        Supplier<List<String>> supplier = agentCandidateSupplier;
        List<String> agents = supplier == null ? null : supplier.get();
        if (agents == null || agents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String agent : agents) {
            if (isBlank(agent) || !matches(agent, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    agent,
                    agent,
                    "Agents",
                    "Inspect coding agent " + agent,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> mcpCandidates(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        if (tokens.size() == 1) {
            return mcpActionCandidates("");
        }
        if (tokens.size() == 2 && !endsWithSpace) {
            return mcpActionCandidates(tokens.get(1));
        }
        String action = tokens.get(1);
        if ("add".equalsIgnoreCase(action)) {
            return mcpAddCandidates(tokens, endsWithSpace);
        }
        if ("list".equalsIgnoreCase(action)) {
            return Collections.emptyList();
        }
        if (tokens.size() == 2 && endsWithSpace) {
            return mcpServerNameCandidates(action, "");
        }
        if (tokens.size() == 3 && !endsWithSpace) {
            return mcpServerNameCandidates(action, tokens.get(2));
        }
        return Collections.emptyList();
    }

    private List<Candidate> mcpActionCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String action : MCP_ACTIONS) {
            if (!matches(action, partial)) {
                continue;
            }
            candidates.add(commandCandidate(action, action, "MCP", describeMcpAction(action), true));
        }
        return candidates;
    }

    private String describeMcpAction(String action) {
        if ("list".equalsIgnoreCase(action)) {
            return "List MCP services";
        }
        if ("add".equalsIgnoreCase(action)) {
            return "Add a global MCP service";
        }
        if ("enable".equalsIgnoreCase(action)) {
            return "Enable an MCP service in this workspace";
        }
        if ("disable".equalsIgnoreCase(action)) {
            return "Disable an MCP service in this workspace";
        }
        if ("pause".equalsIgnoreCase(action)) {
            return "Pause an MCP service for this session";
        }
        if ("resume".equalsIgnoreCase(action)) {
            return "Resume an MCP service for this session";
        }
        if ("retry".equalsIgnoreCase(action)) {
            return "Reconnect an MCP service";
        }
        if ("remove".equalsIgnoreCase(action)) {
            return "Delete a global MCP service";
        }
        return "MCP action";
    }

    private List<Candidate> mcpAddCandidates(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.size() < 2) {
            return Collections.emptyList();
        }
        if (tokens.size() == 2 && endsWithSpace) {
            return mcpAddOptionCandidates("");
        }
        if (tokens.size() == 3 && !endsWithSpace) {
            return mcpAddOptionCandidates(tokens.get(2));
        }
        if (tokens.size() == 3 && endsWithSpace) {
            if (MCP_TRANSPORT_FLAG.equalsIgnoreCase(tokens.get(2))) {
                return mcpTransportCandidates("");
            }
            return Collections.emptyList();
        }
        if (tokens.size() == 4 && !endsWithSpace && MCP_TRANSPORT_FLAG.equalsIgnoreCase(tokens.get(2))) {
            return mcpTransportCandidates(tokens.get(3));
        }
        return Collections.emptyList();
    }

    private List<Candidate> mcpAddOptionCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        if (matches(MCP_TRANSPORT_FLAG, partial)) {
            candidates.add(commandCandidate(
                    MCP_TRANSPORT_FLAG,
                    MCP_TRANSPORT_FLAG,
                    "MCP",
                    "Choose the MCP transport: stdio, sse, http",
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> mcpTransportCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String transport : MCP_TRANSPORT_OPTIONS) {
            if (!matches(transport, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    transport,
                    transport,
                    "MCP",
                    "Use " + transport + " transport",
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> mcpServerNameCandidates(String action, String partial) {
        Supplier<List<String>> supplier = mcpServerCandidateSupplier;
        List<String> serverNames = supplier == null ? null : supplier.get();
        if (serverNames == null || serverNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String serverName : serverNames) {
            if (isBlank(serverName) || !matches(serverName, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    serverName,
                    serverName,
                    "MCP",
                    describeMcpServerAction(action, serverName),
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private String describeMcpServerAction(String action, String serverName) {
        if ("enable".equalsIgnoreCase(action)) {
            return "Enable MCP service " + serverName;
        }
        if ("disable".equalsIgnoreCase(action)) {
            return "Disable MCP service " + serverName;
        }
        if ("pause".equalsIgnoreCase(action)) {
            return "Pause MCP service " + serverName + " for this session";
        }
        if ("resume".equalsIgnoreCase(action)) {
            return "Resume MCP service " + serverName + " for this session";
        }
        if ("retry".equalsIgnoreCase(action)) {
            return "Reconnect MCP service " + serverName;
        }
        if ("remove".equalsIgnoreCase(action)) {
            return "Delete global MCP service " + serverName;
        }
        return "Use MCP service " + serverName;
    }

    private List<Candidate> providerCandidates(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        if (tokens.size() == 1) {
            return providerActionCandidates("");
        }
        if (tokens.size() == 2 && !endsWithSpace) {
            String action = tokens.get(1);
            if (isExactProviderAction(action)) {
                List<Candidate> nestedCandidates = providerNameCandidates(action, "");
                if (!nestedCandidates.isEmpty()) {
                    return prefixCandidates("/provider " + action + " ", nestedCandidates);
                }
            }
            return providerActionCandidates(action);
        }
        String action = tokens.get(1);
        if (("add".equalsIgnoreCase(action) || "edit".equalsIgnoreCase(action))
                && (tokens.size() > 2 || endsWithSpace)) {
            return providerMutationCandidates(action, tokens, endsWithSpace);
        }
        if (tokens.size() == 2 && endsWithSpace) {
            return providerNameCandidates(action, "");
        }
        if (tokens.size() == 3 && !endsWithSpace) {
            return providerNameCandidates(action, tokens.get(2));
        }
        return Collections.emptyList();
    }

    private boolean isExactProviderAction(String value) {
        if (isBlank(value)) {
            return false;
        }
        for (String action : PROVIDER_ACTIONS) {
            if (action.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private List<Candidate> providerActionCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String action : PROVIDER_ACTIONS) {
            if (!matches(action, partial)) {
                continue;
            }
            candidates.add(commandCandidate(action, action, "Provider", "provider " + action, true));
        }
        return candidates;
    }

    private List<Candidate> providerNameCandidates(String action, String partial) {
        if ("add".equalsIgnoreCase(action)) {
            return Collections.emptyList();
        }
        if ("default".equalsIgnoreCase(action)) {
            return providerDefaultCandidates(partial);
        }
        Supplier<List<String>> supplier = profileCandidateSupplier;
        List<String> profiles = supplier == null ? null : supplier.get();
        if (profiles == null || profiles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String profile : profiles) {
            if (!matches(profile, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    profile,
                    profile,
                    "Profiles",
                    describeProviderProfileAction(action, profile),
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private String describeProviderProfileAction(String action, String profile) {
        if ("save".equalsIgnoreCase(action)) {
            return "Overwrite saved profile " + profile;
        }
        if ("edit".equalsIgnoreCase(action)) {
            return "Edit profile " + profile;
        }
        if ("remove".equalsIgnoreCase(action)) {
            return "Delete profile " + profile;
        }
        return "Use profile " + profile;
    }

    private List<Candidate> providerDefaultCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String option : PROVIDER_DEFAULT_OPTIONS) {
            if (!matches(option, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    option,
                    option,
                    "Provider",
                    "Clear the global default profile",
                    null,
                    null,
                    true
            ));
        }
        Supplier<List<String>> supplier = profileCandidateSupplier;
        List<String> profiles = supplier == null ? null : supplier.get();
        if (profiles == null) {
            return candidates;
        }
        for (String profile : profiles) {
            if (!matches(profile, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    profile,
                    profile,
                    "Profiles",
                    "Set the default profile to " + profile,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> providerMutationCandidates(String action, List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.size() < 2) {
            return Collections.emptyList();
        }
        if ("edit".equalsIgnoreCase(action)) {
            if (tokens.size() == 2 && endsWithSpace) {
                return providerNameCandidates(action, "");
            }
            if (tokens.size() == 3 && !endsWithSpace) {
                return providerNameCandidates(action, tokens.get(2));
            }
        } else if (tokens.size() == 2 && endsWithSpace) {
            return Collections.emptyList();
        }

        if (tokens.size() <= 3) {
            return endsWithSpace ? providerMutationOptionCandidates("") : Collections.<Candidate>emptyList();
        }

        String lastToken = tokens.get(tokens.size() - 1);
        if (endsWithSpace) {
            if (isProviderMutationOption(lastToken)) {
                return providerMutationValueCandidates(lastToken, "");
            }
            return providerMutationOptionCandidates("");
        }

        if (lastToken.startsWith("--")) {
            return providerMutationOptionCandidates(lastToken);
        }

        String previousToken = tokens.get(tokens.size() - 2);
        if (isProviderMutationOption(previousToken)) {
            return providerMutationValueCandidates(previousToken, lastToken);
        }
        return Collections.emptyList();
    }

    private List<Candidate> providerMutationOptionCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String option : PROVIDER_MUTATION_OPTIONS) {
            if (!matches(option, partial)) {
                continue;
            }
            candidates.add(commandCandidate(
                    option,
                    option,
                    "Provider",
                    describeProviderMutationOption(option),
                    true
            ));
        }
        return candidates;
    }

    private String describeProviderMutationOption(String option) {
        if ("--provider".equalsIgnoreCase(option)) {
            return "Set provider name";
        }
        if ("--protocol".equalsIgnoreCase(option)) {
            return "Set protocol: chat, responses";
        }
        if ("--model".equalsIgnoreCase(option)) {
            return "Set default model";
        }
        if ("--base-url".equalsIgnoreCase(option)) {
            return "Set custom base URL";
        }
        if ("--api-key".equalsIgnoreCase(option)) {
            return "Set profile API key";
        }
        if ("--clear-model".equalsIgnoreCase(option)) {
            return "Clear saved model";
        }
        if ("--clear-base-url".equalsIgnoreCase(option)) {
            return "Clear saved base URL";
        }
        if ("--clear-api-key".equalsIgnoreCase(option)) {
            return "Clear saved API key";
        }
        return "Provider option";
    }

    private boolean isProviderMutationOption(String value) {
        if (isBlank(value)) {
            return false;
        }
        for (String option : PROVIDER_MUTATION_OPTIONS) {
            if (option.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private List<Candidate> providerMutationValueCandidates(String option, String partial) {
        if ("--provider".equalsIgnoreCase(option)) {
            return providerTypeCandidates(partial);
        }
        if ("--protocol".equalsIgnoreCase(option)) {
            return providerProtocolCandidates(partial);
        }
        return Collections.emptyList();
    }

    private List<Candidate> providerTypeCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType == null || isBlank(platformType.getPlatform()) || !matches(platformType.getPlatform(), partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    platformType.getPlatform(),
                    platformType.getPlatform(),
                    "Providers",
                    "Use provider " + platformType.getPlatform(),
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> providerProtocolCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        addProviderProtocolCandidate(candidates, CliProtocol.CHAT, partial);
        addProviderProtocolCandidate(candidates, CliProtocol.RESPONSES, partial);
        return candidates;
    }

    private void addProviderProtocolCandidate(List<Candidate> candidates, CliProtocol protocol, String partial) {
        if (protocol == null || isBlank(protocol.getValue()) || !matches(protocol.getValue(), partial)) {
            return;
        }
        candidates.add(new Candidate(
                protocol.getValue(),
                protocol.getValue(),
                "Protocols",
                "Use protocol " + protocol.getValue(),
                null,
                null,
                true
        ));
    }

    private List<Candidate> modelCandidates(String partial) {
        LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<String, Candidate>();
        Supplier<List<ModelCompletionCandidate>> supplier = modelCandidateSupplier;
        List<ModelCompletionCandidate> modelCandidates = supplier == null ? null : supplier.get();
        if (modelCandidates != null) {
            for (ModelCompletionCandidate modelCandidate : modelCandidates) {
                if (modelCandidate == null || isBlank(modelCandidate.getModel()) || !matches(modelCandidate.getModel(), partial)) {
                    continue;
                }
                String model = modelCandidate.getModel();
                candidates.put(model.toLowerCase(Locale.ROOT), new Candidate(
                        model,
                        model,
                        "Models",
                        firstNonBlank(modelCandidate.getDescription(), "Use model " + model),
                        null,
                        null,
                        true
                ));
            }
        }
        if (matches(MODEL_RESET, partial)) {
            candidates.put("__reset__", new Candidate(
                    MODEL_RESET,
                    MODEL_RESET,
                    "Model",
                    "Reset the workspace model override",
                    null,
                    null,
                    true
            ));
        }
        return new ArrayList<Candidate>(candidates.values());
    }

    private List<Candidate> sessionCandidates(String partial) {
        CodingSessionManager manager = sessionManager;
        if (manager == null) {
            return Collections.emptyList();
        }
        try {
            List<Candidate> candidates = new ArrayList<Candidate>();
            for (CodingSessionDescriptor descriptor : manager.list()) {
                if (descriptor == null || !matches(descriptor.getSessionId(), partial)) {
                    continue;
                }
                candidates.add(new Candidate(
                        descriptor.getSessionId(),
                        descriptor.getSessionId(),
                        "Sessions",
                        firstNonBlank(descriptor.getSummary(), "Saved session"),
                        null,
                        null,
                        true
                ));
            }
            return candidates;
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    private List<Candidate> processCandidates(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        if (tokens.size() == 1) {
            return processSubcommandCandidates("");
        }
        if (tokens.size() == 2 && !endsWithSpace) {
            return processSubcommandCandidates(tokens.get(1));
        }
        ProcessCommandSpec commandSpec = findProcessCommandSpec(tokens.get(1));
        if (commandSpec == null) {
            return Collections.emptyList();
        }
        if (tokens.size() == 2 && endsWithSpace) {
            return processIdCandidates(commandSpec, "");
        }
        if (tokens.size() == 3 && !endsWithSpace) {
            return processIdCandidates(commandSpec, tokens.get(2));
        }
        if (tokens.size() == 3 && endsWithSpace) {
            if (commandSpec.acceptsFreeText) {
                return Collections.emptyList();
            }
            if (commandSpec.supportsLimit) {
                return processLimitCandidates(commandSpec, "");
            }
            return Collections.emptyList();
        }
        if (tokens.size() == 4 && !endsWithSpace && commandSpec.supportsLimit) {
            return processLimitCandidates(commandSpec, tokens.get(3));
        }
        return Collections.emptyList();
    }

    private List<Candidate> processSubcommandCandidates(String partial) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (ProcessCommandSpec action : PROCESS_COMMANDS) {
            if (!matches(action.name, partial)) {
                continue;
            }
            candidates.add(commandCandidate(
                    action.name,
                    action.name,
                    "Process",
                    action.description,
                    true
            ));
        }
        return candidates;
    }

    private List<Candidate> processIdCandidates(ProcessCommandSpec commandSpec, String partial) {
        Supplier<List<ProcessCompletionCandidate>> supplier = processCandidateSupplier;
        List<ProcessCompletionCandidate> processCandidates = supplier == null ? null : supplier.get();
        if (processCandidates == null || processCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (ProcessCompletionCandidate processCandidate : processCandidates) {
            if (processCandidate == null || !matches(processCandidate.getProcessId(), partial)) {
                continue;
            }
            candidates.add(commandCandidate(
                    processCandidate.getProcessId(),
                    processCandidate.getProcessId(),
                    "Processes",
                    firstNonBlank(processCandidate.getDescription(), "Process " + processCandidate.getProcessId()),
                    commandSpec.supportsLimit || commandSpec.acceptsFreeText
            ));
        }
        return candidates;
    }

    private List<Candidate> processLimitCandidates(ProcessCommandSpec commandSpec, String partial) {
        List<String> limits = "logs".equalsIgnoreCase(commandSpec.name) ? PROCESS_LOG_LIMITS : PROCESS_FOLLOW_LIMITS;
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String limit : limits) {
            if (!matches(limit, partial)) {
                continue;
            }
            candidates.add(new Candidate(
                    limit,
                    limit,
                    "Limits",
                    commandSpec.name + " limit " + limit,
                    null,
                    null,
                    true
            ));
        }
        return candidates;
    }

    private ProcessCommandSpec findProcessCommandSpec(String value) {
        if (isBlank(value)) {
            return null;
        }
        for (ProcessCommandSpec commandSpec : PROCESS_COMMANDS) {
            if (commandSpec.name.equalsIgnoreCase(value)) {
                return commandSpec;
            }
        }
        return null;
    }

    private Candidate commandCandidate(String value,
                                       String display,
                                       String group,
                                       String description,
                                       boolean appendSpace) {
        String candidateValue = appendSpace ? value + " " : value;
        return new Candidate(candidateValue, display, group, description, null, null, !appendSpace);
    }

    private String tokenFragment(List<String> tokens, boolean endsWithSpace) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        if (endsWithSpace) {
            return "";
        }
        return tokens.get(tokens.size() - 1);
    }

    private List<String> splitTokens(String value) {
        if (isBlank(value)) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(trimmed.split("\\s+"));
    }

    private boolean matches(String candidate, String partial) {
        if (isBlank(candidate)) {
            return false;
        }
        if (isBlank(partial) || "/".equals(partial)) {
            return true;
        }
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        String normalizedPartial = partial.toLowerCase(Locale.ROOT);
        return normalizedCandidate.startsWith(normalizedPartial);
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

    private static final class SlashCommandSpec {

        private final String command;
        private final String description;
        private final boolean requiresArgument;

        private SlashCommandSpec(String command, String description, boolean requiresArgument) {
            this.command = command;
            this.description = description;
            this.requiresArgument = requiresArgument;
        }
    }

    public static final class ProcessCompletionCandidate {

        private final String processId;
        private final String description;

        public ProcessCompletionCandidate(String processId, String description) {
            this.processId = processId;
            this.description = description;
        }

        public String getProcessId() {
            return processId;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class ModelCompletionCandidate {

        private final String model;
        private final String description;

        public ModelCompletionCandidate(String model, String description) {
            this.model = model;
            this.description = description;
        }

        public String getModel() {
            return model;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final class ProcessCommandSpec {

        private final String name;
        private final String description;
        private final boolean supportsLimit;
        private final boolean acceptsFreeText;

        private ProcessCommandSpec(String name, String description, boolean supportsLimit, boolean acceptsFreeText) {
            this.name = name;
            this.description = description;
            this.supportsLimit = supportsLimit;
            this.acceptsFreeText = acceptsFreeText;
        }
    }

    public static final class PaletteSnapshot {

        private final boolean open;
        private final String query;
        private final int selectedIndex;
        private final List<PaletteItemSnapshot> items;

        private PaletteSnapshot(boolean open, String query, int selectedIndex, List<PaletteItemSnapshot> items) {
            this.open = open;
            this.query = query;
            this.selectedIndex = selectedIndex;
            this.items = items == null
                    ? Collections.<PaletteItemSnapshot>emptyList()
                    : Collections.unmodifiableList(new ArrayList<PaletteItemSnapshot>(items));
        }

        public static PaletteSnapshot closed() {
            return new PaletteSnapshot(false, "", -1, Collections.<PaletteItemSnapshot>emptyList());
        }

        public boolean isOpen() {
            return open;
        }

        public String getQuery() {
            return query;
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }

        public List<PaletteItemSnapshot> getItems() {
            return items;
        }

        PaletteSnapshot withSelectedIndex(int selectedIndex) {
            return new PaletteSnapshot(open, query, selectedIndex, items);
        }

        PaletteSnapshot copy() {
            return new PaletteSnapshot(open, query, selectedIndex, items);
        }

        String selectedValue() {
            if (selectedIndex < 0 || selectedIndex >= items.size()) {
                return null;
            }
            return items.get(selectedIndex).value;
        }
    }

    public static final class PaletteItemSnapshot {

        private final String value;
        private final String display;
        private final String description;
        private final String group;

        private PaletteItemSnapshot(String value, String display, String description, String group) {
            this.value = value;
            this.display = display;
            this.description = description;
            this.group = group;
        }

        public String getValue() {
            return value;
        }

        public String getDisplay() {
            return display;
        }

        public String getDescription() {
            return description;
        }

        public String getGroup() {
            return group;
        }
    }

    enum SlashMenuAction {
        INSERT_AND_MENU,
        MENU_ONLY,
        INSERT_ONLY
    }

    enum EnterAction {
        ACCEPT,
        IGNORE_EMPTY
    }
}

