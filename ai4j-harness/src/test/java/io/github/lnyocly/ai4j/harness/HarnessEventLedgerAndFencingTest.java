package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSandboxBinding;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Verifies CQRS append-only event ledger, entity rebuilds, idempotency replay,
 * concurrent CAS version conflicts, writer-epoch split-brain fencing,
 * deterministic cursor pagination, Jackson serialization fidelity, and
 * complete removal of fastjson2 from compiled bytecode.
 */
public class HarnessEventLedgerAndFencingTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-cqrs-test-");
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
                            // Best effort cleanup of temporary test directory.
                        }
                    });
        }
    }

    @Test
    public void jdbcStoreAppendAndRebuildEntitiesFromEventsAlone() throws Exception {
        JdbcDataSource dataSource = h2("jdbc_rebuild");
        JdbcHarnessStore store = new JdbcHarnessStore(dataSource, "cqrs-jdbc");
        try {
            appendAndAssertRebuildScenario(store);

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement delete = connection.prepareStatement(
                         "DELETE FROM ai4j_harness_entities WHERE harness_id = ?")) {
                delete.setString(1, "cqrs-jdbc");
                delete.executeUpdate();
            }
            Assert.assertEquals(0, store.listEntitiesPage(null, null, null, 20).size());

            int rebuilt = store.rebuildEntitiesFromEvents();
            Assert.assertEquals(3, rebuilt);
            assertRebuiltEntities(store);
        } finally {
            store.close();
        }
    }

    @Test
    public void fileStoreAppendAndRebuildEntitiesFromEventsAlone() throws Exception {
        Path storeDir = directory.resolve("file-rebuild");
        FileHarnessStore store = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(storeDir)
                .harnessId("cqrs-file")
                .build());
        try {
            appendAndAssertRebuildScenario(store);

            // Simulate a torn trailing line after crash + deleted projection snapshot.
            Files.deleteIfExists(storeDir.resolve("entities.json"));
            Files.write(storeDir.resolve("events.jsonl"),
                    "{\"seq\":999,\"truncated".getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);

            FileHarnessStore reopened = new FileHarnessStore(FileHarnessConfig.builder()
                    .directory(storeDir)
                    .harnessId("cqrs-file")
                    .build());
            try {
                int rebuilt = reopened.rebuildEntitiesFromEvents();
                Assert.assertEquals(3, rebuilt);
                assertRebuiltEntities(reopened);
            } finally {
                reopened.close();
            }
        } finally {
            store.close();
        }
    }

    @Test
    public void idempotencyKeyReplayReturnsIdempotentReplayWithoutDuplicateEvents() {
        JdbcDataSource dataSource = h2("jdbc_idempotency");
        JdbcHarnessStore jdbcStore = new JdbcHarnessStore(dataSource, "idem-jdbc");
        FileHarnessStore fileStore = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(directory.resolve("idem-file"))
                .harnessId("idem-file")
                .build());
        try {
            assertIdempotencyReplay(jdbcStore, "idem-jdbc");
            assertIdempotencyReplay(fileStore, "idem-file");
        } finally {
            jdbcStore.close();
            fileStore.close();
        }
    }

    @Test
    public void concurrentExpectedVersionCompareAndAppendConflictsUsingLatch() throws Exception {
        JdbcDataSource dataSource = h2("jdbc_cas_race");
        JdbcHarnessStore jdbcStore = new JdbcHarnessStore(dataSource, "cas-jdbc");
        FileHarnessStore fileStore = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(directory.resolve("cas-file"))
                .harnessId("cas-file")
                .build());
        try {
            assertConcurrentVersionRace(jdbcStore);
            assertConcurrentVersionRace(fileStore);
        } finally {
            jdbcStore.close();
            fileStore.close();
        }
    }

    @Test
    public void writerEpochSplitBrainFencingRejectsStaleWriters() {
        JdbcDataSource dataSource = h2("jdbc_epoch_fencing");
        JdbcHarnessStore jdbcStore = new JdbcHarnessStore(dataSource, "epoch-jdbc");
        FileHarnessStore fileStore = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(directory.resolve("epoch-file"))
                .harnessId("epoch-file")
                .build());
        try {
            assertEpochFencing(jdbcStore);
            assertEpochFencing(fileStore);
        } finally {
            jdbcStore.close();
            fileStore.close();
        }
    }

    @Test
    public void cursorPageDeterministicPaginationAcrossStoresAndGateway() {
        JdbcDataSource dataSource = h2("jdbc_cursor_page");
        JdbcHarnessStore jdbcStore = new JdbcHarnessStore(dataSource, "page-jdbc");
        FileHarnessStore fileStore = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(directory.resolve("page-file"))
                .harnessId("page-file")
                .build());
        try {
            assertDeterministicPagination(jdbcStore);
            assertDeterministicPagination(fileStore);
        } finally {
            jdbcStore.close();
            fileStore.close();
        }
    }

    @Test
    public void legacyStateMigratesTransparentlyAndJacksonRoundTripsModels() throws Exception {
        Path legacyDir = directory.resolve("legacy-migration");
        Files.createDirectories(legacyDir);
        String legacyStateJson = "{"
                + "\"schemaVersion\":1,"
                + "\"harnessId\":\"legacy-harness\","
                + "\"version\":3,"
                + "\"updatedAtEpochMs\":1700000000000,"
                + "\"tasks\":{\"task-legacy\":{\"taskId\":\"task-legacy\",\"scopeKey\":\"s1\","
                + "\"title\":\"Legacy task\",\"status\":\"ACTIVE\",\"version\":2,"
                + "\"createdAtEpochMs\":1700000000000,\"updatedAtEpochMs\":1700000000100}},"
                + "\"events\":[{\"sequence\":1,\"type\":\"task.created\",\"entityId\":\"task-legacy\","
                + "\"actorId\":\"agent:legacy\",\"recordedAtEpochMs\":1700000000000,"
                + "\"payload\":{\"title\":\"Legacy task\"}}]"
                + "}";
        Files.write(legacyDir.resolve("state.json"), legacyStateJson.getBytes(StandardCharsets.UTF_8));

        FileHarnessStore store = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(legacyDir)
                .harnessId("legacy-harness")
                .build());
        try {
            CursorPage<HarnessEntityRecord> page = store.listEntitiesPage("TASK", "ACTIVE", null, 10);
            Assert.assertEquals(1, page.size());
            Assert.assertEquals("task-legacy", page.getItems().get(0).getEntityId());
            List<HarnessEventRecord> events = store.listEventsSince(0L, 10);
            Assert.assertEquals(1, events.size());
            Assert.assertEquals(1L, events.get(0).getSeq());
            Assert.assertEquals("task.created", events.get(0).getEventType());
            Assert.assertEquals("agent:legacy", events.get(0).getActor());

            // Verify AgentSessionSnapshot with AgentSessionSandboxBinding round-trips via Jackson.
            AgentSessionSnapshot snapshot = new AgentSessionSnapshot(
                    new AgentSessionMetadata("sess-1", 10L, 20L, Collections.<String, Object>singletonMap("k", "v")),
                    MemorySnapshot.from(Collections.<Object>singletonList("hello"), "summary"),
                    Collections.emptyList(),
                    null,
                    AgentSessionSandboxBinding.builder()
                            .providerId("local")
                            .sandboxSessionId("sb-1")
                            .status(SandboxStatus.RUNNING)
                            .profile("default")
                            .label("env", "test")
                            .boundAtEpochMs(100L)
                            .updatedAtEpochMs(200L)
                            .build(),
                    "run-1");
            AgentSessionSnapshot copied = HarnessJson.copy(snapshot, AgentSessionSnapshot.class);
            Assert.assertEquals("sess-1", copied.getSessionId());
            Assert.assertEquals("run-1", copied.getRunId());
            Assert.assertNotNull(copied.getSandboxBinding());
            Assert.assertEquals("sb-1", copied.getSandboxBinding().getSandboxSessionId());
            Assert.assertEquals(SandboxStatus.RUNNING, copied.getSandboxBinding().getStatus());
        } finally {
            store.close();
        }
    }

    @Test
    public void compiledTargetClassesContainZeroFastjson2ConstantPoolReferences() throws Exception {
        Path classesRoot = Paths.get(AgentHarness.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assert.assertTrue("expected compiled classes directory at " + classesRoot, Files.isDirectory(classesRoot));
        final List<Path> classFiles = new ArrayList<Path>();
        Files.walk(classesRoot)
                .filter(path -> path.toString().endsWith(".class"))
                .forEach(classFiles::add);
        Assert.assertFalse("expected compiled .class files in " + classesRoot, classFiles.isEmpty());

        List<String> offending = new ArrayList<String>();
        for (Path classFile : classFiles) {
            byte[] bytes = Files.readAllBytes(classFile);
            for (String utf8 : readConstantPoolUtf8Entries(bytes)) {
                if (utf8.contains("com/alibaba/fastjson2/") || utf8.contains("com.alibaba.fastjson2")) {
                    offending.add(classesRoot.relativize(classFile) + " -> " + utf8);
                }
            }
        }
        Assert.assertTrue("Found fastjson2 constant pool references: " + offending, offending.isEmpty());
    }

    private void appendAndAssertRebuildScenario(HarnessEventStore store) {
        MutationReceipt r1 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("TASK")
                .entityId("task-1")
                .eventType("task.created")
                .expectedVersion(0L)
                .writerEpoch(0L)
                .actor("agent:a")
                .timestampEpochMs(1000L)
                .payloadJson("{\"taskId\":\"task-1\",\"status\":\"PLANNED\",\"title\":\"First\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, r1.getOutcome());
        Assert.assertEquals(1L, r1.getSeq());
        Assert.assertEquals(1L, r1.getEntityVersion());

        MutationReceipt r2 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("TASK")
                .entityId("task-1")
                .eventType("task.transitioned")
                .expectedVersion(1L)
                .writerEpoch(0L)
                .actor("agent:a")
                .timestampEpochMs(1001L)
                .payloadJson("{\"status\":\"ACTIVE\",\"title\":\"First Active\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, r2.getOutcome());
        Assert.assertEquals(2L, r2.getSeq());
        Assert.assertEquals(2L, r2.getEntityVersion());

        MutationReceipt r3 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("TASK")
                .entityId("task-2")
                .eventType("task.created")
                .expectedVersion(0L)
                .writerEpoch(0L)
                .actor("agent:b")
                .timestampEpochMs(1002L)
                .payloadJson("{\"taskId\":\"task-2\",\"status\":\"PLANNED\",\"title\":\"Second\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, r3.getOutcome());

        MutationReceipt r4 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("EXECUTION")
                .entityId("exe-1")
                .eventType("execution.created")
                .expectedVersion(0L)
                .writerEpoch(0L)
                .actor("worker:w1")
                .timestampEpochMs(1003L)
                .payloadJson("{\"executionId\":\"exe-1\",\"status\":\"READY\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, r4.getOutcome());

        MutationReceipt r5 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("EXECUTION")
                .entityId("exe-1")
                .eventType("execution.succeeded")
                .expectedVersion(1L)
                .writerEpoch(0L)
                .actor("worker:w1")
                .timestampEpochMs(1004L)
                .payloadJson("{\"status\":\"SUCCEEDED\",\"outputText\":\"ok\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, r5.getOutcome());
        Assert.assertEquals(5, store.listEventsSince(0L, 100).size());
        assertRebuiltEntities(store);
    }

    private void assertRebuiltEntities(HarnessEventStore store) {
        CursorPage<HarnessEntityRecord> tasks = store.listEntitiesPage("TASK", null, null, 10);
        Assert.assertEquals(2L, tasks.getTotalCount());
        Assert.assertEquals("task-1", tasks.getItems().get(0).getEntityId());
        Assert.assertEquals("ACTIVE", tasks.getItems().get(0).getStatus());
        Assert.assertEquals(2L, tasks.getItems().get(0).getEntityVersion());
        Assert.assertEquals("First Active", tasks.getItems().get(0).decode(TaskRecord.class).getTitle());

        Assert.assertEquals("task-2", tasks.getItems().get(1).getEntityId());
        Assert.assertEquals("PLANNED", tasks.getItems().get(1).getStatus());
        Assert.assertEquals(1L, tasks.getItems().get(1).getEntityVersion());

        CursorPage<HarnessEntityRecord> executions = store.listEntitiesPage("EXECUTION", "SUCCEEDED", null, 10);
        Assert.assertEquals(1L, executions.getTotalCount());
        Assert.assertEquals("exe-1", executions.getItems().get(0).getEntityId());
        Assert.assertEquals(2L, executions.getItems().get(0).getEntityVersion());
    }

    private void assertIdempotencyReplay(HarnessEventStore store, String harnessId) {
        HarnessEventRecord firstEvent = HarnessEventRecord.builder()
                .harnessId(harnessId)
                .entityType("TASK")
                .entityId("task-idem")
                .eventType("task.created")
                .expectedVersion(0L)
                .writerEpoch(0L)
                .actor("agent:idem")
                .idempotencyKey("idem-key-1")
                .payloadJson("{\"taskId\":\"task-idem\",\"status\":\"PLANNED\",\"title\":\"Once\"}")
                .build();
        MutationReceipt first = store.appendEventAndProject(firstEvent);
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, first.getOutcome());
        Assert.assertEquals(1L, first.getSeq());
        Assert.assertEquals(1L, first.getEntityVersion());

        MutationReceipt replay = store.appendEventAndProject(firstEvent.toBuilder()
                .eventId("different-event-id")
                .payloadJson("{\"taskId\":\"task-idem\",\"status\":\"ACTIVE\",\"title\":\"Duplicate\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.IDEMPOTENT_REPLAY, replay.getOutcome());
        Assert.assertEquals(first.getSeq(), replay.getSeq());
        Assert.assertEquals(first.getEntityVersion(), replay.getEntityVersion());
        Assert.assertEquals("idem-key-1", replay.getIdempotencyKey());

        // Multiple null/blank idempotency keys must be permitted without colliding.
        MutationReceipt noKey1 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("TASK").entityId("task-no-key-1").eventType("task.created")
                .expectedVersion(0L).idempotencyKey(null)
                .payloadJson("{\"taskId\":\"task-no-key-1\",\"status\":\"PLANNED\",\"title\":\"N1\"}")
                .build());
        MutationReceipt noKey2 = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("TASK").entityId("task-no-key-2").eventType("task.created")
                .expectedVersion(0L).idempotencyKey("   ")
                .payloadJson("{\"taskId\":\"task-no-key-2\",\"status\":\"PLANNED\",\"title\":\"N2\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, noKey1.getOutcome());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, noKey2.getOutcome());

        List<HarnessEventRecord> events = store.listEventsSince(0L, 20);
        Assert.assertEquals(3, events.size());
        Assert.assertEquals("Once", store.listEntitiesPage("TASK", null, null, 10)
                .getItems().get(0).decode(TaskRecord.class).getTitle());
    }

    private void assertConcurrentVersionRace(final HarnessEventStore store) throws Exception {
        MutationReceipt init = store.appendEventAndProject(HarnessEventRecord.builder()
                .entityType("TASK")
                .entityId("task-race")
                .eventType("task.created")
                .expectedVersion(0L)
                .payloadJson("{\"taskId\":\"task-race\",\"status\":\"PLANNED\",\"title\":\"Initial\"}")
                .build());
        Assert.assertEquals(MutationReceipt.Outcome.APPLIED, init.getOutcome());

        final int contenders = 8;
        final CountDownLatch ready = new CountDownLatch(contenders);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<MutationReceipt>> futures = new ArrayList<Future<MutationReceipt>>();
            for (int i = 0; i < contenders; i++) {
                final int index = i;
                futures.add(pool.submit(new Callable<MutationReceipt>() {
                    @Override
                    public MutationReceipt call() throws Exception {
                        ready.countDown();
                        Assert.assertTrue(start.await(10L, TimeUnit.SECONDS));
                        return store.appendEventAndProject(HarnessEventRecord.builder()
                                .entityType("TASK")
                                .entityId("task-race")
                                .eventType("task.updated")
                                .expectedVersion(1L)
                                .idempotencyKey("race-" + index)
                                .payloadJson("{\"status\":\"ACTIVE\",\"title\":\"Winner-" + index + "\"}")
                                .build());
                    }
                }));
            }
            Assert.assertTrue(ready.await(10L, TimeUnit.SECONDS));
            start.countDown();

            int applied = 0;
            int conflicts = 0;
            for (Future<MutationReceipt> future : futures) {
                MutationReceipt receipt = future.get(10L, TimeUnit.SECONDS);
                if (receipt.getOutcome() == MutationReceipt.Outcome.APPLIED) {
                    applied++;
                    Assert.assertEquals(2L, receipt.getEntityVersion());
                } else if (receipt.getOutcome() == MutationReceipt.Outcome.VERSION_CONFLICT) {
                    conflicts++;
                    Assert.assertEquals(2L, receipt.getEntityVersion());
                } else {
                    Assert.fail("Unexpected outcome: " + receipt);
                }
            }
            Assert.assertEquals(1, applied);
            Assert.assertEquals(contenders - 1, conflicts);
            Assert.assertEquals(2, store.listEventsSince(0L, 100).size());
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertEpochFencing(HarnessEventStore store) {
        HarnessCommandGateway gateway = new HarnessCommandGateway(store);
        try {
            WriterEpochFence nodeA = gateway.claimWriterEpoch("node-a", 0L);
            Assert.assertEquals(1L, nodeA.getEpoch());
            Assert.assertEquals("node-a", nodeA.getHolderNodeId());

            try {
                gateway.claimWriterEpoch("node-b", 0L);
                Assert.fail("claiming stale expected epoch 0 must fail");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("epoch"));
            }

            WriterEpochFence nodeB = gateway.claimWriterEpoch("node-b", 1L);
            Assert.assertEquals(2L, nodeB.getEpoch());
            Assert.assertEquals("node-b", nodeB.getHolderNodeId());
            Assert.assertEquals(2L, gateway.currentWriterEpoch().getEpoch());

            Map<String, Object> stalePayload = new LinkedHashMap<String, Object>();
            stalePayload.put("taskId", "fenced-task");
            stalePayload.put("status", "PLANNED");
            MutationReceipt fenced = gateway.executeFencedMutation(
                    "TASK", "fenced-task", "task.created", 0L, nodeA.getEpoch(),
                    "stale-writer-key", stalePayload, HarnessActor.worker("node-a"));
            Assert.assertEquals(MutationReceipt.Outcome.EPOCH_FENCED, fenced.getOutcome());
            Assert.assertEquals(2L, fenced.getWriterEpoch());
            Assert.assertEquals(0, gateway.eventsSince(0L, 10).size());

            MutationReceipt accepted = gateway.executeFencedMutation(
                    "TASK", "fenced-task", "task.created", 0L, nodeB.getEpoch(),
                    "active-writer-key", stalePayload, HarnessActor.worker("node-b"));
            Assert.assertEquals(MutationReceipt.Outcome.APPLIED, accepted.getOutcome());
            Assert.assertEquals(1L, accepted.getSeq());
            Assert.assertEquals(1, gateway.eventsSince(0L, 10).size());
            Assert.assertNotNull(gateway.getTask("fenced-task"));
        } finally {
            gateway.close();
        }
    }

    private void assertDeterministicPagination(HarnessEventStore store) {
        HarnessCommandGateway gateway = new HarnessCommandGateway(store);
        try {
            for (int i = 1; i <= 5; i++) {
                gateway.createTask(HarnessTaskSpec.builder()
                        .taskId("task-0" + i)
                        .title("Planned " + i)
                        .build());
            }
            for (int i = 6; i <= 7; i++) {
                TaskRecord active = gateway.createTask(HarnessTaskSpec.builder()
                        .taskId("task-0" + i)
                        .title("Active " + i)
                        .build());
                gateway.transitionTask(active.getTaskId(), TaskStatus.ACTIVE, "started");
            }

            CursorPage<TaskRecord> page1 = gateway.listTasksPage(TaskStatus.PLANNED, null, 2);
            Assert.assertEquals(2, page1.size());
            Assert.assertTrue(page1.isHasMore());
            Assert.assertNotNull(page1.getNextCursor());
            Assert.assertEquals(5L, page1.getTotalCount());
            Assert.assertEquals("task-01", page1.getItems().get(0).getTaskId());
            Assert.assertEquals("task-02", page1.getItems().get(1).getTaskId());

            CursorPage<TaskRecord> page2 = gateway.listTasksPage(TaskStatus.PLANNED, page1.getNextCursor(), 2);
            Assert.assertEquals(2, page2.size());
            Assert.assertTrue(page2.isHasMore());
            Assert.assertNotNull(page2.getNextCursor());
            Assert.assertEquals("task-03", page2.getItems().get(0).getTaskId());
            Assert.assertEquals("task-04", page2.getItems().get(1).getTaskId());

            // Determinism check: repeating with the same cursor produces the exact same page.
            CursorPage<TaskRecord> page2Repeat = gateway.listTasksPage(TaskStatus.PLANNED, page1.getNextCursor(), 2);
            Assert.assertEquals(page2, page2Repeat);

            CursorPage<TaskRecord> page3 = gateway.listTasksPage(TaskStatus.PLANNED, page2.getNextCursor(), 2);
            Assert.assertEquals(1, page3.size());
            Assert.assertFalse(page3.isHasMore());
            Assert.assertNull(page3.getNextCursor());
            Assert.assertEquals("task-05", page3.getItems().get(0).getTaskId());

            CursorPage<TaskRecord> activePage = gateway.listTasksPage(TaskStatus.ACTIVE, null, 10);
            Assert.assertEquals(2L, activePage.getTotalCount());
            Assert.assertEquals(2, activePage.size());
            Assert.assertFalse(activePage.isHasMore());

            try {
                store.listEntitiesPage("TASK", null, "not-valid-base64!!!", 10);
                Assert.fail("invalid pagination cursor must be rejected");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("cursor"));
            }
        } finally {
            gateway.close();
        }
    }

    private JdbcDataSource h2(String prefix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + prefix + "_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static List<String> readConstantPoolUtf8Entries(byte[] classBytes) throws IOException {
        List<String> entries = new ArrayList<String>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes));
        int magic = in.readInt();
        Assert.assertEquals("Invalid class magic", 0xCAFEBABE, magic);
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int poolCount = in.readUnsignedShort();
        for (int i = 1; i < poolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: // CONSTANT_Utf8
                    entries.add(in.readUTF());
                    break;
                case 3: // Integer
                case 4: // Float
                case 9: // Fieldref
                case 10: // Methodref
                case 11: // InterfaceMethodref
                case 12: // NameAndType
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    skipFully(in, 4);
                    break;
                case 5: // Long
                case 6: // Double
                    skipFully(in, 8);
                    i++; // takes two constant pool entries
                    break;
                case 7: // Class
                case 8: // String
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    skipFully(in, 2);
                    break;
                case 15: // MethodHandle
                    skipFully(in, 3);
                    break;
                default:
                    throw new IOException("Unknown constant pool tag: " + tag);
            }
        }
        return entries;
    }

    private static void skipFully(DataInputStream in, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = in.skipBytes(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new IOException("Unexpected EOF skipping constant pool");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}
