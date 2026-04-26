package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.script.ScriptEngineManager;
import java.util.Arrays;

public class NashornCodeExecutorTest {

    @Test
    public void test_nashorn_executor_with_tool_alias() throws Exception {
        Assume.assumeTrue("Nashorn is not available", isNashornAvailable());

        NashornCodeExecutor executor = new NashornCodeExecutor();
        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language("js")
                .toolNames(Arrays.asList("echo"))
                .code("var value = echo({text: 'hi'}); __codeact_result = 'ok:' + value;")
                .toolExecutor(new ToolExecutor() {
                    @Override
                    public String execute(AgentToolCall call) {
                        return call.getName() + ":" + call.getArguments();
                    }
                })
                .build());

        Assert.assertTrue(result.isSuccess());
        Assert.assertNotNull(result.getResult());
        Assert.assertTrue(result.getResult().contains("ok:echo:"));
    }

    @Test
    public void test_nashorn_parses_json_tool_result() throws Exception {
        Assume.assumeTrue("Nashorn is not available", isNashornAvailable());

        NashornCodeExecutor executor = new NashornCodeExecutor();
        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language("js")
                .toolNames(Arrays.asList("queryWeather"))
                .code("var data = queryWeather({location:'Beijing',type:'daily',days:1}); __codeact_result = data.results[0].daily[0].text_day;")
                .toolExecutor(new ToolExecutor() {
                    @Override
                    public String execute(AgentToolCall call) {
                        return "\"{\\\"results\\\":[{\\\"daily\\\":[{\\\"text_day\\\":\\\"Sunny\\\"}]}]}\"";
                    }
                })
                .build());

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("Sunny", result.getResult());
    }

    @Test
    public void test_nashorn_uses_return_value_when_codeact_result_not_set() throws Exception {
        Assume.assumeTrue("Nashorn is not available", isNashornAvailable());

        NashornCodeExecutor executor = new NashornCodeExecutor();
        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language("js")
                .code("return 'done';")
                .build());

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("done", result.getResult());
    }

    @Test
    public void test_nashorn_rejects_python_language() throws Exception {
        NashornCodeExecutor executor = new NashornCodeExecutor();
        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language("python")
                .code("print('hello')")
                .build());

        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getError().contains("only javascript is enabled"));
    }

    private boolean isNashornAvailable() {
        return new ScriptEngineManager().getEngineByName("nashorn") != null;
    }
}
