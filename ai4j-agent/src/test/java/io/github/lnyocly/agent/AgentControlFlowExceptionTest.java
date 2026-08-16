package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.control.AgentHostInputChannel;
import io.github.lnyocly.ai4j.agent.control.AgentHostInputException;
import io.github.lnyocly.ai4j.agent.control.HostInputToolExecutor;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.permission.AgentApprovalRequiredException;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionDecision;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionRequest;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * #262：宿主审批/输入类控制流异常必须中断循环并从 run 抛出（禁止降级 TOOL_ERROR）；
 * HostInputToolExecutor 的阻塞通道语义（工具结果 = 用户答案）。
 */
public class AgentControlFlowExceptionTest {

    /** 第 1 次返回 ask_user 工具调用，之后返回 fallback 文本（若被调到即证明异常被吞）。 */
    private static final class AskThenFallbackModelClient implements AgentModelClient {
        final AtomicInteger invocations = new AtomicInteger();

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (invocations.incrementAndGet() == 1) {
                return AgentModelResult.builder()
                        .outputText("I will ask the user.")
                        .toolCalls(Arrays.asList(AgentToolCall.builder()
                                .callId("call_1").name("ask_user").arguments("{}").type("function").build()))
                        .memoryItems(new ArrayList<>())
                        .build();
            }
            return AgentModelResult.builder().outputText("fallback after tool").memoryItems(new ArrayList<>()).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static AgentContext contextWith(AgentModelClient modelClient, ToolExecutor executor) {
        return AgentContext.builder()
                .modelClient(modelClient)
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(4).build())
                .model("test-model")
                .build();
    }

    @Test
    public void hostInputExceptionPropagatesAndStopsLoop() throws Exception {
        AskThenFallbackModelClient modelClient = new AskThenFallbackModelClient();
        ToolExecutor askUser = call -> {
            throw new AgentHostInputException("ask_user", Map.of("question", "画幅？"));
        };

        try {
            new ReActRuntime().run(contextWith(modelClient, askUser), AgentRequest.builder().input("hi").build());
            Assert.fail("expected AgentHostInputException to propagate");
        } catch (AgentHostInputException expected) {
            Assert.assertEquals("画幅？", expected.getRequest().get("question"));
        }
        // 循环在工具处中断：模型没有被再次调用，也就没有 fallback 生成。
        Assert.assertEquals(1, modelClient.invocations.get());
    }

    @Test
    public void approvalExceptionPropagatesAndStopsLoop() throws Exception {
        AskThenFallbackModelClient modelClient = new AskThenFallbackModelClient();
        ToolExecutor approval = call -> {
            AgentPermissionRequest request = AgentPermissionRequest.builder()
                    .toolCall(call)
                    .environment(AgentExecutionEnvironment.LOCAL)
                    .build();
            throw new AgentApprovalRequiredException("needs approval", request,
                    AgentPermissionDecision.requireApproval("confirm canvas writes"));
        };

        try {
            new ReActRuntime().run(contextWith(modelClient, approval), AgentRequest.builder().input("hi").build());
            Assert.fail("expected AgentApprovalRequiredException to propagate");
        } catch (AgentApprovalRequiredException expected) {
            Assert.assertEquals("confirm canvas writes", expected.getDecision().getReason());
        }
        Assert.assertEquals(1, modelClient.invocations.get());
    }

    @Test
    public void hostInputChannelAnswerBecomesToolResultAndLoopContinues() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        AgentModelClient client = new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                if (calls.incrementAndGet() == 1) {
                    return AgentModelResult.builder()
                            .outputText("I will ask the user.")
                            .toolCalls(Arrays.asList(AgentToolCall.builder()
                                    .callId("call_1").name("ask_user")
                                    .arguments("{\"question\":\"画幅？\"}").type("function").build()))
                            .memoryItems(new ArrayList<>())
                            .build();
                }
                return AgentModelResult.builder().outputText("done with the answer").memoryItems(new ArrayList<>()).build();
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                return create(prompt);
            }
        };
        AgentHostInputChannel channel = (call, request) -> {
            Assert.assertEquals("画幅？", request.get("question"));
            return "9:16 竖屏";
        };
        HostInputToolExecutor executor = new HostInputToolExecutor(
                call -> "delegated", channel, Set.of("ask_user"));

        AgentResult result = new ReActRuntime().run(
                contextWith(client, executor), AgentRequest.builder().input("hi").build());

        Assert.assertEquals("done with the answer", result.getOutputText());
        // 第二次模型调用发生 = 工具结果（用户答案）被接受、循环正常继续。
        Assert.assertEquals(2, calls.get());
    }

    @Test
    public void hostInputTimeoutBecomesReadableToolResult() throws Exception {
        AgentHostInputChannel timeoutChannel = (call, request) -> {
            throw new TimeoutException("10 分钟未作答");
        };
        HostInputToolExecutor executor = new HostInputToolExecutor(
                call -> "delegated", timeoutChannel, Set.of("ask_user"));
        AgentToolCall call = AgentToolCall.builder()
                .callId("call_1").name("ask_user").arguments("{}").type("function").build();

        Assert.assertEquals("HOST_INPUT_TIMEOUT: 10 分钟未作答", executor.execute(call));
    }

    @Test
    public void nonHostToolsDelegateUntouched() throws Exception {
        AgentHostInputChannel channel = (call, request) -> "should not be used";
        HostInputToolExecutor executor = new HostInputToolExecutor(
                call -> "delegated", channel, Set.of("ask_user"));
        AgentToolCall other = AgentToolCall.builder()
                .callId("call_2").name("read_file").arguments("{}").type("function").build();

        Assert.assertEquals("delegated", executor.execute(other));
    }
}
