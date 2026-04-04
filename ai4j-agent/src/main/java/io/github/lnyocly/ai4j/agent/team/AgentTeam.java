package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.team.tool.AgentTeamToolExecutor;
import io.github.lnyocly.ai4j.agent.team.tool.AgentTeamToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AgentTeam implements AgentTeamControl {

    private static final String SYSTEM_MEMBER = "system";
    private static final String LEAD_MEMBER = "lead";

    private final AgentTeamPlanner planner;
    private final AgentTeamSynthesizer synthesizer;
    private final AgentTeamOptions options;
    private final String teamId;
    private final List<RuntimeMember> orderedMembers;
    private final Map<String, RuntimeMember> membersById;
    private final AgentTeamMessageBus messageBus;
    private final AgentTeamStateStore stateStore;
    private final AgentTeamPlanApproval planApproval;
    private final List<AgentTeamHook> hooks;
    private final AgentTeamToolRegistry teamToolRegistry;

    private final Object memberLock = new Object();
    private final Object runtimeLock = new Object();

    private volatile AgentTeamTaskBoard activeBoard;
    private volatile List<AgentTeamTaskState> lastTaskStates = Collections.emptyList();
    private volatile String activeObjective;
    private volatile String lastOutput;
    private volatile int lastRounds;
    private volatile long lastRunStartedAt;
    private volatile long lastRunCompletedAt;

    AgentTeam(AgentTeamBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder is required");
        }
        this.options = builder.getOptions() == null ? AgentTeamOptions.builder().build() : builder.getOptions();
        this.teamId = firstNonBlank(builder.getTeamId(), UUID.randomUUID().toString());
        this.messageBus = resolveMessageBus(builder);
        this.stateStore = resolveStateStore(builder);
        this.planApproval = builder.getPlanApproval();
        this.hooks = builder.getHooks() == null ? Collections.<AgentTeamHook>emptyList() : new ArrayList<>(builder.getHooks());
        this.teamToolRegistry = new AgentTeamToolRegistry();

        List<AgentTeamMember> rawMembers = builder.getMembers();
        if (rawMembers == null || rawMembers.isEmpty()) {
            throw new IllegalStateException("at least one team member is required");
        }
        this.orderedMembers = new ArrayList<>();
        this.membersById = new LinkedHashMap<>();
        for (AgentTeamMember member : rawMembers) {
            RuntimeMember runtimeMember = RuntimeMember.from(member);
            if (membersById.containsKey(runtimeMember.id)) {
                throw new IllegalStateException("duplicate team member id: " + runtimeMember.id);
            }
            orderedMembers.add(runtimeMember);
            membersById.put(runtimeMember.id, runtimeMember);
        }

        Agent leadAgent = builder.getLeadAgent();

        AgentTeamPlanner plannerOverride = builder.getPlanner();
        if (plannerOverride != null) {
            this.planner = plannerOverride;
        } else {
            Agent plannerAgent = builder.getPlannerAgent();
            if (plannerAgent == null) {
                plannerAgent = leadAgent;
            }
            if (plannerAgent == null) {
                throw new IllegalStateException("planner or plannerAgent or leadAgent is required");
            }
            this.planner = new LlmAgentTeamPlanner(plannerAgent);
        }

        AgentTeamSynthesizer synthesizerOverride = builder.getSynthesizer();
        if (synthesizerOverride != null) {
            this.synthesizer = synthesizerOverride;
        } else {
            Agent synthesizerAgent = builder.getSynthesizerAgent();
            if (synthesizerAgent == null) {
                synthesizerAgent = leadAgent;
            }
            if (synthesizerAgent == null && builder.getPlannerAgent() != null) {
                synthesizerAgent = builder.getPlannerAgent();
            }
            if (synthesizerAgent == null) {
                throw new IllegalStateException("synthesizer or synthesizerAgent or leadAgent is required");
            }
            this.synthesizer = new LlmAgentTeamSynthesizer(synthesizerAgent);
        }
    }

    public static AgentTeamBuilder builder() {
        return AgentTeamBuilder.builder();
    }

    public String getTeamId() {
        return teamId;
    }

    public AgentTeamState snapshotState() {
        return AgentTeamState.builder()
                .teamId(teamId)
                .objective(currentObjective())
                .members(snapshotMemberViews())
                .taskStates(copyTaskStates(listTaskStates()))
                .messages(options.isEnableMessageBus() ? copyMessages(messageBus.snapshot()) : Collections.<AgentTeamMessage>emptyList())
                .lastOutput(lastOutput)
                .lastRounds(lastRounds)
                .lastRunStartedAt(lastRunStartedAt)
                .lastRunCompletedAt(lastRunCompletedAt)
                .updatedAt(System.currentTimeMillis())
                .runActive(currentBoard() != null)
                .build();
    }

    public AgentTeamState loadPersistedState() {
        if (stateStore == null) {
            return null;
        }
        AgentTeamState state = stateStore.load(teamId);
        restoreState(state);
        return state;
    }

    public void restoreState(AgentTeamState state) {
        if (state == null) {
            return;
        }
        if (!isSameTeam(state)) {
            throw new IllegalArgumentException("team state does not belong to teamId=" + teamId);
        }
        if (options.isEnableMessageBus()) {
            messageBus.restore(copyMessages(state.getMessages()));
        }
        synchronized (runtimeLock) {
            activeObjective = state.getObjective();
            activeBoard = null;
            lastTaskStates = copyTaskStates(state.getTaskStates());
        }
        lastOutput = state.getLastOutput();
        lastRounds = state.getLastRounds();
        lastRunStartedAt = state.getLastRunStartedAt();
        lastRunCompletedAt = state.getLastRunCompletedAt();
    }

    public boolean clearPersistedState() {
        if (options.isEnableMessageBus()) {
            messageBus.clear();
        }
        synchronized (runtimeLock) {
            activeBoard = null;
            activeObjective = null;
            lastTaskStates = Collections.emptyList();
        }
        lastOutput = null;
        lastRounds = 0;
        lastRunStartedAt = 0L;
        lastRunCompletedAt = 0L;
        if (stateStore == null) {
            return false;
        }
        return stateStore.delete(teamId);
    }

    @Override
    public void registerMember(AgentTeamMember member) {
        if (!options.isAllowDynamicMemberRegistration()) {
            throw new IllegalStateException("dynamic member registration is disabled");
        }
        RuntimeMember runtimeMember = RuntimeMember.from(member);
        synchronized (memberLock) {
            if (membersById.containsKey(runtimeMember.id)) {
                throw new IllegalStateException("duplicate team member id: " + runtimeMember.id);
            }
            orderedMembers.add(runtimeMember);
            membersById.put(runtimeMember.id, runtimeMember);
        }
        persistState();
    }

    @Override
    public boolean unregisterMember(String memberId) {
        if (!options.isAllowDynamicMemberRegistration()) {
            throw new IllegalStateException("dynamic member registration is disabled");
        }
        String normalized = normalize(memberId);
        if (normalized == null) {
            return false;
        }
        synchronized (memberLock) {
            if (!membersById.containsKey(normalized)) {
                return false;
            }
            if (orderedMembers.size() <= 1) {
                return false;
            }
            RuntimeMember removed = membersById.remove(normalized);
            if (removed != null) {
                orderedMembers.remove(removed);
                persistState();
                return true;
            }
            return false;
        }
    }

    @Override
    public List<AgentTeamMember> listMembers() {
        return snapshotMembers();
    }

    @Override
    public List<AgentTeamMessage> listMessages() {
        if (!options.isEnableMessageBus()) {
            return Collections.emptyList();
        }
        return messageBus.snapshot();
    }

    @Override
    public List<AgentTeamMessage> listMessagesFor(String memberId, int limit) {
        if (!options.isEnableMessageBus()) {
            return Collections.emptyList();
        }
        String normalizedMemberId = normalize(memberId);
        if (normalizedMemberId != null && !"*".equals(normalizedMemberId)) {
            validateKnownMemberId(normalizedMemberId, true, "memberId");
        }
        return messageBus.historyFor(normalizedMemberId, limit);
    }

    @Override
    public void publishMessage(AgentTeamMessage message) {
        publishMessageInternal(message);
    }

    @Override
    public void sendMessage(String fromMemberId, String toMemberId, String type, String taskId, String content) {
        String from = normalize(fromMemberId);
        String to = normalize(toMemberId);
        validateKnownMemberId(from, true, "fromMemberId");
        validateKnownMemberId(to, false, "toMemberId");
        publishMessage(from, to, type, taskId, content);
    }

    @Override
    public void broadcastMessage(String fromMemberId, String type, String taskId, String content) {
        String from = normalize(fromMemberId);
        validateKnownMemberId(from, true, "fromMemberId");
        publishMessage(from, "*", type, taskId, content);
    }

    @Override
    public List<AgentTeamTaskState> listTaskStates() {
        AgentTeamTaskBoard board = currentBoard();
        if (board != null) {
            return board.snapshot();
        }
        synchronized (runtimeLock) {
            if (lastTaskStates == null || lastTaskStates.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(lastTaskStates);
        }
    }

    @Override
    public boolean claimTask(String taskId, String memberId) {
        AgentTeamTaskBoard board = currentBoard();
        if (board == null) {
            return false;
        }
        String normalizedMemberId = normalize(memberId);
        validateKnownMemberId(normalizedMemberId, false, "memberId");
        boolean claimed = board.claimTask(taskId, normalizedMemberId);
        if (claimed) {
            fireTaskStateChanged(board.getTaskState(taskId), resolveMemberView(normalizedMemberId),
                    "Claimed by " + normalizedMemberId + ".");
        }
        return claimed;
    }

    @Override
    public boolean releaseTask(String taskId, String memberId, String reason) {
        AgentTeamTaskBoard board = currentBoard();
        if (board == null) {
            return false;
        }
        String normalizedMemberId = normalize(memberId);
        validateKnownMemberId(normalizedMemberId, false, "memberId");
        boolean released = board.releaseTask(taskId, normalizedMemberId, reason);
        if (released) {
            fireTaskStateChanged(board.getTaskState(taskId), resolveMemberView(normalizedMemberId),
                    firstNonBlank(reason, "Released back to queue."));
        }
        return released;
    }

    @Override
    public boolean reassignTask(String taskId, String fromMemberId, String toMemberId) {
        AgentTeamTaskBoard board = currentBoard();
        if (board == null) {
            return false;
        }
        String from = normalize(fromMemberId);
        String to = normalize(toMemberId);
        validateKnownMemberId(from, false, "fromMemberId");
        validateKnownMemberId(to, false, "toMemberId");
        boolean reassigned = board.reassignTask(taskId, from, to);
        if (reassigned) {
            fireTaskStateChanged(board.getTaskState(taskId), resolveMemberView(to),
                    "Reassigned from " + from + " to " + to + ".");
        }
        return reassigned;
    }

    @Override
    public boolean heartbeatTask(String taskId, String memberId) {
        AgentTeamTaskBoard board = currentBoard();
        if (board == null) {
            return false;
        }
        String normalizedMemberId = normalize(memberId);
        validateKnownMemberId(normalizedMemberId, false, "memberId");
        boolean heartbeated = board.heartbeatTask(taskId, normalizedMemberId);
        if (heartbeated) {
            fireTaskStateChanged(board.getTaskState(taskId), resolveMemberView(normalizedMemberId),
                    "Heartbeat from " + normalizedMemberId + ".");
        }
        return heartbeated;
    }

    public AgentTeamResult run(String objective) throws Exception {
        return run(AgentRequest.builder().input(objective).build());
    }

    public AgentTeamResult run(AgentRequest request) throws Exception {
        long start = System.currentTimeMillis();
        String objective = request == null ? "" : toText(request.getInput());
        lastRunStartedAt = start;
        lastRunCompletedAt = 0L;

        if (options.isEnableMessageBus()) {
            messageBus.clear();
        }

        List<AgentTeamMember> members = snapshotMembers();
        fireBeforePlan(objective, members);

        AgentTeamPlan plan = planner.plan(objective, members, options);
        if (plan == null) {
            plan = AgentTeamPlan.builder().tasks(Collections.<AgentTeamTask>emptyList()).build();
        }
        AgentTeamTaskBoard board = new AgentTeamTaskBoard(plan.getTasks());
        plan = plan.toBuilder().tasks(board.normalizedTasks()).build();

        synchronized (runtimeLock) {
            activeBoard = board;
            lastTaskStates = Collections.emptyList();
            activeObjective = objective;
        }
        persistState();

        try {
            fireAfterPlan(objective, plan);
            ensurePlanApproved(objective, plan, members);

            DispatchOutcome dispatch = dispatchTasks(objective, board);

            AgentResult synthesis = synthesizer.synthesize(objective, plan, dispatch.results, options);
            fireAfterSynthesis(objective, synthesis);

            List<AgentTeamTaskState> taskStates = board.snapshot();
            rememberTaskStates(taskStates);
            lastRounds = dispatch.rounds;
            lastOutput = synthesis == null ? "" : synthesis.getOutputText();
            lastRunCompletedAt = System.currentTimeMillis();
            persistState();

            return AgentTeamResult.builder()
                    .teamId(teamId)
                    .objective(objective)
                    .plan(plan)
                    .memberResults(dispatch.results)
                    .taskStates(taskStates)
                    .messages(options.isEnableMessageBus() ? messageBus.snapshot() : Collections.<AgentTeamMessage>emptyList())
                    .rounds(dispatch.rounds)
                    .synthesisResult(synthesis)
                    .output(synthesis == null ? "" : synthesis.getOutputText())
                    .totalDurationMillis(System.currentTimeMillis() - start)
                    .build();
        } finally {
            rememberTaskStates(board.snapshot());
            lastRunCompletedAt = System.currentTimeMillis();
            persistState();
            synchronized (runtimeLock) {
                activeBoard = null;
                activeObjective = null;
            }
        }
    }

    private void ensurePlanApproved(String objective, AgentTeamPlan plan, List<AgentTeamMember> members) {
        if (planApproval == null) {
            if (options.isRequirePlanApproval()) {
                throw new IllegalStateException("plan approval is required but no planApproval callback provided");
            }
            return;
        }
        boolean approved = planApproval.approve(objective, plan, members, options);
        if (!approved) {
            throw new IllegalStateException("plan rejected by planApproval callback");
        }
    }

    private DispatchOutcome dispatchTasks(String objective, AgentTeamTaskBoard board) throws Exception {
        if (board == null || board.size() == 0) {
            return new DispatchOutcome(Collections.<AgentTeamMemberResult>emptyList(), 0);
        }

        List<AgentTeamMemberResult> results = new ArrayList<>();
        int rounds = 0;
        int maxRounds = options.getMaxRounds() <= 0 ? 64 : options.getMaxRounds();

        while (board.hasWorkRemaining()) {
            if (options.getTaskClaimTimeoutMillis() > 0L) {
                board.recoverTimedOutClaims(options.getTaskClaimTimeoutMillis(), "task claim timeout");
            }
            if (rounds >= maxRounds) {
                board.markStalledAsBlocked("max rounds exceeded: " + maxRounds);
                break;
            }

            int batchSize = options.isParallelDispatch() ? Math.max(1, options.getMaxConcurrency()) : 1;
            List<AgentTeamTaskState> readyTasks = board.nextReadyTasks(batchSize);
            if (readyTasks.isEmpty()) {
                board.markStalledAsBlocked("unresolved dependencies or cyclic plan");
                break;
            }

            rounds++;
            List<PreparedDispatch> prepared = new ArrayList<>();
            for (AgentTeamTaskState state : readyTasks) {
                RuntimeMember member;
                try {
                    member = resolveMember(state.getTask() == null ? null : state.getTask().getMemberId());
                } catch (Exception e) {
                    board.markFailed(state.getTaskId(), e.getMessage(), 0L);
                    AgentTeamMemberResult failure = AgentTeamMemberResult.builder()
                            .taskId(state.getTaskId())
                            .task(state.getTask())
                            .taskStatus(AgentTeamTaskStatus.FAILED)
                            .memberId(state.getTask() == null ? null : state.getTask().getMemberId())
                            .error(e.getMessage())
                            .durationMillis(0L)
                            .build();
                    results.add(failure);
                    fireAfterTask(objective, failure);
                    if (!options.isContinueOnMemberError()) {
                        throw new IllegalStateException("team member failed: " + failure.getMemberId() + " -> " + failure.getError());
                    }
                    continue;
                }

                if (!board.claimTask(state.getTaskId(), member.id)) {
                    continue;
                }
                prepared.add(new PreparedDispatch(state.getTaskId(), state.getTask(), member));
            }

            if (prepared.isEmpty()) {
                continue;
            }

            List<AgentTeamMemberResult> roundResults = executeRound(objective, prepared, board);
            for (AgentTeamMemberResult result : roundResults) {
                results.add(result);
                if (!result.isSuccess() && !options.isContinueOnMemberError()) {
                    throw new IllegalStateException("team member failed: " + result.getMemberId() + " -> " + result.getError());
                }
            }
        }

        return new DispatchOutcome(results, rounds);
    }

    private List<AgentTeamMemberResult> executeRound(String objective,
                                                     List<PreparedDispatch> dispatches,
                                                     AgentTeamTaskBoard board) throws Exception {
        if (dispatches.size() <= 1 || !options.isParallelDispatch()) {
            List<AgentTeamMemberResult> results = new ArrayList<>();
            for (PreparedDispatch dispatch : dispatches) {
                results.add(executePreparedTask(objective, dispatch, board));
            }
            return results;
        }

        int configured = options.getMaxConcurrency() <= 0 ? dispatches.size() : options.getMaxConcurrency();
        int threads = Math.max(1, Math.min(configured, dispatches.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<AgentTeamMemberResult>> futures = new ArrayList<>();
            for (PreparedDispatch dispatch : dispatches) {
                futures.add(executor.submit(new Callable<AgentTeamMemberResult>() {
                    @Override
                    public AgentTeamMemberResult call() throws Exception {
                        return executePreparedTask(objective, dispatch, board);
                    }
                }));
            }

            List<AgentTeamMemberResult> results = new ArrayList<>();
            for (Future<AgentTeamMemberResult> future : futures) {
                results.add(waitForFuture(future));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private AgentTeamMemberResult waitForFuture(Future<AgentTeamMemberResult> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private AgentTeamMemberResult executePreparedTask(String objective,
                                                      PreparedDispatch dispatch,
                                                      AgentTeamTaskBoard board) {
        long start = System.currentTimeMillis();
        AgentTeamMember memberView = dispatch.member.toPublicMember();

        fireBeforeTask(objective, dispatch.task, memberView);
        publishMessage(SYSTEM_MEMBER, dispatch.member.id, "task.assigned", dispatch.taskId,
                safe(dispatch.task == null ? null : dispatch.task.getTask()));

        try {
            String input = buildDispatchInput(objective, dispatch.member, dispatch.task);
            AgentResult result = runMemberTask(dispatch, input);
            String output = result == null ? "" : result.getOutputText();
            long duration = System.currentTimeMillis() - start;

            board.markCompleted(dispatch.taskId, output, duration);
            publishMessage(dispatch.member.id, LEAD_MEMBER, "task.result", dispatch.taskId, safeShort(output));

            AgentTeamMemberResult memberResult = AgentTeamMemberResult.builder()
                    .taskId(dispatch.taskId)
                    .memberId(dispatch.member.id)
                    .memberName(dispatch.member.name)
                    .task(dispatch.task)
                    .taskStatus(AgentTeamTaskStatus.COMPLETED)
                    .output(output)
                    .rawResult(result)
                    .durationMillis(duration)
                    .build();
            fireAfterTask(objective, memberResult);
            return memberResult;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            board.markFailed(dispatch.taskId, e.getMessage(), duration);
            publishMessage(dispatch.member.id, LEAD_MEMBER, "task.error", dispatch.taskId, safe(e.getMessage()));

            AgentTeamMemberResult memberResult = AgentTeamMemberResult.builder()
                    .taskId(dispatch.taskId)
                    .memberId(dispatch.member.id)
                    .memberName(dispatch.member.name)
                    .task(dispatch.task)
                    .taskStatus(AgentTeamTaskStatus.FAILED)
                    .error(e.getMessage())
                    .durationMillis(duration)
                    .build();
            fireAfterTask(objective, memberResult);
            return memberResult;
        }
    }

    private AgentResult runMemberTask(PreparedDispatch dispatch, String input) throws Exception {
        AgentSession session = dispatch.member.agent.newSession();
        if (session == null) {
            throw new IllegalStateException("failed to create team member session");
        }

        AgentContext sessionContext = session.getContext();
        if (sessionContext != null && options.isEnableMemberTeamTools()) {
            AgentToolRegistry originalRegistry = sessionContext.getToolRegistry();
            ToolExecutor originalExecutor = sessionContext.getToolExecutor();

            AgentToolRegistry mergedRegistry;
            if (originalRegistry == null) {
                mergedRegistry = teamToolRegistry;
            } else {
                mergedRegistry = new CompositeToolRegistry(originalRegistry, teamToolRegistry);
            }

            sessionContext.setToolRegistry(mergedRegistry);
            sessionContext.setToolExecutor(new AgentTeamToolExecutor(this, dispatch.member.id, dispatch.taskId, originalExecutor));
        }

        return session.run(AgentRequest.builder().input(input).build());
    }

    private String buildDispatchInput(String objective, RuntimeMember member, AgentTeamTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Team member role: ").append(member.name).append("\n");
        if (member.description != null && !member.description.trim().isEmpty()) {
            sb.append("Expertise: ").append(member.description).append("\n");
        }
        if (task != null && task.getId() != null) {
            sb.append("Task ID: ").append(task.getId()).append("\n");
        }
        if (task != null && task.getDependsOn() != null && !task.getDependsOn().isEmpty()) {
            sb.append("Dependencies: ").append(task.getDependsOn()).append("\n");
        }
        if (options.isIncludeOriginalObjectiveInDispatch()) {
            sb.append("Objective:\n").append(objective == null ? "" : objective).append("\n\n");
        }
        sb.append("Assigned task:\n").append(task == null ? "" : safe(task.getTask())).append("\n");
        if (options.isIncludeTaskContextInDispatch()) {
            String context = task == null ? null : safe(task.getContext());
            if (context != null && !context.trim().isEmpty()) {
                sb.append("Additional context:\n").append(context).append("\n");
            }
        }

        if (options.isEnableMessageBus() && options.isIncludeMessageHistoryInDispatch()) {
            List<AgentTeamMessage> history = messageBus.historyFor(member.id, options.getMessageHistoryLimit());
            if (history != null && !history.isEmpty()) {
                sb.append("Recent team messages:\n");
                for (AgentTeamMessage message : history) {
                    sb.append("- [").append(safe(message.getType())).append("] ")
                            .append(safe(message.getFromMemberId())).append(" -> ")
                            .append(safe(message.getToMemberId())).append(": ")
                            .append(safeShort(message.getContent())).append("\n");
                }
            }
        }

        sb.append("Return concise, high-signal output for the team lead.");
        return sb.toString();
    }

    private void publishMessage(String from,
                                String to,
                                String type,
                                String taskId,
                                String content) {
        if (!options.isEnableMessageBus()) {
            return;
        }
        AgentTeamMessage message = AgentTeamMessage.builder()
                .id(UUID.randomUUID().toString())
                .fromMemberId(from)
                .toMemberId(to)
                .type(type)
                .taskId(taskId)
                .content(content)
                .createdAt(System.currentTimeMillis())
                .build();
        publishMessageInternal(message);
    }

    private void publishMessageInternal(AgentTeamMessage message) {
        if (!options.isEnableMessageBus() || message == null) {
            return;
        }

        AgentTeamMessage safeMessage = message;
        if (safeMessage.getId() == null || safeMessage.getId().trim().isEmpty() || safeMessage.getCreatedAt() <= 0L) {
            safeMessage = safeMessage.toBuilder()
                    .id(safeMessage.getId() == null || safeMessage.getId().trim().isEmpty() ? UUID.randomUUID().toString() : safeMessage.getId())
                    .createdAt(safeMessage.getCreatedAt() <= 0L ? System.currentTimeMillis() : safeMessage.getCreatedAt())
                    .build();
        }

        messageBus.publish(safeMessage);
        persistState();
        for (AgentTeamHook hook : hooks) {
            try {
                hook.onMessage(safeMessage);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
    }

    private RuntimeMember resolveMember(String requestedId) {
        synchronized (memberLock) {
            if (orderedMembers.isEmpty()) {
                throw new IllegalStateException("no team member available");
            }
            if (requestedId == null || requestedId.trim().isEmpty()) {
                return orderedMembers.get(0);
            }
            String normalized = normalize(requestedId);
            RuntimeMember member = membersById.get(normalized);
            if (member != null) {
                return member;
            }
            if (options.isFailOnUnknownMember()) {
                throw new IllegalStateException("unknown team member: " + requestedId);
            }
            return orderedMembers.get(0);
        }
    }

    private List<AgentTeamMember> snapshotMembers() {
        synchronized (memberLock) {
            List<AgentTeamMember> snapshot = new ArrayList<>();
            for (RuntimeMember member : orderedMembers) {
                snapshot.add(member.toPublicMember());
            }
            return snapshot;
        }
    }

    private AgentTeamTaskBoard currentBoard() {
        synchronized (runtimeLock) {
            return activeBoard;
        }
    }

    private List<AgentTeamMemberSnapshot> snapshotMemberViews() {
        synchronized (memberLock) {
            List<AgentTeamMemberSnapshot> snapshot = new ArrayList<AgentTeamMemberSnapshot>();
            for (RuntimeMember member : orderedMembers) {
                snapshot.add(AgentTeamMemberSnapshot.from(member.toPublicMember()));
            }
            return snapshot;
        }
    }

    private void rememberTaskStates(List<AgentTeamTaskState> taskStates) {
        synchronized (runtimeLock) {
            if (taskStates == null || taskStates.isEmpty()) {
                lastTaskStates = Collections.emptyList();
            } else {
                lastTaskStates = new ArrayList<>(taskStates);
            }
        }
        persistState();
    }

    private String currentObjective() {
        synchronized (runtimeLock) {
            return activeObjective;
        }
    }

    private AgentTeamMessageBus resolveMessageBus(AgentTeamBuilder builder) {
        if (builder.getMessageBus() != null) {
            return builder.getMessageBus();
        }
        if (builder.getStorageDirectory() != null) {
            return new FileAgentTeamMessageBus(
                    builder.getStorageDirectory()
                            .resolve("mailbox")
                            .resolve(teamId + ".jsonl")
            );
        }
        return new InMemoryAgentTeamMessageBus();
    }

    private AgentTeamStateStore resolveStateStore(AgentTeamBuilder builder) {
        if (builder.getStateStore() != null) {
            return builder.getStateStore();
        }
        if (builder.getStorageDirectory() != null) {
            return new FileAgentTeamStateStore(builder.getStorageDirectory().resolve("state"));
        }
        return null;
    }

    private void persistState() {
        if (stateStore == null) {
            return;
        }
        stateStore.save(snapshotState());
    }

    private boolean isSameTeam(AgentTeamState state) {
        return state != null && state.getTeamId() != null && state.getTeamId().equals(teamId);
    }

    private List<AgentTeamTaskState> copyTaskStates(List<AgentTeamTaskState> taskStates) {
        if (taskStates == null || taskStates.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTaskState> copy = new ArrayList<AgentTeamTaskState>(taskStates.size());
        for (AgentTeamTaskState state : taskStates) {
            copy.add(state == null ? null : state.toBuilder().build());
        }
        return copy;
    }

    private List<AgentTeamMessage> copyMessages(List<AgentTeamMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamMessage> copy = new ArrayList<AgentTeamMessage>(messages.size());
        for (AgentTeamMessage message : messages) {
            copy.add(message == null ? null : message.toBuilder().build());
        }
        return copy;
    }

    private AgentTeamMember resolveMemberView(String memberId) {
        if (memberId == null || memberId.trim().isEmpty()) {
            return null;
        }
        synchronized (memberLock) {
            RuntimeMember member = membersById.get(memberId);
            return member == null ? null : member.toPublicMember();
        }
    }

    private void fireTaskStateChanged(AgentTeamTaskState state, AgentTeamMember member, String detail) {
        for (AgentTeamHook hook : hooks) {
            try {
                hook.onTaskStateChanged(currentObjective(), state, member, detail);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
    }

    private void validateKnownMemberId(String memberId, boolean allowReserved, String fieldName) {
        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (allowReserved && isReservedMember(memberId)) {
            return;
        }
        synchronized (memberLock) {
            if (!membersById.containsKey(memberId)) {
                throw new IllegalArgumentException("unknown team member: " + memberId);
            }
        }
    }

    private boolean isReservedMember(String memberId) {
        return SYSTEM_MEMBER.equals(memberId) || LEAD_MEMBER.equals(memberId);
    }


    private void fireBeforePlan(String objective, List<AgentTeamMember> members) {
        for (AgentTeamHook hook : hooks) {
            try {
                hook.beforePlan(objective, members, options);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
    }

    private void fireAfterPlan(String objective, AgentTeamPlan plan) {
        for (AgentTeamHook hook : hooks) {
            try {
                hook.afterPlan(objective, plan);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
    }

    private void fireBeforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
        for (AgentTeamHook hook : hooks) {
            try {
                hook.beforeTask(objective, task, member);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
    }

    private void fireAfterTask(String objective, AgentTeamMemberResult result) {
        for (AgentTeamHook hook : hooks) {
            try {
                hook.afterTask(objective, result);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
    }

    private void fireAfterSynthesis(String objective, AgentResult synthesis) {
        for (AgentTeamHook hook : hooks) {
            try {
                hook.afterSynthesis(objective, synthesis);
            } catch (Exception ignored) {
                // hook failures must not break dispatch
            }
        }
        publishMessage(SYSTEM_MEMBER, LEAD_MEMBER, "run.complete", null,
                synthesis == null ? "" : safeShort(synthesis.getOutputText()));
    }

    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replaceAll("[^a-z0-9_\\-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String safeShort(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= 240) {
            return text;
        }
        return text.substring(0, 240) + "...";
    }

    private String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    private static class RuntimeMember {
        private final String id;
        private final String name;
        private final String description;
        private final Agent agent;

        private RuntimeMember(String id, String name, String description, Agent agent) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.agent = agent;
        }

        private static RuntimeMember from(AgentTeamMember member) {
            if (member == null) {
                throw new IllegalArgumentException("team member cannot be null");
            }
            if (member.getAgent() == null) {
                throw new IllegalArgumentException("team member agent is required");
            }
            String id = member.resolveId();
            String name = member.getName();
            if (name == null || name.trim().isEmpty()) {
                name = id;
            }
            return new RuntimeMember(id, name, member.getDescription(), member.getAgent());
        }

        private AgentTeamMember toPublicMember() {
            return AgentTeamMember.builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .agent(agent)
                    .build();
        }
    }

    private static class PreparedDispatch {
        private final String taskId;
        private final AgentTeamTask task;
        private final RuntimeMember member;

        private PreparedDispatch(String taskId, AgentTeamTask task, RuntimeMember member) {
            this.taskId = taskId;
            this.task = task;
            this.member = member;
        }
    }

    private static class DispatchOutcome {
        private final List<AgentTeamMemberResult> results;
        private final int rounds;

        private DispatchOutcome(List<AgentTeamMemberResult> results, int rounds) {
            this.results = results;
            this.rounds = rounds;
        }
    }
}
