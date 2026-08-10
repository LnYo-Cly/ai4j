package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.Assert;
import org.junit.Test;

import javax.script.ScriptEngineManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * Executable source of truth for the snippet in
 * {@code docs/agent/codeact-runtime.md}.
 *
 * <p>Inline scripted client emits a code message then a final message,
 * exercising the code→execute→finalize loop with zero network.
 * Skips when Nashorn is unavailable (JDK 16+).
 */
public class CodeActDocExamplesTest {

    @Test
    public void codeActDirectOutputFromExecutedCode() throws Exception {
        org.junit.Assume.assumeTrue("Nashorn required", isNashornAvailable());

        // reAct=false：模型发一段代码，执行结果直接作为输出（无需二次收尾）
        ScriptedClient client = new ScriptedClient();
        client.enqueue(codeMessage("javascript", "return 17*3+5")); // 执行得 56

        Agent agent = Agents.codeAct()
                .modelClient(client)
                .model("test-model")
                .codeExecutor(new NashornCodeExecutor())
                .systemPrompt("你是代码执行助手。输出 {type:code,language,code} 让执行器跑。")
                .codeActOptions(CodeActOptions.builder().reAct(false).build())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder()
                .input("请用 JavaScript 计算 17*3+5").build());

        Assert.assertNotNull(result);
        Assert.assertEquals("执行结果应直接作为输出", "56", result.getOutputText().trim());
    }

    @Test
    public void codeActReActModeFinalizesWithSecondCall() throws Exception {
        org.junit.Assume.assumeTrue("Nashorn required", isNashornAvailable());

        // reAct=true：第一轮发代码 → 执行 → 第二轮模型用 type=final 收尾
        ScriptedClient client = new ScriptedClient();
        client.enqueue(codeMessage("javascript", "return 88/11"));   // 执行得 8
        client.enqueue(finalMessage("88 除以 11 等于 8"));

        Agent agent = Agents.codeAct()
                .modelClient(client)
                .model("test-model")
                .codeExecutor(new NashornCodeExecutor())
                .systemPrompt("先输出代码，再用 type=final 收尾。")
                .codeActOptions(CodeActOptions.builder().reAct(true).build())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder()
                .input("用 JavaScript 计算 88 除以 11，再给出最终结果").build());

        Assert.assertNotNull(result);
        Assert.assertEquals("reAct 模式应走到 final 收尾", "88 除以 11 等于 8", result.getOutputText());
    }

    private static boolean isNashornAvailable() {
        try {
            return new ScriptEngineManager().getEngineByName("nashorn") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static AgentModelResult codeMessage(String language, String code) {
        // CodeAct 协议：模型输出 JSON，type=code 触发执行器
        String json = "{\"type\":\"code\",\"language\":\"" + language + "\",\"code\":\"" + code + "\"}";
        return AgentModelResult.builder()
                .outputText(json)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<>())
                .build();
    }

    private static AgentModelResult finalMessage(String output) {
        String json = "{\"type\":\"final\",\"output\":\"" + output + "\"}";
        return AgentModelResult.builder()
                .outputText(json)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<>())
                .build();
    }

    static final class ScriptedClient implements AgentModelClient {
        final Deque<AgentModelResult> queue = new ArrayDeque<AgentModelResult>();

        void enqueue(AgentModelResult r) { queue.addLast(r); }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
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
