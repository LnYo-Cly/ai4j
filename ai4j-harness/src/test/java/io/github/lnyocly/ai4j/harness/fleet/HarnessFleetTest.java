package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentRecord;
import io.github.lnyocly.ai4j.harness.ContentPinnedConsentSpec;
import io.github.lnyocly.ai4j.harness.EntityKind;
import io.github.lnyocly.ai4j.harness.EscalationRecord;
import io.github.lnyocly.ai4j.harness.EscalationStatus;
import io.github.lnyocly.ai4j.harness.EvidenceRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.ExecutionReviewSpec;
import io.github.lnyocly.ai4j.harness.ExecutionStatus;
import io.github.lnyocly.ai4j.harness.FileHarnessConfig;
import io.github.lnyocly.ai4j.harness.FileHarnessStore;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessConflictException;
import io.github.lnyocly.ai4j.harness.HarnessContract;
import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import io.github.lnyocly.ai4j.harness.HarnessEscalateSpec;
import io.github.lnyocly.ai4j.harness.HarnessExecutionOutcome;
import io.github.lnyocly.ai4j.harness.HarnessExecutionSpec;
import io.github.lnyocly.ai4j.harness.HarnessJson;
import io.github.lnyocly.ai4j.harness.HarnessSubmissionSpec;
import io.github.lnyocly.ai4j.harness.HarnessTaskSpec;
import io.github.lnyocly.ai4j.harness.HarnessValidationException;
import io.github.lnyocly.ai4j.harness.JdbcHarnessStore;
import io.github.lnyocly.ai4j.harness.ReviewVerdict;
import io.github.lnyocly.ai4j.harness.SteerRecord;
import io.github.lnyocly.ai4j.harness.SubmissionRecord;
import io.github.lnyocly.ai4j.harness.SupervisoryAgenda;
import io.github.lnyocly.ai4j.harness.TaskRecord;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Deterministic tests for multi-node Harness Fleet:
 * all 5 topologies, InMemory and HTTP (HttpServer + OkHttpClient) transports,
 * edgeJoin, 3-way edgeSync merge & conflict detection, node affinity routing,
 * deterministic AutoClaimWorker, and forced unregisterNode epoch fencing + orphan takeover.
 * Zero Thread.sleep usage.
 */
public class HarnessFleetTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-fleet-test-");
    }

    @After
    public void tearDown() throws Exception {
        if (directory != null && Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best effort cleanup
                        }
                    });
        }
    }

    private HarnessCommandGateway centerGateway(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcHarnessStore store = new JdbcHarnessStore(ds, name);
        return new HarnessCommandGateway(store, HarnessContract.builder().build(), HarnessActor.agent("center"));
    }

    @Test
    public void httpFleetRoundtripAllFiveTopologiesProxyAndAgendaActions() throws Exception {
        HarnessCommandGateway coreGw = centerGateway("fleet_http");
        FleetGateway center = FleetGateway.center(coreGw);
        HttpFleetServer server = center.startHttpServer("127.0.0.1", 0);
        String baseUrl = server.getBaseUrl();

        HarnessCommandGateway localOnlyGw = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder().directory(directory.resolve("local")).harnessId("local").build()),
                HarnessContract.builder().build(),
                HarnessActor.agent("local"));
        FleetGateway local = FleetGateway.local(localOnlyGw);
        FleetGateway shared = FleetGateway.sharedCluster(coreGw, "shared-node",
                AutoClaimPolicy.builder().tags(Collections.singletonList("cpu")).maxConcurrentExecutions(2).build());

        FleetGateway gpuEdge = FleetGateway.edge("edge-gpu", new HttpFleetTransport(baseUrl),
                AutoClaimPolicy.builder()
                        .tags(Arrays.asList("gpu", "cuda"))
                        .allowedAgents(Collections.singletonList("gpu-coder"))
                        .allowedScopeKeys(Collections.singletonList("scope-prod"))
                        .maxConcurrentExecutions(2)
                        .build());

        FleetGateway proxy = FleetGateway.proxy("proxy-ui", new HttpFleetTransport(baseUrl));

        try {
            Assert.assertEquals(FleetTopology.LOCAL, local.getTopology());
            Assert.assertEquals(FleetTopology.SHARED_CLUSTER, shared.getTopology());
            Assert.assertEquals(FleetTopology.FLEET_CENTER, center.getTopology());
            Assert.assertEquals(FleetTopology.FLEET_EDGE, gpuEdge.getTopology());
            Assert.assertEquals(FleetTopology.FLEET_PROXY, proxy.getTopology());

            // Proxy cannot create AutoClaimWorker
            try {
                proxy.createAutoClaimWorker("bad-worker");
                Assert.fail("FLEET_PROXY must reject AutoClaimWorker");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("FLEET_PROXY"));
            }

            EdgeJoinResponse joinResp = gpuEdge.join("GPU Worker Node");
            Assert.assertEquals("edge-gpu", joinResp.getNode().getNodeId());
            Assert.assertNotNull(joinResp.getCredentialToken());

            HarnessActor agent = HarnessActor.agent("gpu-coder");
            HarnessActor owner = HarnessActor.owner("owner-fleet");

            TaskRecord matchingTask = coreGw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-gpu-1")
                    .scopeKey("scope-prod")
                    .title("Run GPU kernel bench")
                    .assignedAgentId("gpu-coder")
                    .requiredTags(Collections.singletonList("gpu"))
                    .build(), agent);
            ExecutionRecord execMatch = coreGw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-gpu-1")
                    .taskId(matchingTask.getTaskId())
                    .scopeKey("scope-prod")
                    .requiredTags(Collections.singletonList("cuda"))
                    .build());

            // Non-matching node affinity / scope / agent task
            TaskRecord cpuTask = coreGw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-cpu-1")
                    .scopeKey("scope-prod")
                    .title("CPU task")
                    .assignedAgentId("cpu-agent")
                    .requiredTags(Collections.singletonList("tpu"))
                    .build(), agent);
            coreGw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-cpu-1")
                    .taskId(cpuTask.getTaskId())
                    .scopeKey("scope-prod")
                    .build());

            // Deterministic trigger-driven AutoClaimWorker pulls and runs ONLY matching execution over HTTP
            AutoClaimWorker worker = gpuEdge.createAutoClaimWorker("gpu-worker-1");
            ExecutorService workerPool = Executors.newSingleThreadExecutor();
            CountDownLatch completedLatch = new CountDownLatch(1);
            workerPool.submit(() -> {
                try {
                    int ran = worker.awaitTriggerAndRunOnce(5L, TimeUnit.SECONDS, exe -> {
                        Assert.assertEquals("exec-gpu-1", exe.getExecutionId());
                        // Proxy steers active execution over HTTP
                        SteerRecord steered = proxy.steer(exe.getExecutionId(), matchingTask.getTaskId(),
                                exe.getVersion(), "emit json summary", owner);
                        Assert.assertNotNull(steered.getSteerId());
                        return HarnessExecutionOutcome.builder()
                                .status(ExecutionStatus.SUCCEEDED)
                                .outputText("gpu done")
                                .build();
                    });
                    Assert.assertEquals(1, ran);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completedLatch.countDown();
                }
            });
            worker.trigger();
            Assert.assertTrue(completedLatch.await(10L, TimeUnit.SECONDS));
            workerPool.shutdownNow();

            Assert.assertEquals(ExecutionStatus.SUCCEEDED, coreGw.getExecution(execMatch.getExecutionId()).getStatus());
            Assert.assertEquals(ExecutionStatus.READY, coreGw.getExecution("exec-cpu-1").getStatus());
            Assert.assertEquals(0, worker.getActiveCount());

            // Proxy escalates + fetches agenda + answers escalation + human consent over HTTP
            EscalationRecord esc = coreGw.escalate(HarnessEscalateSpec.builder()
                    .taskId(matchingTask.getTaskId())
                    .question("Approve prod rollout?")
                    .options(Arrays.asList("yes", "no"))
                    .recommend("yes")
                    .targetActorId("owner-fleet")
                    .build(), agent);
            SupervisoryAgenda proxyAgenda = proxy.fetchAgenda("owner-fleet", "scope-prod");
            Assert.assertEquals(1, proxyAgenda.getAwaitingRuling().size());

            EscalationRecord answered = proxy.answerEscalation(esc.getEscalationId(), "yes", "yes", "approved", owner);
            Assert.assertEquals(EscalationStatus.ANSWERED, answered.getStatus());

            EvidenceRecord ev = coreGw.recordEvidence(HarnessEvidenceSpecBuilder(matchingTask.getTaskId()), agent);
            SubmissionRecord sub = coreGw.submitTask(matchingTask.getTaskId(), null, HarnessSubmissionSpec.builder()
                    .completionClaim("gpu bench complete")
                    .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                    .build(), agent);
            coreGw.submitExecutionReview(ExecutionReviewSpec.builder()
                    .submissionId(sub.getSubmissionId())
                    .submissionDigest(sub.getSubmissionDigest())
                    .verdict(ReviewVerdict.APPROVED)
                    .build(), HarnessActor.agent("reviewer"));

            // Agent consent over HTTP propagates HarnessValidationException cleanly
            try {
                proxy.reviewConsent(ContentPinnedConsentSpec.builder()
                        .submissionId(sub.getSubmissionId())
                        .submissionDigest(sub.getSubmissionDigest())
                        .approved(true)
                        .build(), agent);
                Assert.fail("agent consent over HTTP must fail");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("human") || "HUMAN_CONSENT_REQUIRED".equals(expected.getErrorCode()));
            }

            ContentPinnedConsentRecord consent = proxy.reviewConsent(ContentPinnedConsentSpec.builder()
                    .submissionId(sub.getSubmissionId())
                    .submissionDigest(sub.getSubmissionDigest())
                    .approved(true)
                    .rationale("verified over proxy")
                    .build(), owner);
            Assert.assertTrue(consent.isApproved());
        } finally {
            proxy.close();
            gpuEdge.close();
            shared.close();
            local.close();
            center.close();
            localOnlyGw.close();
        }
    }

    @Test
    public void threeWayEdgeSyncMergesDisjointEditsAndDetectsSameFieldConflicts() {
        HarnessCommandGateway centerGw = centerGateway("fleet_sync");
        FleetGateway center = FleetGateway.center(centerGw);
        FleetGateway edge = FleetGateway.edge("edge-sync-1", center.getTransport(), AutoClaimPolicy.builder().build());

        try {
            HarnessActor agent = HarnessActor.agent("edge-agent");
            TaskRecord initial = centerGw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-sync-1")
                    .scopeKey("sync-scope")
                    .title("Initial Title")
                    .goal("Initial Goal")
                    .plan("Initial Plan")
                    .build(), agent);

            edge.join("Sync Edge");
            HarnessEntityRecord edgeCopy = edge.getLocalEntity(EntityKind.TASK.name(), initial.getTaskId());
            Assert.assertNotNull(edgeCopy);

            // Offline edge modifies `plan` ONLY
            Map<String, Object> edgePayload = HarnessJson.parseMap(edgeCopy.getPayloadJson());
            edgePayload.put("plan", "Edge updated detailed steps");
            edgePayload.put("version", 2L);
            edgeCopy.setEntityVersion(2L);
            edgeCopy.setPayloadJson(HarnessJson.toJson(edgePayload));
            edge.putLocalEntity(edgeCopy);

            // Center concurrently modifies `goal` ONLY (disjoint field!)
            centerGw.getStore().update(state -> {
                TaskRecord t = state.getTasks().get("task-sync-1");
                t.setGoal("Center refined goal");
                t.setVersion(t.getVersion() + 1L);
                return state;
            });

            EdgeSyncResponse cleanMerge = edge.sync(EdgeSyncConflictPolicy.DETECT_AND_REPORT);
            Assert.assertTrue(cleanMerge.isSynced());
            Assert.assertFalse(cleanMerge.isHasConflicts());
            TaskRecord afterMerge = centerGw.getTask("task-sync-1");
            Assert.assertEquals("Center refined goal", afterMerge.getGoal());
            Assert.assertEquals("Edge updated detailed steps", afterMerge.getPlan());

            // Now both sides modify the SAME field `title` -> DETECT_AND_REPORT emits conflict
            HarnessEntityRecord edgeConflictCopy = edge.getLocalEntity(EntityKind.TASK.name(), "task-sync-1");
            Map<String, Object> localConflictMap = HarnessJson.parseMap(edgeConflictCopy.getPayloadJson());
            localConflictMap.put("title", "Edge conflicting title");
            edgeConflictCopy.setEntityVersion(edgeConflictCopy.getEntityVersion() + 1L);
            edgeConflictCopy.setPayloadJson(HarnessJson.toJson(localConflictMap));
            edge.putLocalEntity(edgeConflictCopy);

            centerGw.getStore().update(state -> {
                TaskRecord t = state.getTasks().get("task-sync-1");
                t.setTitle("Center conflicting title");
                t.setVersion(t.getVersion() + 1L);
                return state;
            });

            EdgeSyncResponse conflicted = edge.sync(EdgeSyncConflictPolicy.DETECT_AND_REPORT);
            Assert.assertFalse(conflicted.isSynced());
            Assert.assertTrue(conflicted.isHasConflicts());
            Assert.assertFalse(conflicted.getConflicts().isEmpty());
            Assert.assertEquals("title", conflicted.getConflicts().get(0).getFieldPath());

            // Same conflict retried under LOCAL_WINS resolves cleanly in favor of edge
            EdgeSyncResponse localWins = edge.sync(EdgeSyncConflictPolicy.LOCAL_WINS);
            Assert.assertTrue(localWins.isSynced());
            Assert.assertEquals("Edge conflicting title", centerGw.getTask("task-sync-1").getTitle());
        } finally {
            edge.close();
            center.close();
        }
    }

    @Test
    public void forcedUnregisterNodeIncrementsWriterEpochAndExportsOrphansForTakeover() throws Exception {
        HarnessCommandGateway centerGw = centerGateway("fleet_failover");
        FleetGateway center = FleetGateway.center(centerGw);
        HttpFleetServer server = center.startHttpServer("127.0.0.1", 0);

        FleetGateway deadEdge = FleetGateway.edge("edge-dead", new HttpFleetTransport(server.getBaseUrl()),
                AutoClaimPolicy.builder().tags(Collections.singletonList("linux")).maxConcurrentExecutions(2).build());
        FleetGateway mismatchEdge = FleetGateway.edge("edge-win", new HttpFleetTransport(server.getBaseUrl()),
                AutoClaimPolicy.builder().tags(Collections.singletonList("windows")).maxConcurrentExecutions(2).build());
        FleetGateway healthyEdge = FleetGateway.edge("edge-healthy", new HttpFleetTransport(server.getBaseUrl()),
                AutoClaimPolicy.builder().tags(Arrays.asList("linux", "docker")).maxConcurrentExecutions(2).build());

        try {
            EdgeJoinResponse deadJoin = deadEdge.join("Dead Node");
            mismatchEdge.join("Windows Node");
            healthyEdge.join("Healthy Linux Node");

            HarnessActor owner = HarnessActor.owner("ops-admin");
            TaskRecord task = centerGw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-orphan-1")
                    .scopeKey("ops-scope")
                    .title("Long running container task")
                    .requiredTags(Collections.singletonList("linux"))
                    .build(), owner);
            ExecutionRecord exec = centerGw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-orphan-1")
                    .taskId(task.getTaskId())
                    .scopeKey("ops-scope")
                    .targetNodeId("edge-dead")
                    .requiredTags(Collections.singletonList("linux"))
                    .build());

            // Dead node claims the execution
            ExecutionRecord claimed = deadEdge.claimExecution(exec.getExecutionId(), "dead-worker-1", 60_000L);
            Assert.assertEquals(ExecutionStatus.RUNNING, claimed.getStatus());
            Assert.assertEquals("edge-dead", claimed.getAssignedNodeId());

            // Non-forced unregister MUST fail while execution is active
            try {
                center.unregisterNode("edge-dead", false, "graceful drain", owner);
                Assert.fail("non-forced unregister with active execution must throw HarnessConflictException");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(FleetCenterService.ERROR_NODE_HAS_ACTIVE_WORK, expected.getErrorCode());
            }

            // Forced unregister bumps WriterEpoch, clears node credentials, revokes leases, exports orphan
            UnregisterNodeResult unreg = center.unregisterNode("edge-dead", true, "heartbeat lost", owner);
            Assert.assertEquals(FleetNodeStatus.UNREGISTERED, unreg.getNode().getStatus());
            Assert.assertTrue(unreg.getNewWriterEpoch() > unreg.getPreviousWriterEpoch());
            Assert.assertFalse(unreg.getRevokedLeaseIds().isEmpty());
            Assert.assertEquals(1, unreg.getOrphanExports().size());
            OrphanHandoffExport export = unreg.getOrphanExports().get(0);
            Assert.assertEquals(OrphanHandoffExport.Status.PENDING_TAKEOVER, export.getStatus());
            Assert.assertEquals(unreg.getNewWriterEpoch(), export.getFencedWriterEpoch());

            // Stale fenced mutation from old epoch MUST be rejected by WriterEpoch fence!
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("title", "zombie write");
            io.github.lnyocly.ai4j.harness.MutationReceipt zombieReceipt = centerGw.executeFencedMutation(
                    "TASK", task.getTaskId(), "task.zombie",
                    null, deadJoin.getWriterEpoch(), "zombie-key", payload, HarnessActor.agent("edge-dead"));
            Assert.assertFalse(zombieReceipt.isAccepted());
            Assert.assertEquals(io.github.lnyocly.ai4j.harness.MutationReceipt.Outcome.EPOCH_FENCED,
                    zombieReceipt.getOutcome());
            Assert.assertEquals(unreg.getNewWriterEpoch(), zombieReceipt.getWriterEpoch());

            // Dead edge's sync / pull must also be rejected
            try {
                deadEdge.pullClaimableExecutions("dead-worker-1", 1, true);
                Assert.fail("unregistered dead node must not pull executions");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(FleetCenterService.ERROR_NODE_UNREGISTERED, expected.getErrorCode());
            }

            // Node lacking required capability tags cannot take over the orphan
            try {
                mismatchEdge.takeoverOrphan(export.getExportId(), "win-worker", 60_000L, owner);
                Assert.fail("capability mismatch must block orphan takeover");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(FleetCenterService.ERROR_NODE_AFFINITY_MISMATCH, expected.getErrorCode());
            }

            // Healthy edge node with matching tag takes over orphan over HTTP and completes it
            ExecutionRecord takenOver = healthyEdge.takeoverOrphan(
                    export.getExportId(), "healthy-worker-1", 60_000L, owner);
            Assert.assertEquals(ExecutionStatus.RUNNING, takenOver.getStatus());
            Assert.assertEquals("edge-healthy", takenOver.getAssignedNodeId());
            Assert.assertTrue(takenOver.getFencingToken() > claimed.getFencingToken());

            List<OrphanHandoffExport> afterExports = healthyEdge.listOrphanExports("ops-scope");
            Assert.assertEquals(1, afterExports.size());
            Assert.assertEquals(OrphanHandoffExport.Status.TAKEN_OVER, afterExports.get(0).getStatus());
            Assert.assertEquals("edge-healthy", afterExports.get(0).getTakenOverByNodeId());

            ExecutionRecord finished = healthyEdge.persistOutcome(HarnessExecutionOutcome.builder()
                    .executionId(takenOver.getExecutionId())
                    .leaseId(takenOver.getLeaseId())
                    .fencingToken(takenOver.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .outputText("recovered on healthy edge")
                    .build());
            Assert.assertEquals(ExecutionStatus.SUCCEEDED, finished.getStatus());
        } finally {
            healthyEdge.close();
            mismatchEdge.close();
            deadEdge.close();
            center.close();
        }
    }

    private static io.github.lnyocly.ai4j.harness.HarnessEvidenceSpec HarnessEvidenceSpecBuilder(String taskId) {
        return io.github.lnyocly.ai4j.harness.HarnessEvidenceSpec.builder()
                .scopeKey("scope-prod")
                .taskId(taskId)
                .kind("benchmark")
                .location("gpu-node")
                .summary("kernel bench ok")
                .build();
    }
}
