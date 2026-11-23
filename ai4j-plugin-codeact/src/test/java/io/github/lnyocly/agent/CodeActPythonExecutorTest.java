package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.GraalVmCodeExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class CodeActPythonExecutorTest {

    @Test
    public void test_python_executor_with_tool() throws Exception {
        Assume.assumeTrue("GraalPy is not available", isGraalPyAvailable());

        GraalVmCodeExecutor executor = new GraalVmCodeExecutor();
        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language("python")
                .code("result = callTool(\"echo\", {\"text\": \"hi\"})\n__codeact_result = \"ok:\" + result")
                .toolExecutor(new ToolExecutor() {
                    @Override
                    public String execute(AgentToolCall call) {
                        return call.getName() + ":" + call.getArguments();
                    }
                })
                .build());

        Assert.assertTrue(result.isSuccess());
        Assert.assertNotNull(result.getResult());
        Assert.assertTrue(result.getResult().contains("ok:"));
    }

    @Test
    public void test_python_executor_rejects_non_python_language() {
        GraalVmCodeExecutor executor = new GraalVmCodeExecutor();
        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language("javascript")
                .code("return 1;")
                .build());

        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.getError().contains("only python is enabled"));
    }

    private boolean isGraalPyAvailable() {
        try (Context context = Context.newBuilder("python").build()) {
            context.eval("python", "1+1");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
