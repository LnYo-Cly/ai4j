package io.github.lnyocly.ai4j.cli.acp;

import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpStatusSnapshot;
import io.github.lnyocly.ai4j.cli.runtime.CliTeamStateManager;
import io.github.lnyocly.ai4j.cli.runtime.TeamBoardRenderSupport;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.StoredCodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpointFormatter;
import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.coding.skill.CodingSkillDescriptor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

final class AcpSlashCommandSupport {

    private static final int DEFAULT_EVENT_LIMIT = 12;
    private static final int DEFAULT_PROCESS_LOG_LIMIT = 800;

    private static final List<CommandSpec> COMMANDS = Collections.unmodifiableList(Arrays.asList(
            new CommandSpec("help", "Show ACP slash commands", null),
            new CommandSpec("status", "Show current session status", null),
            new CommandSpec("session", "Show current session metadata", null),
            new CommandSpec("save", "Persist the current session state", null),
            new CommandSpec("providers", "List saved provider profiles", null),
            new CommandSpec("provider", "Show or switch the active provider profile", "use <profile> | save <profile> | add/edit/default/remove ..."),
            new CommandSpec("model", "Show or switch the active model override", "optional model name | reset"),
            new CommandSpec("experimental", "Show or switch experimental runtime features", "optional feature | <subagent|agent-teams> <on|off>"),
            new CommandSpec("skills", "List coding skills or inspect one skill", "optional skill name"),
            new CommandSpec("agents", "List coding agents or inspect one agent", "optional agent name"),
            new CommandSpec("mcp", "Show MCP services and status", null),
            new CommandSpec("sessions", "List saved sessions", null),
            new CommandSpec("history", "Show session lineage", "optional target session id"),
            new CommandSpec("tree", "Show the session tree", "optional root session id"),
            new CommandSpec("events", "Show recent session events", "optional limit"),
            new CommandSpec("team", "Show current team board or persisted team state", "optional: list | status [team-id] | messages [team-id] [limit] | resume [team-id]"),
            new CommandSpec("compacts", "Show compact history", "optional limit"),
            new CommandSpec("checkpoint", "Show current checkpoint summary", null),
            new CommandSpec("processes", "List managed processes", null),
            new CommandSpec("process", "Inspect process status or logs", "status <process-id> | logs <process-id> [limit]")
    ));

    private AcpSlashCommandSupport() {
    }

    static List<Map<String, Object>> availableCommands() {
        List<Map<String, Object>> commands = new ArrayList<Map<String, Object>>();
        for (CommandSpec spec : COMMANDS) {
            Map<String, Object> command = new LinkedHashMap<String, Object>();
            command.put("name", spec.name);
            command.put("description", spec.description);
            if (!isBlank(spec.inputHint)) {
                Map<String, Object> input = new LinkedHashMap<String, Object>();
                input.put("hint", spec.inputHint);
                command.put("input", input);
            }
            commands.add(command);
        }
        return commands;
    }

    static boolean supports(String input) {
        return parse(input) != null;
    }

    static ExecutionResult execute(Context context, String input) throws Exception {
        ParsedCommand command = parse(input);
        if (context == null || command == null) {
            return null;
        }
        String name = command.name;
        if ("help".equals(name)) {
            return ExecutionResult.of(renderHelp());
        }
        if ("status".equals(name)) {
            return ExecutionResult.of(renderStatus(context));
        }
        if ("session".equals(name)) {
            return ExecutionResult.of(renderSession(context));
        }
        if ("save".equals(name)) {
            StoredCodingSession stored = context.sessionManager.save(context.session);
            return ExecutionResult.of("saved session: " + stored.getSessionId() + " -> " + stored.getStorePath());
        }
        if ("providers".equals(name)) {
            return ExecutionResult.of(executeRuntimeCommand(context, RuntimeCommand.PROVIDERS, null));
        }
        if ("provider".equals(name)) {
            return ExecutionResult.of(executeRuntimeCommand(context, RuntimeCommand.PROVIDER, command.argument));
        }
        if ("model".equals(name)) {
            return ExecutionResult.of(executeRuntimeCommand(context, RuntimeCommand.MODEL, command.argument));
        }
        if ("experimental".equals(name)) {
            return ExecutionResult.of(executeRuntimeCommand(context, RuntimeCommand.EXPERIMENTAL, command.argument));
        }
        if ("skills".equals(name)) {
            return ExecutionResult.of(renderSkills(context, command.argument));
        }
        if ("agents".equals(name)) {
            return ExecutionResult.of(renderAgents(context, command.argument));
        }
        if ("mcp".equals(name)) {
            return ExecutionResult.of(renderMcp(context));
        }
        if ("sessions".equals(name)) {
            return ExecutionResult.of(renderSessions(context));
        }
        if ("history".equals(name)) {
            return ExecutionResult.of(renderHistory(context, command.argument));
        }
        if ("tree".equals(name)) {
            return ExecutionResult.of(renderTree(context, command.argument));
        }
        if ("events".equals(name)) {
            return ExecutionResult.of(renderEvents(context, command.argument));
        }
        if ("team".equals(name)) {
            return ExecutionResult.of(renderTeam(context, command.argument));
        }
        if ("compacts".equals(name)) {
            return ExecutionResult.of(renderCompacts(context, command.argument));
        }
        if ("checkpoint".equals(name)) {
            return ExecutionResult.of(renderCheckpoint(context));
        }
        if ("processes".equals(name)) {
            return ExecutionResult.of(renderProcesses(context));
        }
        if ("process".equals(name)) {
            return ExecutionResult.of(renderProcess(context, command.argument));
        }
        return null;
    }

    private static String executeRuntimeCommand(Context context,
                                                RuntimeCommand command,
                                                String argument) throws Exception {
        if (context == null || context.runtimeCommandHandler == null || command == null) {
            return "command unavailable in this ACP session";
        }
        if (command == RuntimeCommand.PROVIDERS) {
            return context.runtimeCommandHandler.executeProviders();
        }
        if (command == RuntimeCommand.PROVIDER) {
            return context.runtimeCommandHandler.executeProvider(argument);
        }
        if (command == RuntimeCommand.MODEL) {
            return context.runtimeCommandHandler.executeModel(argument);
        }
        if (command == RuntimeCommand.EXPERIMENTAL) {
            return context.runtimeCommandHandler.executeExperimental(argument);
        }
        return "command unavailable in this ACP session";
    }

    private static ParsedCommand parse(String input) {
        String normalized = trimToNull(input);
        if (normalized == null || !normalized.startsWith("/")) {
            return null;
        }
        String body = normalized.substring(1).trim();
        if (body.isEmpty()) {
            return null;
        }
        int space = body.indexOf(' ');
        String name = (space < 0 ? body : body.substring(0, space)).trim().toLowerCase(Locale.ROOT);
        if (findSpec(name) == null) {
            return null;
        }
        String argument = space < 0 ? null : trimToNull(body.substring(space + 1));
        return new ParsedCommand(name, argument);
    }

    private static CommandSpec findSpec(String name) {
        if (isBlank(name)) {
            return null;
        }
        for (CommandSpec spec : COMMANDS) {
            if (spec.name.equalsIgnoreCase(name.trim())) {
                return spec;
            }
        }
        return null;
    }

    private static String renderHelp() {
        StringBuilder builder = new StringBuilder();
        builder.append("ACP slash commands:\n");
        for (CommandSpec spec : COMMANDS) {
            builder.append("- /").append(spec.name).append(" | ").append(spec.description);
            if (!isBlank(spec.inputHint)) {
                builder.append(" | input: ").append(spec.inputHint);
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderStatus(Context context) {
        ManagedCodingSession session = context.session;
        if (session == null) {
            return "status: (none)";
        }
        CodingSessionSnapshot snapshot = snapshot(session);
        StringBuilder builder = new StringBuilder();
        builder.append("status:\n");
        builder.append("- session=").append(session.getSessionId()).append('\n');
        builder.append("- provider=").append(firstNonBlank(session.getProvider(), "(none)"))
                .append(", protocol=").append(firstNonBlank(session.getProtocol(), "(none)"))
                .append(", model=").append(firstNonBlank(session.getModel(), "(none)")).append('\n');
        builder.append("- workspace=").append(firstNonBlank(session.getWorkspace(), "(none)")).append('\n');
        builder.append("- mode=").append(context.options != null && context.options.isNoSession() ? "memory-only" : "persistent")
                .append(", memory=").append(snapshot == null ? 0 : snapshot.getMemoryItemCount())
                .append(", activeProcesses=").append(snapshot == null ? 0 : snapshot.getActiveProcessCount())
                .append(", restoredProcesses=").append(snapshot == null ? 0 : snapshot.getRestoredProcessCount())
                .append(", tokens=").append(snapshot == null ? 0 : snapshot.getEstimatedContextTokens()).append('\n');
        String mcpSummary = renderMcpSummary(context.mcpRuntimeManager);
        if (!isBlank(mcpSummary)) {
            builder.append("- mcp=").append(mcpSummary).append('\n');
        }
        builder.append("- checkpointGoal=").append(clip(snapshot == null ? null : snapshot.getCheckpointGoal(), 120)).append('\n');
        builder.append("- compact=").append(firstNonBlank(snapshot == null ? null : snapshot.getLastCompactMode(), "none"));
        return builder.toString().trim();
    }

    private static String renderSession(Context context) {
        ManagedCodingSession session = context.session;
        if (session == null) {
            return "session: (none)";
        }
        CodingSessionDescriptor descriptor = session.toDescriptor();
        CodingSessionSnapshot snapshot = snapshot(session);
        StringBuilder builder = new StringBuilder();
        builder.append("session:\n");
        builder.append("- id=").append(descriptor.getSessionId()).append('\n');
        builder.append("- root=").append(firstNonBlank(descriptor.getRootSessionId(), "(none)"))
                .append(", parent=").append(firstNonBlank(descriptor.getParentSessionId(), "(none)")).append('\n');
        builder.append("- provider=").append(firstNonBlank(descriptor.getProvider(), "(none)"))
                .append(", protocol=").append(firstNonBlank(descriptor.getProtocol(), "(none)"))
                .append(", model=").append(firstNonBlank(descriptor.getModel(), "(none)")).append('\n');
        builder.append("- workspace=").append(firstNonBlank(descriptor.getWorkspace(), "(none)"))
                .append(", mode=").append(context.options != null && context.options.isNoSession() ? "memory-only" : "persistent").append('\n');
        builder.append("- created=").append(formatTimestamp(descriptor.getCreatedAtEpochMs()))
                .append(", updated=").append(formatTimestamp(descriptor.getUpdatedAtEpochMs())).append('\n');
        builder.append("- memory=").append(descriptor.getMemoryItemCount())
                .append(", processes=").append(descriptor.getProcessCount())
                .append(" (active=").append(descriptor.getActiveProcessCount())
                .append(", restored=").append(descriptor.getRestoredProcessCount()).append(")").append('\n');
        builder.append("- tokens=").append(snapshot == null ? 0 : snapshot.getEstimatedContextTokens()).append('\n');
        builder.append("- checkpoint=").append(clip(snapshot == null ? null : snapshot.getCheckpointGoal(), 160)).append('\n');
        builder.append("- compact=").append(firstNonBlank(snapshot == null ? null : snapshot.getLastCompactMode(), "none")).append('\n');
        builder.append("- summary=").append(clip(descriptor.getSummary(), 220));
        return builder.toString().trim();
    }

    private static String renderSkills(Context context, String argument) {
        WorkspaceContext workspaceContext = context.session == null || context.session.getSession() == null
                ? null
                : context.session.getSession().getWorkspaceContext();
        if (workspaceContext == null) {
            return "skills: (none)";
        }
        List<CodingSkillDescriptor> skills = workspaceContext.getAvailableSkills();
        if (!isBlank(argument)) {
            CodingSkillDescriptor selected = findSkill(skills, argument);
            if (selected == null) {
                return "skills: unknown skill `" + argument.trim() + "`";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("skill:\n");
            builder.append("- name=").append(firstNonBlank(selected.getName(), "skill")).append('\n');
            builder.append("- source=").append(firstNonBlank(selected.getSource(), "unknown")).append('\n');
            builder.append("- path=").append(firstNonBlank(selected.getSkillFilePath(), "(missing)")).append('\n');
            builder.append("- description=").append(firstNonBlank(selected.getDescription(), "No description available."));
            return builder.toString().trim();
        }
        if (skills == null || skills.isEmpty()) {
            return "skills: (none)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("skills:\n");
        builder.append("- count=").append(skills.size()).append('\n');
        for (CodingSkillDescriptor skill : skills) {
            if (skill == null) {
                continue;
            }
            builder.append("- ").append(firstNonBlank(skill.getName(), "skill"))
                    .append(" | source=").append(firstNonBlank(skill.getSource(), "unknown"))
                    .append(" | path=").append(firstNonBlank(skill.getSkillFilePath(), "(missing)"))
                    .append(" | description=").append(firstNonBlank(skill.getDescription(), "No description available."))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderAgents(Context context, String argument) {
        CodingCliAgentFactory.PreparedCodingAgent prepared = context.prepared;
        CodingAgentDefinitionRegistry registry = prepared == null || prepared.getAgent() == null
                ? null
                : prepared.getAgent().getDefinitionRegistry();
        List<CodingAgentDefinition> definitions = registry == null ? null : registry.listDefinitions();
        if (!isBlank(argument)) {
            CodingAgentDefinition selected = findAgent(definitions, argument);
            if (selected == null) {
                return "agents: unknown agent `" + argument.trim() + "`";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("agent:\n");
            builder.append("- name=").append(firstNonBlank(selected.getName(), "agent")).append('\n');
            builder.append("- tool=").append(firstNonBlank(selected.getToolName(), "(none)")).append('\n');
            builder.append("- model=").append(firstNonBlank(selected.getModel(), "(inherit)")).append('\n');
            builder.append("- background=").append(selected.isBackground()).append('\n');
            builder.append("- tools=").append(renderAllowedTools(selected)).append('\n');
            builder.append("- description=").append(firstNonBlank(selected.getDescription(), "No description available."));
            return builder.toString().trim();
        }
        if (definitions == null || definitions.isEmpty()) {
            return "agents: (none)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("agents:\n");
        builder.append("- count=").append(definitions.size()).append('\n');
        for (CodingAgentDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            builder.append("- ").append(firstNonBlank(definition.getName(), "agent"))
                    .append(" | tool=").append(firstNonBlank(definition.getToolName(), "(none)"))
                    .append(" | model=").append(firstNonBlank(definition.getModel(), "(inherit)"))
                    .append(" | background=").append(definition.isBackground())
                    .append(" | tools=").append(renderAllowedTools(definition))
                    .append(" | description=").append(firstNonBlank(definition.getDescription(), "No description available."))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderMcp(Context context) {
        CliMcpRuntimeManager runtimeManager = context.mcpRuntimeManager;
        if (runtimeManager == null || !runtimeManager.hasStatuses()) {
            return "mcp: (none)";
        }
        List<CliMcpStatusSnapshot> statuses = runtimeManager.getStatuses();
        if (statuses == null || statuses.isEmpty()) {
            return "mcp: (none)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("mcp:\n");
        for (CliMcpStatusSnapshot status : statuses) {
            if (status == null) {
                continue;
            }
            builder.append("- ").append(firstNonBlank(status.getServerName(), "(server)"))
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
        return builder.toString().trim();
    }

    private static String renderSessions(Context context) throws Exception {
        List<CodingSessionDescriptor> sessions = context.sessionManager.list();
        if (sessions == null || sessions.isEmpty()) {
            return "sessions: (none)";
        }
        List<CodingSessionDescriptor> sorted = new ArrayList<CodingSessionDescriptor>(sessions);
        Collections.sort(sorted, new Comparator<CodingSessionDescriptor>() {
            @Override
            public int compare(CodingSessionDescriptor left, CodingSessionDescriptor right) {
                long l = left == null ? 0L : left.getUpdatedAtEpochMs();
                long r = right == null ? 0L : right.getUpdatedAtEpochMs();
                return Long.compare(r, l);
            }
        });
        StringBuilder builder = new StringBuilder("sessions:\n");
        for (CodingSessionDescriptor session : sorted) {
            if (session == null) {
                continue;
            }
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

    private static String renderHistory(Context context, String targetSessionId) throws Exception {
        List<CodingSessionDescriptor> sessions = mergeCurrentSession(context.sessionManager.list(), context.session);
        CodingSessionDescriptor target = resolveTargetDescriptor(sessions, context.session, targetSessionId);
        if (target == null) {
            return "history: (none)";
        }
        List<CodingSessionDescriptor> history = resolveHistory(sessions, target);
        if (history.isEmpty()) {
            return "history: (none)";
        }
        StringBuilder builder = new StringBuilder("history:\n");
        for (CodingSessionDescriptor session : history) {
            builder.append("- ").append(session.getSessionId())
                    .append(" | parent=").append(firstNonBlank(session.getParentSessionId(), "(root)"))
                    .append(" | updated=").append(formatTimestamp(session.getUpdatedAtEpochMs()))
                    .append(" | ").append(clip(session.getSummary(), 120))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderTree(Context context, String rootArgument) throws Exception {
        List<CodingSessionDescriptor> sessions = mergeCurrentSession(context.sessionManager.list(), context.session);
        List<String> lines = renderTreeLines(sessions, rootArgument, context.session == null ? null : context.session.getSessionId());
        if (lines.isEmpty()) {
            return "tree: (none)";
        }
        StringBuilder builder = new StringBuilder("tree:\n");
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderEvents(Context context, String limitArgument) throws Exception {
        ManagedCodingSession session = context.session;
        if (session == null) {
            return "events: (no current session)";
        }
        Integer limit = parseLimit(limitArgument);
        List<SessionEvent> events = context.sessionManager.listEvents(session.getSessionId(), limit, null);
        if (events == null || events.isEmpty()) {
            return "events: (none)";
        }
        StringBuilder builder = new StringBuilder("events:\n");
        for (SessionEvent event : events) {
            if (event == null) {
                continue;
            }
            builder.append("- ").append(formatTimestamp(event.getTimestamp()))
                    .append(" | ").append(event.getType())
                    .append(event.getStep() == null ? "" : " | step=" + event.getStep())
                    .append(" | ").append(clip(event.getSummary(), 160))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderCompacts(Context context, String limitArgument) throws Exception {
        ManagedCodingSession session = context.session;
        if (session == null) {
            return "compacts: (no current session)";
        }
        int limit = parseLimit(limitArgument);
        List<SessionEvent> events = context.sessionManager.listEvents(session.getSessionId(), null, null);
        List<String> lines = buildCompactLines(events, limit);
        if (lines.isEmpty()) {
            return "compacts: (none)";
        }
        StringBuilder builder = new StringBuilder("compacts:\n");
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderCheckpoint(Context context) {
        CodingSessionSnapshot snapshot = snapshot(context.session);
        String summary = snapshot == null ? null : snapshot.getSummary();
        if (isBlank(summary)) {
            return "checkpoint: (none)";
        }
        return "checkpoint:\n" + CodingSessionCheckpointFormatter.render(CodingSessionCheckpointFormatter.parse(summary));
    }

    private static String renderTeam(Context context, String argument) throws Exception {
        if (context == null || context.session == null || context.sessionManager == null) {
            return "team: (none)";
        }
        if (!isBlank(argument)) {
            CliTeamStateManager manager = new CliTeamStateManager(resolveWorkspaceRoot(context));
            List<String> tokens = splitWhitespace(argument);
            if (tokens.isEmpty()) {
                return manager.renderListOutput();
            }
            String action = tokens.get(0).toLowerCase(Locale.ROOT);
            if ("list".equals(action)) {
                return manager.renderListOutput();
            }
            if ("status".equals(action)) {
                return manager.renderStatusOutput(tokens.size() > 1 ? tokens.get(1) : null);
            }
            if ("messages".equals(action)) {
                Integer limit = tokens.size() > 2 ? Integer.valueOf(parseLimit(tokens.get(2))) : null;
                return manager.renderMessagesOutput(tokens.size() > 1 ? tokens.get(1) : null, limit);
            }
            if ("resume".equals(action)) {
                return manager.renderResumeOutput(tokens.size() > 1 ? tokens.get(1) : null);
            }
            return "Usage: /team | /team list | /team status [team-id] | /team messages [team-id] [limit] | /team resume [team-id]";
        }
        List<SessionEvent> events = context.sessionManager.listEvents(context.session.getSessionId(), null, null);
        return TeamBoardRenderSupport.renderBoardOutput(TeamBoardRenderSupport.renderBoardLines(events));
    }

    private static java.nio.file.Path resolveWorkspaceRoot(Context context) {
        String workspace = context == null || context.session == null ? null : trimToNull(context.session.getWorkspace());
        if (workspace == null && context != null && context.options != null) {
            workspace = trimToNull(context.options.getWorkspace());
        }
        if (workspace == null) {
            return java.nio.file.Paths.get(".").toAbsolutePath().normalize();
        }
        return java.nio.file.Paths.get(workspace).toAbsolutePath().normalize();
    }

    private static String renderProcesses(Context context) {
        CodingSessionSnapshot snapshot = snapshot(context.session);
        List<BashProcessInfo> processes = snapshot == null ? null : snapshot.getProcesses();
        if (processes == null || processes.isEmpty()) {
            return "processes: (none)";
        }
        StringBuilder builder = new StringBuilder("processes:\n");
        for (BashProcessInfo process : processes) {
            if (process == null) {
                continue;
            }
            builder.append("- ").append(firstNonBlank(process.getProcessId(), "(process)"))
                    .append(" | status=").append(process.getStatus())
                    .append(" | mode=").append(process.isControlAvailable() ? "live" : "metadata-only")
                    .append(" | restored=").append(process.isRestored())
                    .append(" | cwd=").append(clip(process.getWorkingDirectory(), 48))
                    .append(" | cmd=").append(clip(process.getCommand(), 72))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String renderProcess(Context context, String rawArguments) throws Exception {
        if (context.session == null || context.session.getSession() == null) {
            return "process: (no current session)";
        }
        if (isBlank(rawArguments)) {
            return "Usage: /process status <process-id>\nUsage: /process logs <process-id> [limit]";
        }
        String[] parts = rawArguments.trim().split("\\s+", 3);
        String action = parts[0].toLowerCase(Locale.ROOT);
        if ("status".equals(action)) {
            if (parts.length < 2) {
                return "Usage: /process status <process-id>";
            }
            return renderProcessStatusOutput(context.session.getSession().processStatus(parts[1]));
        }
        if ("logs".equals(action)) {
            if (parts.length < 2) {
                return "Usage: /process logs <process-id> [limit]";
            }
            Integer limit = parts.length >= 3 ? parseLimit(parts[2]) : DEFAULT_PROCESS_LOG_LIMIT;
            BashProcessInfo info = context.session.getSession().processStatus(parts[1]);
            BashProcessLogChunk logs = context.session.getSession().processLogs(parts[1], null, limit);
            return renderProcessDetailsOutput(info, logs);
        }
        return "Unknown process action: " + action + ". Use /process status <process-id> or /process logs <process-id> [limit].";
    }

    private static CodingSessionSnapshot snapshot(ManagedCodingSession session) {
        return session == null || session.getSession() == null ? null : session.getSession().snapshot();
    }

    private static List<CodingSessionDescriptor> mergeCurrentSession(List<CodingSessionDescriptor> sessions, ManagedCodingSession currentSession) {
        LinkedHashMap<String, CodingSessionDescriptor> merged = new LinkedHashMap<String, CodingSessionDescriptor>();
        if (sessions != null) {
            for (CodingSessionDescriptor session : sessions) {
                if (session != null && !isBlank(session.getSessionId())) {
                    merged.put(session.getSessionId(), session);
                }
            }
        }
        if (currentSession != null && !isBlank(currentSession.getSessionId())) {
            merged.put(currentSession.getSessionId(), currentSession.toDescriptor());
        }
        return new ArrayList<CodingSessionDescriptor>(merged.values());
    }

    private static CodingSessionDescriptor resolveTargetDescriptor(List<CodingSessionDescriptor> sessions,
                                                                   ManagedCodingSession currentSession,
                                                                   String targetSessionId) {
        String requested = trimToNull(targetSessionId);
        if (requested == null && currentSession != null) {
            requested = currentSession.getSessionId();
        }
        if (requested == null) {
            return null;
        }
        for (CodingSessionDescriptor session : sessions) {
            if (session != null && requested.equals(session.getSessionId())) {
                return session;
            }
        }
        return null;
    }

    private static List<CodingSessionDescriptor> resolveHistory(List<CodingSessionDescriptor> sessions, CodingSessionDescriptor target) {
        if (target == null || sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CodingSessionDescriptor> byId = new LinkedHashMap<String, CodingSessionDescriptor>();
        for (CodingSessionDescriptor session : sessions) {
            if (session != null && !isBlank(session.getSessionId())) {
                byId.put(session.getSessionId(), session);
            }
        }
        ArrayDeque<CodingSessionDescriptor> chain = new ArrayDeque<CodingSessionDescriptor>();
        Set<String> seen = new LinkedHashSet<String>();
        CodingSessionDescriptor current = target;
        while (current != null && !seen.contains(current.getSessionId())) {
            chain.addFirst(current);
            seen.add(current.getSessionId());
            current = byId.get(current.getParentSessionId());
        }
        return new ArrayList<CodingSessionDescriptor>(chain);
    }

    private static List<String> renderTreeLines(List<CodingSessionDescriptor> sessions, String rootArgument, String currentSessionId) {
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CodingSessionDescriptor> byId = new LinkedHashMap<String, CodingSessionDescriptor>();
        Map<String, List<CodingSessionDescriptor>> children = new LinkedHashMap<String, List<CodingSessionDescriptor>>();
        for (CodingSessionDescriptor session : sessions) {
            if (session == null || isBlank(session.getSessionId())) {
                continue;
            }
            byId.put(session.getSessionId(), session);
            String parentId = trimToNull(session.getParentSessionId());
            List<CodingSessionDescriptor> siblings = children.get(parentId);
            if (siblings == null) {
                siblings = new ArrayList<CodingSessionDescriptor>();
                children.put(parentId, siblings);
            }
            siblings.add(session);
        }
        for (List<CodingSessionDescriptor> siblings : children.values()) {
            Collections.sort(siblings, new Comparator<CodingSessionDescriptor>() {
                @Override
                public int compare(CodingSessionDescriptor left, CodingSessionDescriptor right) {
                    long l = left == null ? 0L : left.getCreatedAtEpochMs();
                    long r = right == null ? 0L : right.getCreatedAtEpochMs();
                    int createdCompare = Long.compare(l, r);
                    if (createdCompare != 0) {
                        return createdCompare;
                    }
                    String leftId = left == null ? "" : firstNonBlank(left.getSessionId(), "");
                    String rightId = right == null ? "" : firstNonBlank(right.getSessionId(), "");
                    return leftId.compareTo(rightId);
                }
            });
        }
        String requestedRoot = trimToNull(rootArgument);
        List<CodingSessionDescriptor> roots = new ArrayList<CodingSessionDescriptor>();
        if (requestedRoot != null) {
            CodingSessionDescriptor root = byId.get(requestedRoot);
            if (root != null) {
                roots.add(root);
            }
        } else {
            List<CodingSessionDescriptor> top = children.get(null);
            if (top != null) {
                roots.addAll(top);
            }
        }
        if (roots.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < roots.size(); i++) {
            renderTreeNode(lines, children, roots.get(i), "", i == roots.size() - 1, currentSessionId);
        }
        return lines;
    }

    private static void renderTreeNode(List<String> lines,
                                       Map<String, List<CodingSessionDescriptor>> children,
                                       CodingSessionDescriptor session,
                                       String prefix,
                                       boolean last,
                                       String currentSessionId) {
        if (lines == null || session == null) {
            return;
        }
        String branch = prefix + (prefix.isEmpty() ? "" : (last ? "\\- " : "|- "));
        lines.add((prefix.isEmpty() ? "" : branch) + renderTreeLabel(session, currentSessionId));
        List<CodingSessionDescriptor> descendants = children.get(session.getSessionId());
        if (descendants == null || descendants.isEmpty()) {
            return;
        }
        String childPrefix = prefix + (prefix.isEmpty() ? "" : (last ? "   " : "|  "));
        if (prefix.isEmpty()) {
            childPrefix = "";
        }
        for (int i = 0; i < descendants.size(); i++) {
            boolean childLast = i == descendants.size() - 1;
            String nextPrefix = prefix.isEmpty() ? "" : childPrefix;
            renderTreeNode(lines, children, descendants.get(i), nextPrefix, childLast, currentSessionId);
        }
    }

    private static String renderTreeLabel(CodingSessionDescriptor session, String currentSessionId) {
        StringBuilder builder = new StringBuilder();
        builder.append(session.getSessionId());
        if (!isBlank(currentSessionId) && currentSessionId.equals(session.getSessionId())) {
            builder.append(" [current]");
        }
        builder.append(" | updated=").append(formatTimestamp(session.getUpdatedAtEpochMs()));
        builder.append(" | ").append(clip(session.getSummary(), 120));
        return builder.toString();
    }

    private static List<String> buildCompactLines(List<SessionEvent> events, int limit) {
        List<SessionEvent> compactEvents = new ArrayList<SessionEvent>();
        if (events != null) {
            for (SessionEvent event : events) {
                if (event != null && event.getType() == SessionEventType.COMPACT) {
                    compactEvents.add(event);
                }
            }
        }
        if (compactEvents.isEmpty()) {
            return Collections.emptyList();
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
                    .append(" | tokens=").append(formatCompactDelta(payloadInt(payload, "estimatedTokensBefore"), payloadInt(payload, "estimatedTokensAfter")))
                    .append(" | items=").append(formatCompactDelta(payloadInt(payload, "beforeItemCount"), payloadInt(payload, "afterItemCount")));
            if (payloadBoolean(payload, "splitTurn")) {
                line.append(" | splitTurn");
            }
            String checkpointGoal = clip(payloadString(payload, "checkpointGoal"), 64);
            if (!isBlank(checkpointGoal) && !"(none)".equals(checkpointGoal)) {
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

    private static String renderProcessStatusOutput(BashProcessInfo processInfo) {
        if (processInfo == null) {
            return "process status: (none)";
        }
        StringBuilder builder = new StringBuilder("process status:\n");
        appendProcessSummary(builder, processInfo);
        return builder.toString().trim();
    }

    private static String renderProcessDetailsOutput(BashProcessInfo processInfo, BashProcessLogChunk logs) {
        StringBuilder builder = new StringBuilder(renderProcessStatusOutput(processInfo));
        String content = logs == null ? null : logs.getContent();
        if (!isBlank(content)) {
            builder.append('\n').append('\n').append("process logs:\n").append(content.trim());
        }
        return builder.toString().trim();
    }

    private static void appendProcessSummary(StringBuilder builder, BashProcessInfo processInfo) {
        if (builder == null || processInfo == null) {
            return;
        }
        builder.append("- id=").append(firstNonBlank(processInfo.getProcessId(), "(process)")).append('\n');
        builder.append("- status=").append(processInfo.getStatus())
                .append(", mode=").append(processInfo.isControlAvailable() ? "live" : "metadata-only")
                .append(", restored=").append(processInfo.isRestored()).append('\n');
        builder.append("- pid=").append(processInfo.getPid() == null ? "(none)" : processInfo.getPid())
                .append(", exitCode=").append(processInfo.getExitCode() == null ? "(running)" : processInfo.getExitCode()).append('\n');
        builder.append("- cwd=").append(firstNonBlank(processInfo.getWorkingDirectory(), "(none)")).append('\n');
        builder.append("- command=").append(firstNonBlank(processInfo.getCommand(), "(none)")).append('\n');
        builder.append("- started=").append(formatTimestamp(processInfo.getStartedAt()))
                .append(", ended=").append(processInfo.getEndedAt() == null ? "(running)" : formatTimestamp(processInfo.getEndedAt()));
    }

    private static CodingSkillDescriptor findSkill(List<CodingSkillDescriptor> skills, String name) {
        if (skills == null || isBlank(name)) {
            return null;
        }
        String normalized = name.trim();
        for (CodingSkillDescriptor skill : skills) {
            if (skill != null && !isBlank(skill.getName()) && skill.getName().trim().equalsIgnoreCase(normalized)) {
                return skill;
            }
        }
        return null;
    }

    private static CodingAgentDefinition findAgent(List<CodingAgentDefinition> definitions, String nameOrToolName) {
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

    private static String renderAllowedTools(CodingAgentDefinition definition) {
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

    private static String renderMcpSummary(CliMcpRuntimeManager runtimeManager) {
        if (runtimeManager == null || !runtimeManager.hasStatuses()) {
            return null;
        }
        int connected = 0;
        int errors = 0;
        int paused = 0;
        int disabled = 0;
        int missing = 0;
        int tools = 0;
        for (CliMcpStatusSnapshot status : runtimeManager.getStatuses()) {
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

    private static String resolveCompactMode(SessionEvent event) {
        if (event == null || event.getPayload() == null) {
            return "unknown";
        }
        Object automatic = event.getPayload().get("automatic");
        if (automatic instanceof Boolean) {
            return Boolean.TRUE.equals(automatic) ? "auto" : "manual";
        }
        String summary = firstNonBlank(event.getSummary(), "");
        if (summary.startsWith("auto compact")) {
            return "auto";
        }
        if (summary.startsWith("manual compact")) {
            return "manual";
        }
        return "unknown";
    }

    private static String formatCompactDelta(Integer before, Integer after) {
        return defaultInt(before) + "->" + defaultInt(after);
    }

    private static Integer parseLimit(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return DEFAULT_EVENT_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            return parsed <= 0 ? DEFAULT_EVENT_LIMIT : parsed;
        } catch (NumberFormatException ignore) {
            return DEFAULT_EVENT_LIMIT;
        }
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static Integer payloadInt(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean payloadBoolean(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return false;
        }
        Object value = payload.get(key);
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value);
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String payloadString(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String formatTimestamp(long epochMs) {
        if (epochMs <= 0L) {
            return "(unknown)";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMs));
    }

    private static String clip(String value, int maxChars) {
        String normalized = value == null ? null : value.replace('\r', ' ').replace('\n', ' ').trim();
        if (isBlank(normalized)) {
            return "(none)";
        }
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars));
    }

    private static String firstNonBlank(String... values) {
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static List<String> splitWhitespace(String value) {
        if (isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.trim().split("\\s+"));
    }

    static final class Context {
        final ManagedCodingSession session;
        final CodingSessionManager sessionManager;
        final CodeCommandOptions options;
        final CodingCliAgentFactory.PreparedCodingAgent prepared;
        final CliMcpRuntimeManager mcpRuntimeManager;
        final RuntimeCommandHandler runtimeCommandHandler;

        Context(ManagedCodingSession session,
                CodingSessionManager sessionManager,
                CodeCommandOptions options,
                CodingCliAgentFactory.PreparedCodingAgent prepared,
                CliMcpRuntimeManager mcpRuntimeManager) {
            this(session, sessionManager, options, prepared, mcpRuntimeManager, null);
        }

        Context(ManagedCodingSession session,
                CodingSessionManager sessionManager,
                CodeCommandOptions options,
                CodingCliAgentFactory.PreparedCodingAgent prepared,
                CliMcpRuntimeManager mcpRuntimeManager,
                RuntimeCommandHandler runtimeCommandHandler) {
            this.session = session;
            this.sessionManager = sessionManager;
            this.options = options;
            this.prepared = prepared;
            this.mcpRuntimeManager = mcpRuntimeManager;
            this.runtimeCommandHandler = runtimeCommandHandler;
        }
    }

    interface RuntimeCommandHandler {

        String executeProviders() throws Exception;

        String executeProvider(String argument) throws Exception;

        String executeModel(String argument) throws Exception;

        String executeExperimental(String argument) throws Exception;
    }

    static final class ExecutionResult {
        private final String output;

        private ExecutionResult(String output) {
            this.output = output;
        }

        static ExecutionResult of(String output) {
            return new ExecutionResult(output);
        }

        String getOutput() {
            return output;
        }
    }

    private static final class CommandSpec {
        private final String name;
        private final String description;
        private final String inputHint;

        private CommandSpec(String name, String description, String inputHint) {
            this.name = name;
            this.description = description;
            this.inputHint = inputHint;
        }
    }

    private static final class ParsedCommand {
        private final String name;
        private final String argument;

        private ParsedCommand(String name, String argument) {
            this.name = name;
            this.argument = argument;
        }
    }

    private enum RuntimeCommand {
        PROVIDERS,
        PROVIDER,
        MODEL,
        EXPERIMENTAL
    }
}
