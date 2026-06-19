package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.permission.AgentApprovalRequiredException;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionDecision;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionException;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionRequest;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class AgentApprovalPermissionPolicyTest {

    @Test
    public void shouldAllowToolWhenPolicyAllowsIt() throws Exception {
        CountingToolExecutor delegate = new CountingToolExecutor();
        AgentPermissionToolExecutor executor = new AgentPermissionToolExecutor(
                delegate,
                AgentPermissionPolicies.allowTools(Collections.singleton("echo")),
                AgentExecutionEnvironment.LOCAL);

        String output = executor.execute(call("echo"));

        Assert.assertEquals("{\"ok\":true}", output);
        Assert.assertEquals(1, delegate.count);
    }

    @Test
    public void shouldBlockDeniedToolBeforeDelegateRuns() throws Exception {
        CountingToolExecutor delegate = new CountingToolExecutor();
        AgentPermissionToolExecutor executor = new AgentPermissionToolExecutor(
                delegate,
                AgentPermissionPolicies.denyTools(Collections.singleton("bash"), "dangerous local command"),
                AgentExecutionEnvironment.LOCAL);

        try {
            executor.execute(call("bash"));
            Assert.fail("expected permission exception");
        } catch (AgentPermissionException expected) {
            Assert.assertTrue(expected.getMessage().contains("Tool permission denied"));
            Assert.assertTrue(expected.getMessage().contains("dangerous local command"));
            Assert.assertEquals(AgentExecutionEnvironment.LOCAL, expected.getRequest().getEnvironment());
        }
        Assert.assertEquals(0, delegate.count);
    }

    @Test
    public void shouldBlockApprovalRequiredToolBeforeDelegateRuns() throws Exception {
        CountingToolExecutor delegate = new CountingToolExecutor();
        AgentPermissionToolExecutor executor = new AgentPermissionToolExecutor(
                delegate,
                AgentPermissionPolicies.requireApprovalForTools(Collections.singleton("write_file"), "write operation"),
                AgentExecutionEnvironment.REMOTE_SANDBOX);

        try {
            executor.execute(call("write_file"));
            Assert.fail("expected approval exception");
        } catch (AgentApprovalRequiredException expected) {
            Assert.assertTrue(expected.getMessage().contains("Tool requires approval"));
            Assert.assertEquals(AgentExecutionEnvironment.REMOTE_SANDBOX, expected.getRequest().getEnvironment());
        }
        Assert.assertEquals(0, delegate.count);
    }

    @Test
    public void shouldExposeEnvironmentToCustomPolicy() throws Exception {
        final List<AgentExecutionEnvironment> seen = new ArrayList<AgentExecutionEnvironment>();
        AgentPermissionPolicy policy = new AgentPermissionPolicy() {
            @Override
            public AgentPermissionDecision evaluate(AgentPermissionRequest request) {
                seen.add(request.getEnvironment());
                return request.getEnvironment() == AgentExecutionEnvironment.REMOTE_SANDBOX
                        ? AgentPermissionDecision.allow()
                        : AgentPermissionDecision.deny("must run remotely");
            }
        };
        CountingToolExecutor delegate = new CountingToolExecutor();
        AgentPermissionToolExecutor executor = new AgentPermissionToolExecutor(
                delegate,
                policy,
                AgentExecutionEnvironment.REMOTE_SANDBOX);

        executor.execute(call("browser"));

        Assert.assertEquals(1, delegate.count);
        Assert.assertEquals(Arrays.asList(AgentExecutionEnvironment.REMOTE_SANDBOX), seen);
    }

    @Test
    public void agentBuilderShouldApplyPermissionPolicyToRuntimeToolCalls() throws Exception {
        CountingToolExecutor delegate = new CountingToolExecutor();
        Deque<AgentModelResult> queue = new ArrayDeque<AgentModelResult>();
        queue.add(resultWithToolCall("call-1", "bash", "{\"command\":\"echo hi\"}"));
        queue.add(resultWithText("done"));

        Agent agent = Agents.react()
                .modelClient(new QueueModelClient(queue))
                .model("test-model")
                .toolExecutor(delegate)
                .permissionPolicy(AgentPermissionPolicies.denyTools(Collections.singleton("bash"), "local shell blocked"))
                .executionEnvironment(AgentExecutionEnvironment.LOCAL)
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("hi").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(0, delegate.count);
        Assert.assertEquals(1, result.getToolResults().size());
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("Tool permission denied"));
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("local shell blocked"));
    }

    private static AgentToolCall call(String name) {
        return AgentToolCall.builder()
                .name(name)
                .arguments("{}")
                .callId("call-1")
                .build();
    }

    private static AgentModelResult resultWithText(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    private static AgentModelResult resultWithToolCall(String callId, String name, String arguments) {
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .callId(callId)
                        .name(name)
                        .arguments(arguments)
                        .type("function_call")
                        .build()))
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private static class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue;

        private QueueModelClient(Deque<AgentModelResult> queue) {
            this.queue = queue;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }
    }

    private static class CountingToolExecutor implements ToolExecutor {
        private int count;

        @Override
        public String execute(AgentToolCall call) {
            count += 1;
            return "{\"ok\":true}";
        }
    }
}

