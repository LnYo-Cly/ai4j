package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.HandoffFailureAction;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Executable source of truth for the snippets in
 * {@code docs/agent/subagent-handoff-policy.md}.
 *
 * <p>Uses an inline scripted model client so the handoff wiring and
 * HandoffPolicy behaviour are exercised with zero network. Runs in CI.
 */
public class SubAgentHandoffDocExamplesTest {

    // ---- §3 把 SubAgent 装进 parent ----

    @Test
    public void subAgentRegisteredAsAToolAndDelegated() throws Exception {
        ScriptedClient subClient = new ScriptedClient();
        subClient.enqueue(text("review-ready"));
        Agent reviewer = Agents.react().modelClient(subClient).model("reviewer").build();

        SubAgentDefinition reviewerSub = SubAgentDefinition.builder()
                .name("code-reviewer")
                .description("Review code quality and risks")
                .toolName("delegate_code_review")
                .agent(reviewer)
                .build();

        ScriptedClient parentClient = new ScriptedClient();
        parentClient.enqueue(toolCall("call_1", "delegate_code_review", "{\"task\":\"review auth\"}"));
        parentClient.enqueue(text("final-answer"));

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("manager")
                .subAgent(reviewerSub)
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("analyze").build());

        Assert.assertEquals("final-answer", result.getOutputText());
        Assert.assertEquals("subagent 的输出应回流给 parent", 1, result.getToolResults().size());
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("review-ready"));
    }

    // ---- §10/§11 HandoffPolicy：限制工具面 + 失败回退 ----

    @Test
    public void handoffPolicyAllowedToolsDeniesUnlistedToolByDefault() throws Exception {
        ScriptedClient subClient = new ScriptedClient();
        Agent sub = Agents.react().modelClient(subClient).model("sub").build();

        SubAgentDefinition def = SubAgentDefinition.builder()
                .name("worker").description("d").toolName("delegate_worker").agent(sub).build();

        ScriptedClient parentClient = new ScriptedClient();
        parentClient.enqueue(toolCall("c1", "delegate_worker", "{}"));
        parentClient.enqueue(text("done"));

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("parent")
                .subAgent(def)
                .handoffPolicy(HandoffPolicy.builder()
                        .allowedTools(Collections.singleton("delegate_other"))  // 不含 delegate_worker
                        .build())
                .build();

        // 默认 onDenied=FAIL：delegate_worker 不在 allowedTools 里，handoff 被拒绝并抛异常
        try {
            parent.run(AgentRequest.builder().input("go").build());
            Assert.fail("expected HandoffPolicy denial");
        } catch (RuntimeException e) {
            Assert.assertTrue("应说明被 allowedTools 拒绝: " + e.getMessage(),
                    e.getMessage().contains("allowedTools"));
        }
    }

    @Test
    public void fallbackToPrimaryKeepsParentAliveWhenSubAgentDenied() throws Exception {
        ScriptedClient subClient = new ScriptedClient();
        Agent sub = Agents.react().modelClient(subClient).model("sub").build();

        SubAgentDefinition def = SubAgentDefinition.builder()
                .name("worker").description("d").toolName("delegate_worker").agent(sub).build();

        ScriptedClient parentClient = new ScriptedClient();
        parentClient.enqueue(toolCall("c1", "delegate_worker", "{}"));
        parentClient.enqueue(text("parent-handled"));

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("parent")
                .subAgent(def)
                .handoffPolicy(HandoffPolicy.builder()
                        .allowedTools(Collections.singleton("delegate_other"))
                        .onDenied(HandoffFailureAction.FALLBACK_TO_PRIMARY)  // 拒绝后回退给主 agent
                        .build())
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("go").build());
        Assert.assertEquals("回退后 parent 自己完成", "parent-handled", result.getOutputText());
    }

    // ---- §10 maxDepth 防止递归 handoff ----

    @Test
    public void maxDepthBlocksRecursiveHandoff() throws Exception {
        ScriptedClient leafClient = new ScriptedClient();
        leafClient.enqueue(text("leaf-out"));
        Agent leaf = Agents.react().modelClient(leafClient).model("leaf").build();
        SubAgentDefinition leafDef = SubAgentDefinition.builder()
                .name("leaf").description("d").toolName("delegate_leaf").agent(leaf).build();

        // middle 想再 delegate 给 leaf，但 maxDepth=1 会挡住
        ScriptedClient middleClient = new ScriptedClient();
        middleClient.enqueue(toolCall("c1", "delegate_leaf", "{}"));
        middleClient.enqueue(text("middle-out"));
        Agent middle = Agents.react()
                .modelClient(middleClient).model("middle")
                .subAgent(leafDef)
                .handoffPolicy(HandoffPolicy.builder().maxDepth(1).build())
                .build();
        SubAgentDefinition middleDef = SubAgentDefinition.builder()
                .name("middle").description("d").toolName("delegate_middle").agent(middle).build();

        ScriptedClient parentClient = new ScriptedClient();
        parentClient.enqueue(toolCall("c1", "delegate_middle", "{}"));
        parentClient.enqueue(text("parent-out"));

        Agent parent = Agents.react()
                .modelClient(parentClient).model("parent")
                .subAgent(middleDef)
                .handoffPolicy(HandoffPolicy.builder().maxDepth(1).build())
                .build();

        // parent→middle（depth 1）允许；middle→leaf（depth 2）超过 maxDepth 被拒绝
        try {
            parent.run(AgentRequest.builder().input("go").build());
            Assert.fail("expected maxDepth denial");
        } catch (RuntimeException e) {
            Assert.assertTrue("应说明超过 maxDepth: " + e.getMessage(),
                    e.getMessage().contains("maxDepth"));
        }
    }

    // ---- 极简 scripted client（不依赖测试包里的 helper）----

    private static AgentModelResult text(String t) {
        return AgentModelResult.builder()
                .outputText(t)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    private static AgentModelResult toolCall(String callId, String name, String args) {
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .callId(callId).name(name).arguments(args).type("function_call").build()))
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    static final class ScriptedClient implements AgentModelClient {
        final Deque<AgentModelResult> queue = new ConcurrentLinkedDeque<AgentModelResult>();
        final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();

        void enqueue(AgentModelResult r) { queue.addLast(r); }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            AgentModelResult r = queue.pollFirst();
            if (r == null) throw new IllegalStateException("scripted client exhausted");
            return r;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
