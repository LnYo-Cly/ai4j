package io.github.lnyocly.ai4j.ambient.heartbeat;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.ambient.memory.TrustLevel;
import io.github.lnyocly.ai4j.harness.AgentHarness;
import io.github.lnyocly.ai4j.harness.FileHarnessConfig;
import io.github.lnyocly.ai4j.harness.FileHarnessStore;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.testing.ScriptedModelClient;
import io.github.lnyocly.ai4j.testing.ScriptedSystemOneService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AmbientHeartbeatTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static SystemOneAnswer choice(String value, double confidence) {
        SystemOneAnswer answer = new SystemOneAnswer();
        answer.setType("choice");
        answer.setChoice(value);
        answer.setConfidence(confidence);
        return answer;
    }

    private static SystemOneAnswer noul(double probability) {
        SystemOneAnswer answer = new SystemOneAnswer();
        answer.setType("noul");
        answer.setNoul(probability);
        return answer;
    }

    @Test
    public void twoTierHeartbeatFiltersIdleAndRoutineTicksAtTier1BeforeWakingTier2() {
        ScriptedSystemOneService systemOne = new ScriptedSystemOneService()
                // Tick 2: non-empty delta, SystemOne classifies as IGNORE
                .enqueueAnswers(answers(choice("IGNORE", 0.96d), noul(0.05d)))
                // Tick 3: routine email digest
                .enqueueAnswers(answers(choice("DIGEST", 0.88d), noul(0.20d)))
                // Tick 4: actionable CI failure -> WAKE_MAIN_AGENT
                .enqueueAnswers(answers(choice("WAKE_MAIN_AGENT", 0.92d), noul(0.75d)));

        ScriptedModelClient mainModel = new ScriptedModelClient()
                .enqueueText("Diagnosed flaky integration step in PR #42.")
                .failWhenExhausted(true);
        Agent mainAgent = Agents.react().modelClient(mainModel).model("main-model").build();

        SupervisoryAgenda agenda = new SupervisoryAgenda();
        Clock daytime = Clock.fixed(Instant.parse("2026-11-23T14:00:00Z"), ZoneId.of("UTC"));
        AgenticHeartbeat heartbeat = new AgenticHeartbeat(
                new SystemOneSentinel(systemOne),
                AgenticHeartbeat.fromAgent(mainAgent),
                agenda,
                QuietHoursPolicy.ofHours(23, 8, ZoneId.of("UTC")),
                daytime
        );

        List<AgenticHeartbeat.HeartbeatResult> results = heartbeat.tickAll(Arrays.asList(
                // Tick 1: zero-delta idle tick -> filtered with ZERO SystemOne calls
                HeartbeatTick.builder().tickId("t1").source("github").cursorBefore("10").cursorAfter("10").deltaCount(0).build(),
                // Tick 2: low-value bot comment -> filtered by SystemOne
                HeartbeatTick.builder().tickId("t2").source("github").cursorBefore("10").cursorAfter("11").deltaCount(1).summary("dependabot bump").build(),
                // Tick 3: informational summary -> queued to agenda digest, main agent NOT invoked
                HeartbeatTick.builder().tickId("t3").source("email").cursorBefore("1").cursorAfter("2").deltaCount(1).summary("Weekly newsletter").build(),
                // Tick 4: CI build failure -> Tier-2 main agent woken once
                HeartbeatTick.builder().tickId("t4").source("ci").cursorBefore("50").cursorAfter("51").deltaCount(1).summary("Main branch CI red").build()
        ));

        assertEquals(AgenticHeartbeat.OutcomeType.SILENT_NOOP, results.get(0).getOutcomeType());
        assertFalse(results.get(0).getAssessment().isUsedSystemOne());
        assertFalse(results.get(0).isMainAgentInvoked());

        assertEquals(AgenticHeartbeat.OutcomeType.SILENT_NOOP, results.get(1).getOutcomeType());
        assertTrue(results.get(1).getAssessment().isUsedSystemOne());
        assertFalse(results.get(1).isMainAgentInvoked());

        assertEquals(AgenticHeartbeat.OutcomeType.QUEUED_DIGEST, results.get(2).getOutcomeType());
        assertFalse(results.get(2).isMainAgentInvoked());

        assertEquals(AgenticHeartbeat.OutcomeType.MAIN_AGENT_EXECUTED, results.get(3).getOutcomeType());
        assertTrue(results.get(3).isMainAgentInvoked());
        assertEquals("Diagnosed flaky integration step in PR #42.", results.get(3).getWakeupOutcome().getOutputText());

        // Only 3 calls hit SystemOne (t1 skipped at zero cost) and only 1 hit Tier-2 main agent
        assertEquals(3, systemOne.getRequests().size());
        assertEquals(1, mainModel.getInvocationCount());
        SystemOneSentinel.SentinelMetrics metrics = heartbeat.getSentinel().getMetrics();
        assertEquals(4L, metrics.getTotalTicks());
        assertEquals(1L, metrics.getZeroCostFilteredCount());
        assertEquals(2L, metrics.getIgnoredCount());
        assertEquals(0.5d, metrics.getFilterRatio(), 0.001d);
    }

    @Test
    public void quietHoursDefersNonCriticalItemsToDigestAndAllowsCriticalBypassAndFlush() throws Exception {
        ScriptedSystemOneService systemOne = new ScriptedSystemOneService()
                // Non-critical wakeup during quiet hours
                .enqueueAnswers(answers(choice("WAKE_MAIN_AGENT", 0.85d), noul(0.45d)))
                // Critical security escalation during quiet hours
                .enqueueAnswers(answers(choice("ESCALATE_OWNER", 0.97d), noul(0.95d)));

        Path harnessDir = temporaryFolder.newFolder("heartbeat-harness").toPath();
        ScriptedModelClient mainModel = new ScriptedModelClient()
                .enqueueText("Critical prod alert requires owner decision.")
                .failWhenExhausted(true);
        Agent mainAgent = Agents.react().modelClient(mainModel).model("main-model").build();

        SupervisoryAgenda agenda = new SupervisoryAgenda();
        QuietHoursPolicy quietHours = QuietHoursPolicy.ofHours(23, 8, ZoneId.of("UTC"));
        Instant nightTime = Instant.parse("2026-11-23T02:30:00Z");
        Clock nightClock = Clock.fixed(nightTime, ZoneId.of("UTC"));

        try (FileHarnessStore store = new FileHarnessStore(FileHarnessConfig.builder().directory(harnessDir).build());
             AgentHarness harness = AgentHarness.builder().agent(mainAgent).store(store).build()) {

            AgenticHeartbeat heartbeat = new AgenticHeartbeat(
                    new SystemOneSentinel(systemOne),
                    AgenticHeartbeat.fromHarness(harness),
                    agenda,
                    quietHours,
                    nightClock
            );

            // Non-critical tick during quiet hours -> deferred to digest without waking main agent
            AgenticHeartbeat.HeartbeatResult deferred = heartbeat.tick(HeartbeatTick.builder()
                    .tickId("night-1")
                    .scopeKey("ops")
                    .source("calendar")
                    .cursorBefore("1")
                    .cursorAfter("2")
                    .deltaCount(1)
                    .summary("Tomorrow 10am meeting rescheduled")
                    .trustLevel(TrustLevel.VERIFIED_TOOL)
                    .build());

            assertEquals(AgenticHeartbeat.OutcomeType.DEFERRED_QUIET_HOURS, deferred.getOutcomeType());
            assertTrue(deferred.isInQuietHours());
            assertFalse(deferred.isMainAgentInvoked());
            assertEquals(1, agenda.pendingDigest("ops").size());
            assertEquals(0, agenda.awaitingRuling("ops").size());

            // Critical alert during quiet hours -> bypasses quiet hours, invokes Harness, and enters awaitingRuling
            AgenticHeartbeat.HeartbeatResult critical = heartbeat.tick(HeartbeatTick.builder()
                    .tickId("night-crit")
                    .scopeKey("ops")
                    .source("server-alert")
                    .cursorBefore("10")
                    .cursorAfter("11")
                    .deltaCount(1)
                    .urgentHint(true)
                    .summary("Production database disk 98% full")
                    .build());

            assertEquals(AgenticHeartbeat.OutcomeType.ESCALATED_IMMEDIATE, critical.getOutcomeType());
            assertTrue(critical.isMainAgentInvoked());
            assertNotNull(critical.getWakeupOutcome().getExecutionId());
            assertEquals(1, agenda.awaitingRuling("ops").size());

            // Morning flush moves deferred digest items into awaitingRuling
            Instant morning = Instant.parse("2026-11-23T08:05:00Z");
            assertFalse(quietHours.isQuietAt(morning));
            SupervisoryAgenda.AgendaDigest digest = agenda.flushDigest("ops", morning);
            assertEquals(1, digest.getItems().size());
            assertTrue(agenda.pendingDigest("ops").isEmpty());
            assertEquals(2, agenda.awaitingRuling("ops").size());

            // Owner answers one item and completes follow-up
            String rulingId = critical.getAgendaItem().getItemId();
            SupervisoryAgenda.AgendaItem answered = agenda.recordAnswer(
                    rulingId, "expand-volume", "Approved 500GB expansion", "owner-1", morning);
            assertEquals(SupervisoryAgenda.Status.ANSWERED_PENDING_FOLLOWUP, answered.getStatus());
            assertEquals(1, agenda.answeredPendingFollowUp("ops").size());

            SupervisoryAgenda.AgendaItem resolved = agenda.markFollowUpCompleted(rulingId);
            assertEquals(SupervisoryAgenda.Status.RESOLVED, resolved.getStatus());
            assertTrue(agenda.answeredPendingFollowUp("ops").isEmpty());
        }
    }

    private static Map<String, SystemOneAnswer> answers(SystemOneAnswer triage, SystemOneAnswer urgent) {
        Map<String, SystemOneAnswer> map = new LinkedHashMap<String, SystemOneAnswer>();
        map.put(SystemOneSentinel.QUESTION_TRIAGE, triage);
        map.put(SystemOneSentinel.QUESTION_URGENT, urgent);
        return map;
    }
}
