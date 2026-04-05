package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.net.SocketTimeoutException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class CodeActAgentUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_codeact_basic_js_execution() throws Exception {
        // CodeAct 在 JDK8 场景优先使用 Nashorn，保证 JavaScript 执行链路可用
        Assume.assumeTrue(isNashornAvailable());

        Agent agent = Agents.codeAct()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .maxOutputTokens(512)
                .codeExecutor(new NashornCodeExecutor())
                .systemPrompt("你是代码执行助手。只允许输出 JSON。优先使用 JavaScript 计算并返回最终结果。")
                .codeActOptions(CodeActOptions.builder().reAct(false).build())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = runWithRetry(agent, AgentRequest.builder().input("请用JavaScript计算 17*3+5 ，只返回最终数字").build(), 2);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
        Assert.assertFalse(result.getOutputText().contains("CODE_ERROR"));
    }

    @Test
    public void test_codeact_react_finalize_flow_without_external_tool() throws Exception {
        // CodeAct ReAct 模式：先输出代码块，再进入 final 输出
        Assume.assumeTrue(isNashornAvailable());

        Agent agent = Agents.codeAct()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .maxOutputTokens(512)
                .codeExecutor(new NashornCodeExecutor())
                .systemPrompt("你是代码执行助手。先输出可执行 JavaScript，再输出最终结果。保持简短。")
                .instructions("用 JavaScript 计算 88 除以 11。")
                .codeActOptions(CodeActOptions.builder().reAct(true).build())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = runWithRetry(agent, AgentRequest.builder().input("请先写代码再返回最终结果，不要解释").build(), 2);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getOutputText());
        Assert.assertTrue(result.getOutputText().trim().length() > 0);
        Assert.assertFalse(result.getOutputText().contains("CODE_ERROR"));
    }

    private boolean isNashornAvailable() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        return engine != null;
    }

    private AgentResult runWithRetry(Agent agent, AgentRequest request, int maxAttempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return callWithProviderGuard(() -> agent.run(request));
            } catch (Exception ex) {
                last = ex;
                if (i + 1 >= maxAttempts || !isRetryable(ex)) {
                    throw ex;
                }
            }
        }
        throw last == null ? new RuntimeException("CodeAct run failed without exception detail") : last;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
