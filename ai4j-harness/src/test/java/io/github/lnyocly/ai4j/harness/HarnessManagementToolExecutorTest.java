package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class HarnessManagementToolExecutorTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-management-test-");
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
                            // Best effort cleanup of the test directory.
                        }
                    });
        }
    }

    @Test
    public void managementCommandsInheritScopeAndCannotReadAnotherScope() throws Exception {
        HarnessCommandGateway gateway = gateway("scope-management");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-shop-a")
                .scopeKey("shop-a")
                .sessionId("customer-session-a")
                .build());
        HarnessExecutionContext context = new HarnessExecutionContext(
                gateway, execution.getExecutionId(), null, execution.getSessionId(),
                execution.getScopeKey(), execution.getRunId(), HarnessActor.agent("support-agent"), null);
        HarnessManagementToolExecutor tools = new HarnessManagementToolExecutor(context);

        TaskRecord first = JSON.parseObject(result(tools, "task-create-shop-a-1",
                HarnessToolNames.TASK_MANAGE,
                "{\"operation\":\"create\",\"taskId\":\"task-shop-a-1\"," 
                        + "\"title\":\"Investigate refund\"}"), TaskRecord.class);
        Assert.assertEquals("shop-a", first.getScopeKey());
        Assert.assertEquals(first.getTaskId(), context.getTaskId());

        TaskRecord second = JSON.parseObject(result(tools, "task-create-shop-a-2",
                HarnessToolNames.TASK_MANAGE,
                "{\"operation\":\"create\",\"taskId\":\"task-shop-a-2\"," 
                        + "\"title\":\"Check replacement\"}"), TaskRecord.class);
        Assert.assertEquals("shop-a", second.getScopeKey());

        TaskRecord foreign = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-shop-b")
                .scopeKey("shop-b")
                .title("Foreign task")
                .build());
        Assert.assertNotNull(foreign);

        String view = result(tools, HarnessToolNames.CONTEXT_GET, "{}");
        Assert.assertTrue(view.contains("task-shop-a-1"));
        Assert.assertTrue(view.contains("task-shop-a-2"));
        Assert.assertFalse(view.contains("task-shop-b"));

        try {
            result(tools, HarnessToolNames.TASK_MANAGE,
                    "{\"operation\":\"get\",\"taskId\":\"task-shop-b\"}");
            Assert.fail("a scoped Agent must not read another scope");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("outside"));
        }

        try {
            result(tools, HarnessToolNames.TASK_MANAGE,
                    "{\"operation\":\"create\",\"scopeKey\":\"shop-b\","
                            + "\"title\":\"Cross-scope task\"}");
            Assert.fail("a scoped Agent must not choose another write scope");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("outside"));
        }
        gateway.close();
    }

    @Test
    public void relationManagementUsesGatewayValidationAndScopeProjection() throws Exception {
        HarnessCommandGateway gateway = gateway("relation-management");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-relation")
                .scopeKey("project-a")
                .build());
        HarnessExecutionContext context = new HarnessExecutionContext(
                gateway, execution.getExecutionId(), null, null,
                execution.getScopeKey(), execution.getRunId(), HarnessActor.agent("coding-agent"), null);
        HarnessManagementToolExecutor tools = new HarnessManagementToolExecutor(context);
        TaskRecord from = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-from")
                .scopeKey("project-a")
                .title("Source")
                .build());
        TaskRecord to = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-to")
                .scopeKey("project-a")
                .title("Target")
                .build());

        RelationRecord relation = JSON.parseObject(result(tools, HarnessToolNames.RELATION_MANAGE,
                "{\"operation\":\"create\",\"type\":\"SUPPORTS\","
                        + "\"fromKind\":\"TASK\",\"fromId\":\"task-from\","
                        + "\"toKind\":\"TASK\",\"toId\":\"task-to\"}"), RelationRecord.class);
        Assert.assertEquals(RelationType.SUPPORTS, relation.getType());
        Assert.assertEquals("project-a", relation.getScopeKey());
        Assert.assertEquals(1, gateway.listRelationsInScope("project-a").size());
        Assert.assertEquals(0, gateway.listRelationsInScope("project-b").size());

        String listed = result(tools, HarnessToolNames.RELATION_MANAGE,
                "{\"operation\":\"list\"}");
        Assert.assertTrue(listed.contains(relation.getRelationId()));
        String fetched = result(tools, HarnessToolNames.RELATION_MANAGE,
                "{\"operation\":\"get\",\"relationId\":\""
                        + relation.getRelationId() + "\"}");
        Assert.assertTrue(fetched.contains(relation.getRelationId()));

        TaskRecord foreign = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-foreign")
                .scopeKey("project-b")
                .title("Foreign")
                .build());
        try {
            result(tools, HarnessToolNames.RELATION_MANAGE,
                    "{\"operation\":\"add\",\"type\":\"SUPPORTS\","
                            + "\"fromKind\":\"TASK\",\"fromId\":\""
                            + from.getTaskId() + "\",\"toKind\":\"TASK\",\"toId\":\""
                            + foreign.getTaskId() + "\"}");
            Assert.fail("relation endpoints from different scopes must be rejected");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("scope"));
        }
        gateway.close();
    }

    @Test
    public void repeatedTaskCreateCallIsIdempotentWithoutExplicitKey() throws Exception {
        HarnessCommandGateway gateway = gateway("task-create-idempotency");
        gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-1")
                .scopeKey("project-a")
                .sessionId("session-1")
                .runId("run-1")
                .build());
        HarnessExecutionContext context = new HarnessExecutionContext(
                gateway, "execution-1", null, "session-1", "project-a", "run-1",
                HarnessActor.agent("test-agent"), null);
        HarnessManagementToolExecutor tools = new HarnessManagementToolExecutor(context);
        AgentToolCall call = AgentToolCall.builder()
                .name(HarnessToolNames.TASK_MANAGE)
                .callId("task-create-call")
                .arguments("{\"operation\":\"create\",\"taskId\":\"first-task\","
                        + "\"title\":\"Discovered work\"}")
                .type("function_call")
                .build();

        TaskRecord first = JSON.parseObject(result(tools, call), TaskRecord.class);
        TaskRecord repeated = JSON.parseObject(result(tools, call), TaskRecord.class);

        Assert.assertEquals(first.getTaskId(), repeated.getTaskId());
        Assert.assertEquals(1, gateway.listTasks("project-a").size());
        gateway.close();
    }

    private HarnessCommandGateway gateway(String harnessId) {
        return new HarnessCommandGateway(
                HarnessPersistence.file(FileHarnessConfig.builder()
                        .directory(directory.resolve(harnessId))
                        .harnessId(harnessId)
                        .build()).getStore(),
                HarnessContract.builder().build(), HarnessActor.agent("test-agent"));
    }

    private String result(HarnessManagementToolExecutor tools,
                          String name,
                          String arguments) throws Exception {
        return result(tools, AgentToolCall.builder()
                .name(name)
                .callId(name + "-call")
                .arguments(arguments)
                .type("function_call")
                .build());
    }

    private String result(HarnessManagementToolExecutor tools,
                          String callId,
                          String name,
                          String arguments) throws Exception {
        return result(tools, AgentToolCall.builder()
                .name(name)
                .callId(callId)
                .arguments(arguments)
                .type("function_call")
                .build());
    }

    private String result(HarnessManagementToolExecutor tools,
                          AgentToolCall call) throws Exception {
        AgentToolExecution execution = tools.start(call);
        AgentToolResult result = execution == null ? null : execution.await();
        return result == null ? null : result.getOutput();
    }
}
