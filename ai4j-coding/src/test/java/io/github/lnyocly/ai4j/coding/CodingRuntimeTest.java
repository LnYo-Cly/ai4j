package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.definition.BuiltInCodingAgentDefinitions;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateRequest;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateResult;
import io.github.lnyocly.ai4j.coding.policy.CodingToolContextPolicy;
import io.github.lnyocly.ai4j.coding.policy.CodingToolPolicyResolver;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLinkStore;
import io.github.lnyocly.ai4j.coding.session.InMemoryCodingSessionLinkStore;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.task.CodingTaskManager;
import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;
import io.github.lnyocly.ai4j.coding.task.InMemoryCodingTaskManager;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.CodingToolRegistryFactory;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CodingRuntimeTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldResolveBuiltInDefinitionsByNameAndToolName() {
        CodingAgentDefinitionRegistry registry = BuiltInCodingAgentDefinitions.registry();

        assertEquals(4, registry.listDefinitions().size());
        assertNotNull(registry.getDefinition(BuiltInCodingAgentDefinitions.GENERAL_PURPOSE));
        assertNotNull(registry.getDefinition("delegate_explore"));
        assertTrue(registry.getDefinition("explore").getAllowedToolNames().contains(CodingToolNames.READ_FILE));
        assertFalse(registry.getDefinition("explore").getAllowedToolNames().contains(CodingToolNames.WRITE_FILE));
    }

    @Test
    public void shouldExposeDelegateToolsInBuiltInRegistry() {
        AgentToolRegistry registry = CodingAgentBuilder.createBuiltInRegistry(
                CodingAgentOptions.builder().build(),
                BuiltInCodingAgentDefinitions.registry()
        );

        List<String> toolNames = collectToolNames(registry);
        assertTrue(toolNames.contains("delegate_general_purpose"));
        assertTrue(toolNames.contains("delegate_explore"));
        assertTrue(toolNames.contains("delegate_plan"));
        assertTrue(toolNames.contains("delegate_verification"));
    }

    @Test
    public void shouldFilterToolsForReadOnlyDefinitions() throws Exception {
        AgentToolRegistry registry = CodingToolRegistryFactory.createBuiltInRegistry();
        ToolExecutor executor = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return call == null ? null : call.getName();
            }
        };
        CodingAgentDefinition definition = BuiltInCodingAgentDefinitions.registry().getDefinition("explore");

        CodingToolContextPolicy policy = new CodingToolPolicyResolver().resolve(registry, executor, definition);

        assertEquals(2, policy.getToolRegistry().getTools().size());
        assertEquals(CodingToolNames.READ_FILE, policy.getToolExecutor().execute(AgentToolCall.builder().name(CodingToolNames.READ_FILE).build()));
        try {
            policy.getToolExecutor().execute(AgentToolCall.builder().name(CodingToolNames.WRITE_FILE).build());
            fail("Expected disallowed tool execution to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not allowed"));
        }
    }

    @Test
    public void shouldDelegateSynchronouslyAndTrackTaskAndLinks() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("runtime-sync").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit runtime sync workspace")
                .build();
        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder().outputText("sync-child-done").build());
        CodingTaskManager taskManager = new InMemoryCodingTaskManager();
        CodingSessionLinkStore linkStore = new InMemoryCodingSessionLinkStore();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .taskManager(taskManager)
                .sessionLinkStore(linkStore)
                .build();

        try (CodingSession session = agent.newSession()) {
            CodingDelegateResult result = session.delegate(CodingDelegateRequest.builder()
                    .definitionName("explore")
                    .input("Inspect the workspace and summarize what matters.")
                    .build());

            assertEquals(CodingTaskStatus.COMPLETED, result.getStatus());
            assertEquals("sync-child-done", result.getOutputText());
            CodingTask task = taskManager.getTask(result.getTaskId());
            assertNotNull(task);
            assertEquals(CodingTaskStatus.COMPLETED, task.getStatus());
            assertEquals(session.getSessionId(), task.getParentSessionId());
            List<CodingSessionLink> links = linkStore.listLinksByParentSessionId(session.getSessionId());
            assertEquals(1, links.size());
            assertEquals(result.getTaskId(), links.get(0).getTaskId());
        }
    }

    @Test
    public void shouldFallbackToAssistantMemoryWhenDelegateOutputTextIsBlank() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("runtime-sync-memory-fallback").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit runtime sync workspace fallback")
                .build();
        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("")
                .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", "delegate plan ready from memory")))
                .build());
        CodingTaskManager taskManager = new InMemoryCodingTaskManager();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoContinueEnabled(false)
                        .build())
                .taskManager(taskManager)
                .build();

        try (CodingSession session = agent.newSession()) {
            CodingDelegateResult result = session.delegate(CodingDelegateRequest.builder()
                    .definitionName("plan")
                    .input("Draft a short implementation plan.")
                    .build());

            assertEquals(CodingTaskStatus.COMPLETED, result.getStatus());
            assertEquals("delegate plan ready from memory", result.getOutputText());
            CodingTask task = taskManager.getTask(result.getTaskId());
            assertNotNull(task);
            assertEquals("delegate plan ready from memory", task.getOutputText());
        }
    }

    @Test
    public void shouldDelegateInBackgroundAndEventuallyComplete() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("runtime-background").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit runtime background workspace")
                .build();
        BlockingModelClient modelClient = new BlockingModelClient("background-child-done");
        CodingTaskManager taskManager = new InMemoryCodingTaskManager();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .taskManager(taskManager)
                .build();

        try (CodingSession session = agent.newSession()) {
            CodingDelegateResult result = session.delegate(CodingDelegateRequest.builder()
                    .definitionName("verification")
                    .input("Run verification in the background.")
                    .build());

            assertTrue(result.isBackground());
            assertTrue(modelClient.awaitStarted(3, TimeUnit.SECONDS));

            CodingTask runningTask = taskManager.getTask(result.getTaskId());
            assertNotNull(runningTask);
            assertTrue(Arrays.asList(CodingTaskStatus.QUEUED, CodingTaskStatus.STARTING, CodingTaskStatus.RUNNING, CodingTaskStatus.COMPLETED)
                    .contains(runningTask.getStatus()));

            modelClient.release();
            CodingTask completed = waitForTask(taskManager, result.getTaskId(), 5000L);
            assertNotNull(completed);
            assertEquals(CodingTaskStatus.COMPLETED, completed.getStatus());
            assertEquals("background-child-done", completed.getOutputText());
        }
    }

    private CodingTask waitForTask(CodingTaskManager taskManager, String taskId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            CodingTask task = taskManager.getTask(taskId);
            if (task != null && task.getStatus() != null && task.getStatus().isTerminal()) {
                return task;
            }
            Thread.sleep(50L);
        }
        return taskManager.getTask(taskId);
    }

    private List<String> collectToolNames(AgentToolRegistry registry) {
        List<String> toolNames = new java.util.ArrayList<String>();
        if (registry == null || registry.getTools() == null) {
            return toolNames;
        }
        for (Object tool : registry.getTools()) {
            if (tool instanceof io.github.lnyocly.ai4j.platform.openai.tool.Tool) {
                io.github.lnyocly.ai4j.platform.openai.tool.Tool.Function function =
                        ((io.github.lnyocly.ai4j.platform.openai.tool.Tool) tool).getFunction();
                if (function != null && function.getName() != null) {
                    toolNames.add(function.getName());
                }
            }
        }
        return toolNames;
    }

    private static class QueueModelClient implements AgentModelClient {

        private final LinkedBlockingQueue<AgentModelResult> results = new LinkedBlockingQueue<AgentModelResult>();

        private void enqueue(AgentModelResult result) {
            results.offer(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) throws Exception {
            return results.poll(3, TimeUnit.SECONDS);
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
            AgentModelResult result = create(prompt);
            if (listener != null && result != null && result.getOutputText() != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }
    }

    private static class BlockingModelClient implements AgentModelClient {

        private final String outputText;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingModelClient(String outputText) {
            this.outputText = outputText;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) throws Exception {
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
            return AgentModelResult.builder()
                    .outputText(outputText)
                    .memoryItems(Collections.<Object>emptyList())
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }

        private boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        private void release() {
            release.countDown();
        }
    }
}
