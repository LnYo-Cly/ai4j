package io.github.lnyocly.ai4j.cli.hook;

import io.github.lnyocly.ai4j.agent.interceptor.PromptDecision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic tests for {@link CliPromptInterceptor} (UserPromptSubmit external-command bridge)
 * using the fake runner from {@link CliHookInterceptorTest}. Mirrors the tool-hook bridge tests.
 */
public class CliPromptInterceptorTest {

    private static CliHooksConfig promptHooks(String command) {
        CliHooksConfig c = new CliHooksConfig();
        c.setUserPromptSubmit(java.util.Collections.singletonList(new CliHookEntry(command, null)));
        return c;
    }

    @Test
    public void exitCode2BlocksAndStdinReceivesPromptJson() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("guard.sh", new HookCommandRunner.HookCommandResult(2, "", "prompt not allowed"));
        CliPromptInterceptor interceptor = new CliPromptInterceptor(promptHooks("guard.sh"), runner);

        PromptDecision d = interceptor.beforePrompt("drop table users", null);

        assertEquals(PromptDecision.Type.BLOCK, d.getType());
        assertTrue(d.getReason().contains("prompt not allowed"));
        assertEquals(1, runner.stdins.size());
        assertTrue("stdin carries the prompt json", runner.stdins.get(0).contains("drop table users"));
    }

    @Test
    public void exitZeroWithBlockDecisionJsonBlocks() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("guard.sh", new HookCommandRunner.HookCommandResult(0,
                "{\"decision\":\"block\",\"reason\":\"sql injection\"}", ""));
        PromptDecision d = new CliPromptInterceptor(promptHooks("guard.sh"), runner).beforePrompt("x", null);
        assertEquals(PromptDecision.Type.BLOCK, d.getType());
        assertEquals("sql injection", d.getReason());
    }

    @Test
    public void exitZeroWithModifyDecisionRewritesPrompt() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("norm.sh", new HookCommandRunner.HookCommandResult(0,
                "{\"decision\":\"modify\",\"input\":\"sanitized prompt\"}", ""));
        PromptDecision d = new CliPromptInterceptor(promptHooks("norm.sh"), runner).beforePrompt("x", null);
        assertEquals(PromptDecision.Type.MODIFY, d.getType());
        assertEquals("sanitized prompt", d.getModifiedInput());
    }

    @Test
    public void exitZeroWithNoDecisionAllows() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("noop.sh", new HookCommandRunner.HookCommandResult(0, "ok", ""));
        PromptDecision d = new CliPromptInterceptor(promptHooks("noop.sh"), runner).beforePrompt("hello", null);
        assertEquals(PromptDecision.Type.ALLOW, d.getType());
    }

    @Test
    public void hookThatThrowsBlocksFailClosed() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.toThrow = new RuntimeException("command not found");
        PromptDecision d = new CliPromptInterceptor(promptHooks("broken.sh"), runner).beforePrompt("hello", null);
        assertEquals(PromptDecision.Type.BLOCK, d.getType());
        assertTrue(d.getReason().contains("command not found"));
    }
}
