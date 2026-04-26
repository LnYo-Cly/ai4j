package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import io.github.lnyocly.ai4j.coding.definition.StaticCodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.task.CodingTaskManager;
import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;
import io.github.lnyocly.ai4j.coding.task.InMemoryCodingTaskManager;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CodingAgentBuilderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRunBuiltInCodingToolWithinAgentLoop() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-agent").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding workspace")
                .build();

        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name(CodingToolNames.BASH)
                        .arguments("{\"action\":\"exec\",\"command\":\"echo session-ready\"}")
                        .callId("call-1")
                        .build()))
                .rawResponse("tool-call")
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("done")
                .rawResponse("final")
                .build());

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSession session = agent.newSession();
        CodingAgentResult result;
        try {
            result = session.run("Run a quick shell check.");
        } finally {
            session.close();
        }

        assertNotNull(session.getSessionId());
        assertEquals("done", result.getOutputText());
        assertEquals(1, result.getToolResults().size());
        assertTrue(result.getToolResults().get(0).getOutput().toLowerCase().contains("session-ready"));
    }

    @Test
    public void shouldApplyPatchWithinAgentLoop() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-agent-patch").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit patch workspace")
                .build();

        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name(CodingToolNames.APPLY_PATCH)
                        .arguments("{\"patch\":\"*** Begin Patch\\n*** Add File: notes/patch.txt\\n+created-by-agent\\n*** End Patch\"}")
                        .callId("call-2")
                        .build()))
                .rawResponse("tool-call")
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("patch-done")
                .rawResponse("final")
                .build());

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSession session = agent.newSession();
        CodingAgentResult result;
        try {
            result = session.run("Create a file by patch.");
        } finally {
            session.close();
        }

        assertEquals("patch-done", result.getOutputText());
        assertEquals(1, result.getToolResults().size());
        assertTrue(Files.exists(workspaceRoot.resolve("notes/patch.txt")));
        assertEquals("created-by-agent",
                new String(Files.readAllBytes(workspaceRoot.resolve("notes/patch.txt")), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldReturnToolErrorInsteadOfAbortingSession() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-agent-invalid-patch").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit invalid patch workspace")
                .build();

        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name(CodingToolNames.APPLY_PATCH)
                        .arguments("{\"patch\":\"*** Begin Patch\\n*** Unknown: calculator.py\\n*** End Patch\"}")
                        .callId("call-invalid-patch")
                        .build()))
                .rawResponse("tool-call")
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("tool-error-handled")
                .rawResponse("final")
                .build());

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSession session = agent.newSession();
        CodingAgentResult result;
        try {
            result = session.run("Trigger an invalid patch.");
        } finally {
            session.close();
        }

        assertEquals("tool-error-handled", result.getOutputText());
        assertEquals(1, result.getToolResults().size());
        assertTrue(result.getToolResults().get(0).getOutput().contains("TOOL_ERROR:"));
        assertTrue(result.getToolResults().get(0).getOutput().contains("Unsupported patch line"));
        assertTrue(Files.notExists(workspaceRoot.resolve("calculator.py")));
    }

    @Test
    public void shouldAllowModelToInvokeDelegateTool() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-agent-delegate").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit delegate workspace")
                .build();

        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("delegate_plan")
                        .arguments("{\"task\":\"Draft a short implementation plan\",\"context\":\"Focus on tests first\"}")
                        .callId("delegate-call-1")
                        .build()))
                .rawResponse("delegate-tool-call")
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("delegate plan ready")
                .rawResponse("delegate-child")
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("root-complete")
                .rawResponse("root-final")
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

        CodingSession session = agent.newSession();
        CodingAgentResult result;
        try {
            result = session.run("Delegate planning to a worker.");
        } finally {
            session.close();
        }

        assertNotNull(result.getOutputText());
        assertTrue(!result.getOutputText().trim().isEmpty());
        assertEquals(1, result.getToolResults().size());
        String toolOutput = result.getToolResults().get(0).getOutput();
        assertTrue(toolOutput, toolOutput.contains("\"definitionName\":\"plan\""));
        assertTrue(toolOutput, toolOutput.contains("\"status\":\"completed\""));
        assertTrue(toolOutput, toolOutput.contains("\"output\":\"delegate plan ready\""));

        assertEquals(1, taskManager.listTasksByParentSessionId(session.getSessionId()).size());
        CodingTask task = taskManager.listTasksByParentSessionId(session.getSessionId()).get(0);
        assertEquals(CodingTaskStatus.COMPLETED, task.getStatus());
        assertEquals("delegate plan ready", task.getOutputText());
    }

    @Test
    public void shouldAllowModelToInvokeGenericSubAgentToolWithinCodingSession() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-agent-subagent").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit subagent workspace")
                .build();

        QueueModelClient reviewModelClient = new QueueModelClient();
        reviewModelClient.enqueue(AgentModelResult.builder()
                .outputText("review-ready")
                .rawResponse("review-final")
                .build());

        QueueModelClient rootModelClient = new QueueModelClient();
        rootModelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("subagent_review")
                        .arguments("{\"task\":\"Review the proposed patch\",\"context\":\"Focus on correctness risks\"}")
                        .callId("subagent-call-1")
                        .build()))
                .rawResponse("subagent-tool-call")
                .build());
        rootModelClient.enqueue(AgentModelResult.builder()
                .outputText("root-finished")
                .rawResponse("root-final")
                .build());

        CodingAgent agent = CodingAgents.builder()
                .modelClient(rootModelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .subAgent(SubAgentDefinition.builder()
                        .name("review")
                        .toolName("subagent_review")
                        .description("Review coding changes and report risks.")
                        .agent(Agents.react()
                                .modelClient(reviewModelClient)
                                .model("glm-4.5-flash")
                                .build())
                        .build())
                .handoffPolicy(HandoffPolicy.builder().maxDepth(1).build())
                .build();

        CodingAgentResult result;
        try (CodingSession session = agent.newSession()) {
            result = session.run("Ask the reviewer for a focused review.");
        }

        assertEquals("root-finished", result.getOutputText());
        assertEquals(1, result.getToolResults().size());
        String toolOutput = result.getToolResults().get(0).getOutput();
        assertTrue(toolOutput, toolOutput.contains("\"subagent\":\"review\""));
        assertTrue(toolOutput, toolOutput.contains("\"toolName\":\"subagent_review\""));
        assertTrue(toolOutput, toolOutput.contains("\"output\":\"review-ready\""));
    }

    @Test
    public void shouldAllowDelegatedCodingWorkerToInvokeConfiguredSubAgent() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-agent-delegate-subagent").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit delegate subagent workspace")
                .build();

        QueueModelClient reviewModelClient = new QueueModelClient();
        reviewModelClient.enqueue(AgentModelResult.builder()
                .outputText("review-ready")
                .rawResponse("review-final")
                .build());

        QueueModelClient rootAndDelegateModelClient = new QueueModelClient();
        rootAndDelegateModelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("delegate_review_worker")
                        .arguments("{\"task\":\"Delegate a review step\",\"context\":\"Use the review specialist\"}")
                        .callId("delegate-review-call-1")
                        .build()))
                .rawResponse("delegate-worker-call")
                .build());
        rootAndDelegateModelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("subagent_review")
                        .arguments("{\"task\":\"Review the delegated change\"}")
                        .callId("delegate-subagent-call-1")
                        .build()))
                .rawResponse("delegate-subagent-call")
                .build());
        rootAndDelegateModelClient.enqueue(AgentModelResult.builder()
                .outputText("delegate-worker-finished")
                .rawResponse("delegate-worker-final")
                .build());
        rootAndDelegateModelClient.enqueue(AgentModelResult.builder()
                .outputText("root-finished")
                .rawResponse("root-final")
                .build());

        CodingTaskManager taskManager = new InMemoryCodingTaskManager();
        StaticCodingAgentDefinitionRegistry definitionRegistry = new StaticCodingAgentDefinitionRegistry(
                Arrays.asList(CodingAgentDefinition.builder()
                        .name("review-worker")
                        .toolName("delegate_review_worker")
                        .description("Delegated worker that can call the review subagent.")
                        .allowedToolNames(new LinkedHashSet<String>(Arrays.asList("subagent_review")))
                        .sessionMode(CodingSessionMode.FORK)
                        .build())
        );

        CodingAgent agent = CodingAgents.builder()
                .modelClient(rootAndDelegateModelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .definitionRegistry(definitionRegistry)
                .taskManager(taskManager)
                .subAgent(SubAgentDefinition.builder()
                        .name("review")
                        .toolName("subagent_review")
                        .description("Review coding changes and report risks.")
                        .agent(Agents.react()
                                .modelClient(reviewModelClient)
                                .model("glm-4.5-flash")
                                .build())
                        .build())
                .handoffPolicy(HandoffPolicy.builder().maxDepth(2).build())
                .build();

        CodingAgentResult result;
        try (CodingSession session = agent.newSession()) {
            result = session.run("Delegate review work to a specialized worker.");

            assertEquals("root-finished", result.getOutputText());
            assertEquals(1, result.getToolResults().size());
            String toolOutput = result.getToolResults().get(0).getOutput();
            assertTrue(toolOutput, toolOutput.contains("\"definitionName\":\"review-worker\""));
            assertTrue(toolOutput, toolOutput.contains("\"status\":\"completed\""));
            assertTrue(toolOutput, toolOutput.contains("\"output\":\"delegate-worker-finished\""));

            assertEquals(1, taskManager.listTasksByParentSessionId(session.getSessionId()).size());
            CodingTask task = taskManager.listTasksByParentSessionId(session.getSessionId()).get(0);
            assertEquals(CodingTaskStatus.COMPLETED, task.getStatus());
            assertEquals("delegate-worker-finished", task.getOutputText());
        }
    }

    private static class QueueModelClient implements AgentModelClient {

        private final Deque<AgentModelResult> results = new ArrayDeque<>();

        private void enqueue(AgentModelResult result) {
            results.addLast(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return results.removeFirst();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = results.removeFirst();
            if (listener != null && result != null && result.getOutputText() != null) {
                listener.onDeltaText(result.getOutputText());
            }
            return result;
        }
    }
}
