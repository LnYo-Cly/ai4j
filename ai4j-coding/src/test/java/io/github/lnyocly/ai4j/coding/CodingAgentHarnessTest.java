package io.github.lnyocly.ai4j.coding;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.harness.HarnessAdapterState;
import io.github.lnyocly.ai4j.harness.HarnessPersistence;
import io.github.lnyocly.ai4j.harness.HarnessRunBudget;
import io.github.lnyocly.ai4j.harness.HarnessRunRequest;
import io.github.lnyocly.ai4j.harness.HarnessRunResult;
import io.github.lnyocly.ai4j.harness.HarnessRunStatus;
import io.github.lnyocly.ai4j.harness.WaitStatus;
import io.github.lnyocly.ai4j.harness.WaitType;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CodingAgentHarnessTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsCodingStateAndRestoresItForNewExecutionAndHarnessReopen() throws Exception {
        Path harnessDirectory = temporaryFolder.newFolder("coding-harness").toPath();
        Path workspace = temporaryFolder.newFolder("coding-workspace").toPath();

        CodingAgent firstAgent = codingAgent(new QueueModelClient(
                toolCallResult("echo-call", "echo"),
                textResult("completed after the new message")), new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "echo-result";
            }
        }, workspace);
        CodingAgentHarness firstHarness = CodingAgentHarness.builder()
                .codingAgent(firstAgent)
                .persistence(HarnessPersistence.file(harnessDirectory))
                .autoResume(false)
                .build();

        HarnessRunResult first = firstHarness.run(HarnessRunRequest.builder()
                .sessionId("coding-project")
                .input("implement the payment module")
                .budget(HarnessRunBudget.builder().maxSteps(1).build())
                .build());

        assertEquals(HarnessRunStatus.CONTINUATION_REQUIRED, first.getStatus());
        assertEquals(AgentExecutionStatus.CONTINUATION_REQUIRED,
                ((CodingAgentResult) first.getAdapterResult()).getExecutionStatus());
        assertNotNull(first.getExecution().getCheckpointId());
        HarnessAdapterState checkpointState = adapterState(firstHarness, first.getExecution().getCheckpointId());
        assertEquals(CodingAgentHarnessExecutionAdapter.ADAPTER_TYPE, checkpointState.getAdapterType());
        assertNotNull(checkpointState.getPayload().get(
                CodingAgentHarnessExecutionAdapter.CODING_SESSION_STATE));

        HarnessRunResult nextMessage = firstHarness.run(HarnessRunRequest.builder()
                .sessionId("coding-project")
                .input("continue the payment module")
                .build());
        assertEquals(HarnessRunStatus.COMPLETED, nextMessage.getStatus());
        assertEquals("completed after the new message", nextMessage.getOutputText());
        assertEquals("coding-project", nextMessage.getExecution().getSessionId());
        assertFalse(first.getExecution().getExecutionId()
                .equals(nextMessage.getExecution().getExecutionId()));
        firstHarness.close();

        CodingAgent reopenedAgent = codingAgent(new QueueModelClient(
                textResult("completed after the harness reopened")), new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "echo-result";
            }
        }, workspace);
        CodingAgentHarness reopened = CodingAgentHarness.builder()
                .codingAgent(reopenedAgent)
                .persistence(HarnessPersistence.file(harnessDirectory))
                .autoResume(false)
                .build();
        try {
            HarnessRunResult resumed = reopened.resume(first.getExecution().getExecutionId());
            assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
            assertEquals("completed after the harness reopened", resumed.getOutputText());
            assertEquals("coding-project", resumed.getExecution().getSessionId());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void asyncFunctionCallWaitsDurablyAndResumesCodingSession() throws Exception {
        Path harnessDirectory = temporaryFolder.newFolder("coding-async-harness").toPath();
        Path workspace = temporaryFolder.newFolder("coding-async-workspace").toPath();
        final CompletableFuture<AgentToolResult> completion = new CompletableFuture<AgentToolResult>();
        CodingAgent agent = codingAgent(new QueueModelClient(
                toolCallResult("slow-call", "slow_operation"),
                textResult("async coding operation completed")), new AsyncToolExecutor() {
            @Override
            public AgentToolExecution start(AgentToolCall call) {
                return AgentToolExecution.pending("operation-42", null,
                        "slow operation is pending", null, completion);
            }
        }, workspace);
        CodingAgentHarness harness = CodingAgentHarness.builder()
                .codingAgent(agent)
                .persistence(HarnessPersistence.file(harnessDirectory))
                .autoResume(false)
                .build();

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("coding-async-session")
                .input("run the remote build")
                .build());

        assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        CodingAgentResult waitingResult = (CodingAgentResult) waiting.getAdapterResult();
        assertEquals(AgentExecutionStatus.WAITING, waitingResult.getExecutionStatus());
        assertEquals("operation-42", waitingResult.getOperationId());
        assertNotNull(waiting.getWaitId());
        assertEquals(WaitType.ASYNC_OPERATION,
                harness.getGateway().getWait(waiting.getWaitId()).getType());

        completion.complete(AgentToolResult.builder()
                .output("remote build finished")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());
        awaitWait(harness, waiting.getWaitId());
        assertEquals(WaitStatus.DELIVERED,
                harness.getGateway().getWait(waiting.getWaitId()).getStatus());

        HarnessRunResult resumed = harness.resume(waiting.getExecution().getExecutionId());
        assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        assertEquals("async coding operation completed", resumed.getOutputText());
        harness.close();
    }

    private CodingAgent codingAgent(AgentModelClient model,
                                    ToolExecutor executor,
                                    Path workspace) {
        return CodingAgents.builder()
                .modelClient(model)
                .model("test-coding-model")
                .workspaceContext(io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext.builder()
                        .rootPath(workspace.toString())
                        .build())
                .codingOptions(CodingAgentOptions.builder()
                        .includeBuiltInTools(false)
                        .build())
                .toolRegistry(singleToolRegistry(executor instanceof AsyncToolExecutor
                        ? "slow_operation" : "echo"))
                .toolExecutor(executor)
                .build();
    }

    private AgentToolRegistry singleToolRegistry(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("Test coding tool");
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", function)));
    }

    private AgentModelResult toolCallResult(String callId, String name) {
        return AgentModelResult.builder()
                .toolCalls(Collections.singletonList(AgentToolCall.builder()
                        .callId(callId)
                        .name(name)
                        .arguments("{}")
                        .type("function")
                        .build()))
                .memoryItems(Collections.<Object>emptyList())
                .build();
    }

    private AgentModelResult textResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .toolCalls(Collections.<AgentToolCall>emptyList())
                .memoryItems(Collections.<Object>emptyList())
                .build();
    }

    private HarnessAdapterState adapterState(CodingAgentHarness harness, String checkpointId) {
        Object raw = harness.getGateway().getCheckpoint(checkpointId).getState()
                .get("harnessAdapterState");
        return JSON.parseObject(JSON.toJSONString(raw), HarnessAdapterState.class);
    }

    private void awaitWait(CodingAgentHarness harness, String waitId) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5L);
        while (System.currentTimeMillis() < deadline) {
            if (WaitStatus.DELIVERED.equals(harness.getGateway().getWait(waitId).getStatus())) {
                return;
            }
            Thread.sleep(10L);
        }
    }

    private static final class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> results;

        private QueueModelClient(AgentModelResult... results) {
            this.results = new ArrayDeque<AgentModelResult>(Arrays.asList(results));
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return results.isEmpty() ? AgentModelResult.builder()
                    .outputText("unexpected model call")
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .build() : results.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }
}
