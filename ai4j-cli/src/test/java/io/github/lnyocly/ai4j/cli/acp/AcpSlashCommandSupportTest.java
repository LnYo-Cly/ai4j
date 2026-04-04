package io.github.lnyocly.ai4j.cli.acp;

import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberSnapshot;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamMessageBus;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamStateStore;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.FileCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.FileSessionEventStore;
import io.github.lnyocly.ai4j.cli.session.StoredCodingSession;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionState;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcpSlashCommandSupportTest {

    @Test
    public void availableCommandsShouldIncludeTeam() {
        Assert.assertTrue(containsCommand("team"));
        Assert.assertTrue(containsCommand("providers"));
        Assert.assertTrue(containsCommand("provider"));
        Assert.assertTrue(containsCommand("model"));
        Assert.assertTrue(containsCommand("experimental"));
    }

    @Test
    public void executeExperimentalShouldDelegateToRuntimeHandler() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-experimental-command");
        String sessionId = "experimental-command-session";
        DefaultCodingSessionManager sessionManager = seedTeamHistory(workspace, sessionId);

        ManagedCodingSession session = new ManagedCodingSession(
                new CodingSession(sessionId, null, null, null, null),
                "openai",
                "responses",
                "fake-model",
                workspace.toString(),
                null,
                null,
                null,
                sessionId,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        AcpSlashCommandSupport.ExecutionResult result = AcpSlashCommandSupport.execute(
                new AcpSlashCommandSupport.Context(
                        session,
                        sessionManager,
                        null,
                        null,
                        null,
                        new AcpSlashCommandSupport.RuntimeCommandHandler() {
                            @Override
                            public String executeProviders() {
                                return "providers";
                            }

                            @Override
                            public String executeProvider(String argument) {
                                return "provider " + argument;
                            }

                            @Override
                            public String executeModel(String argument) {
                                return "model " + argument;
                            }

                            @Override
                            public String executeExperimental(String argument) {
                                return "experimental " + argument;
                            }
                        }
                ),
                "/experimental subagent off"
        );

        Assert.assertNotNull(result);
        Assert.assertEquals("experimental subagent off", result.getOutput());
    }

    @Test
    public void executeTeamShouldRenderMemberLaneBoard() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-team-command");
        String sessionId = "team-command-session";
        DefaultCodingSessionManager sessionManager = seedTeamHistory(workspace, sessionId);

        ManagedCodingSession session = new ManagedCodingSession(
                new CodingSession(sessionId, null, null, null, null),
                "openai",
                "responses",
                "fake-model",
                workspace.toString(),
                null,
                null,
                null,
                sessionId,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        AcpSlashCommandSupport.ExecutionResult result = AcpSlashCommandSupport.execute(
                new AcpSlashCommandSupport.Context(session, sessionManager, null, null, null),
                "/team"
        );

        Assert.assertNotNull(result);
        Assert.assertTrue(result.getOutput().contains("team board:"));
        Assert.assertTrue(result.getOutput().contains("lane Reviewer"));
        Assert.assertTrue(result.getOutput().contains("Review this patch"));
        Assert.assertTrue(result.getOutput().contains("[task.assigned] system -> reviewer"));
    }

    @Test
    public void executeTeamSubcommandsShouldRenderPersistedState() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-team-persisted");
        String sessionId = "team-persisted-session";
        DefaultCodingSessionManager sessionManager = seedTeamHistory(workspace, sessionId);
        seedPersistedTeamState(workspace, "experimental-delivery-team");

        ManagedCodingSession session = new ManagedCodingSession(
                new CodingSession(sessionId, null, null, null, null),
                "openai",
                "responses",
                "fake-model",
                workspace.toString(),
                null,
                null,
                null,
                sessionId,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        AcpSlashCommandSupport.ExecutionResult listResult = AcpSlashCommandSupport.execute(
                new AcpSlashCommandSupport.Context(session, sessionManager, null, null, null),
                "/team list"
        );
        AcpSlashCommandSupport.ExecutionResult statusResult = AcpSlashCommandSupport.execute(
                new AcpSlashCommandSupport.Context(session, sessionManager, null, null, null),
                "/team status experimental-delivery-team"
        );
        AcpSlashCommandSupport.ExecutionResult messageResult = AcpSlashCommandSupport.execute(
                new AcpSlashCommandSupport.Context(session, sessionManager, null, null, null),
                "/team messages experimental-delivery-team 5"
        );
        AcpSlashCommandSupport.ExecutionResult resumeResult = AcpSlashCommandSupport.execute(
                new AcpSlashCommandSupport.Context(session, sessionManager, null, null, null),
                "/team resume experimental-delivery-team"
        );

        Assert.assertNotNull(listResult);
        Assert.assertTrue(listResult.getOutput().contains("teams:"));
        Assert.assertTrue(listResult.getOutput().contains("experimental-delivery-team"));

        Assert.assertNotNull(statusResult);
        Assert.assertTrue(statusResult.getOutput().contains("team status:"));
        Assert.assertTrue(statusResult.getOutput().contains("objective=Deliver a travel planner demo."));

        Assert.assertNotNull(messageResult);
        Assert.assertTrue(messageResult.getOutput().contains("team messages:"));
        Assert.assertTrue(messageResult.getOutput().contains("Define the backend contract first."));

        Assert.assertNotNull(resumeResult);
        Assert.assertTrue(resumeResult.getOutput().contains("team resumed: experimental-delivery-team"));
        Assert.assertTrue(resumeResult.getOutput().contains("team board:"));
    }

    private DefaultCodingSessionManager seedTeamHistory(Path workspace, String sessionId) throws Exception {
        Path sessionDirectory = workspace.resolve(".ai4j").resolve("sessions");
        long now = System.currentTimeMillis();
        new FileCodingSessionStore(sessionDirectory).save(StoredCodingSession.builder()
                .sessionId(sessionId)
                .rootSessionId(sessionId)
                .provider("openai")
                .protocol("responses")
                .model("fake-model")
                .workspace(workspace.toString())
                .summary("team history")
                .createdAtEpochMs(now)
                .updatedAtEpochMs(now)
                .state(CodingSessionState.builder()
                        .sessionId(sessionId)
                        .workspaceRoot(workspace.toString())
                        .build())
                .build());

        DefaultCodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new FileCodingSessionStore(sessionDirectory),
                new FileSessionEventStore(sessionDirectory.resolve("events"))
        );

        Map<String, Object> createdPayload = new HashMap<String, Object>();
        createdPayload.put("taskId", "review");
        createdPayload.put("callId", "team-task:review");
        createdPayload.put("title", "Team task review");
        createdPayload.put("task", "Review this patch");
        createdPayload.put("status", "pending");
        createdPayload.put("phase", "planned");
        createdPayload.put("percent", Integer.valueOf(0));
        createdPayload.put("memberId", "reviewer");
        createdPayload.put("memberName", "Reviewer");

        Map<String, Object> updatedPayload = new HashMap<String, Object>(createdPayload);
        updatedPayload.put("status", "running");
        updatedPayload.put("phase", "heartbeat");
        updatedPayload.put("percent", Integer.valueOf(15));
        updatedPayload.put("detail", "Heartbeat from reviewer.");
        updatedPayload.put("heartbeatCount", Integer.valueOf(2));
        updatedPayload.put("updatedAtEpochMs", Long.valueOf(now + 1L));

        Map<String, Object> messagePayload = new HashMap<String, Object>();
        messagePayload.put("messageId", "msg-1");
        messagePayload.put("taskId", "review");
        messagePayload.put("fromMemberId", "system");
        messagePayload.put("toMemberId", "reviewer");
        messagePayload.put("messageType", "task.assigned");
        messagePayload.put("content", "Review this patch");
        messagePayload.put("createdAt", Long.valueOf(now + 2L));

        sessionManager.appendEvent(sessionId, SessionEvent.builder()
                .sessionId(sessionId)
                .type(SessionEventType.TASK_CREATED)
                .timestamp(now)
                .summary("Team task review [pending]")
                .payload(createdPayload)
                .build());
        sessionManager.appendEvent(sessionId, SessionEvent.builder()
                .sessionId(sessionId)
                .type(SessionEventType.TASK_UPDATED)
                .timestamp(now + 1L)
                .summary("Team task review [running]")
                .payload(updatedPayload)
                .build());
        sessionManager.appendEvent(sessionId, SessionEvent.builder()
                .sessionId(sessionId)
                .type(SessionEventType.TEAM_MESSAGE)
                .timestamp(now + 2L)
                .summary("Team message system -> reviewer [task.assigned]")
                .payload(messagePayload)
                .build());
        return sessionManager;
    }

    private void seedPersistedTeamState(Path workspace, String teamId) {
        Path teamRoot = workspace.resolve(".ai4j").resolve("teams");
        long now = System.currentTimeMillis();
        AgentTeamTask backendTask = AgentTeamTask.builder()
                .id("backend")
                .memberId("backend")
                .task("Define travel destination API")
                .build();
        AgentTeamTaskState backendState = AgentTeamTaskState.builder()
                .taskId("backend")
                .task(backendTask)
                .status(AgentTeamTaskStatus.IN_PROGRESS)
                .claimedBy("backend")
                .phase("running")
                .detail("Drafting OpenAPI paths and payloads.")
                .percent(Integer.valueOf(40))
                .heartbeatCount(2)
                .updatedAtEpochMs(now)
                .build();
        AgentTeamMessage message = AgentTeamMessage.builder()
                .id("msg-1")
                .fromMemberId("architect")
                .toMemberId("backend")
                .type("contract.note")
                .taskId("backend")
                .content("Define the backend contract first.")
                .createdAt(now)
                .build();
        AgentTeamState state = AgentTeamState.builder()
                .teamId(teamId)
                .objective("Deliver a travel planner demo.")
                .members(java.util.Arrays.asList(
                        AgentTeamMemberSnapshot.builder().id("architect").name("Architect").description("System design").build(),
                        AgentTeamMemberSnapshot.builder().id("backend").name("Backend").description("API implementation").build()
                ))
                .taskStates(java.util.Collections.singletonList(backendState))
                .messages(java.util.Collections.singletonList(message))
                .lastOutput("Architecture and backend work are in progress.")
                .lastRounds(2)
                .lastRunStartedAt(now - 3000L)
                .updatedAt(now)
                .runActive(true)
                .build();
        new FileAgentTeamStateStore(teamRoot.resolve("state")).save(state);
        new FileAgentTeamMessageBus(teamRoot.resolve("mailbox").resolve(teamId + ".jsonl"))
                .restore(java.util.Collections.singletonList(message));
    }

    private boolean containsCommand(String name) {
        List<Map<String, Object>> commands = AcpSlashCommandSupport.availableCommands();
        if (commands == null) {
            return false;
        }
        for (Map<String, Object> command : commands) {
            if (command != null && name.equals(command.get("name"))) {
                return true;
            }
        }
        return false;
    }
}
