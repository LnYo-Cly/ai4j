package io.github.lnyocly.ai4j.cli.hook;

import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic tests for {@link CliHookInterceptor} using a fake {@link HookCommandRunner} (no real
 * process spawn — fast, cross-platform). Verifies the exit-code-2 block, JSON block/modify,
 * allow-on-no-decision, fail-closed on hook error, and match filtering.
 */
public class CliHookInterceptorTest {

    private static AgentToolCall bashCall() {
        return AgentToolCall.builder().name("bash").callId("c1").arguments("{\"command\":\"rm -rf x\"}").build();
    }

    private static CliHooksConfig hooks(CliHookEntry... entries) {
        CliHooksConfig c = new CliHooksConfig();
        c.setPreToolUse(new ArrayList<CliHookEntry>(Arrays.asList(entries)));
        return c;
    }

    /** Fake runner: returns a canned result per command, records stdin, optionally throws. */
    static final class FakeRunner implements HookCommandRunner {
        final Map<String, HookCommandResult> byCommand = new HashMap<String, HookCommandResult>();
        final List<String> stdins = new ArrayList<String>();
        Exception toThrow;
        public HookCommandResult run(String command, String stdinJson) throws Exception {
            stdins.add(stdinJson);
            if (toThrow != null) {
                throw toThrow;
            }
            HookCommandResult r = byCommand.get(command);
            return r == null ? new HookCommandResult(0, "", "") : r;
        }
    }

    @Test
    public void exitCode2BlocksAndStdinReceivesToolCallJson() {
        FakeRunner runner = new FakeRunner();
        runner.byCommand.put("guard.sh", new HookCommandRunner.HookCommandResult(2, "", "refusing destructive command"));
        CliHookInterceptor interceptor = new CliHookInterceptor(
                hooks(new CliHookEntry("guard.sh", null)), runner);

        ToolCallDecision d = interceptor.beforeToolCall(bashCall(), null);

        assertEquals(ToolCallDecision.Type.BLOCK, d.getType());
        assertTrue("block reason comes from stderr", d.getReason().contains("refusing destructive command"));
        assertEquals("hook receives one stdin payload", 1, runner.stdins.size());
        assertTrue("stdin carries the tool call json", runner.stdins.get(0).contains("rm -rf x"));
    }

    @Test
    public void exitZeroWithBlockDecisionJsonBlocks() {
        FakeRunner runner = new FakeRunner();
        runner.byCommand.put("guard.sh", new HookCommandRunner.HookCommandResult(0,
                "{\"decision\":\"block\",\"reason\":\"policy says no\"}", ""));
        CliHookInterceptor interceptor = new CliHookInterceptor(
                hooks(new CliHookEntry("guard.sh", null)), runner);

        ToolCallDecision d = interceptor.beforeToolCall(bashCall(), null);
        assertEquals(ToolCallDecision.Type.BLOCK, d.getType());
        assertEquals("policy says no", d.getReason());
    }

    @Test
    public void exitZeroWithModifyDecisionRewritesTheCall() {
        FakeRunner runner = new FakeRunner();
        runner.byCommand.put("rewrite.sh", new HookCommandRunner.HookCommandResult(0,
                "{\"decision\":\"modify\",\"name\":\"bash\",\"arguments\":\"{\\\"command\\\":\\\"echo safe\\\"}\"}", ""));
        CliHookInterceptor interceptor = new CliHookInterceptor(
                hooks(new CliHookEntry("rewrite.sh", null)), runner);

        ToolCallDecision d = interceptor.beforeToolCall(bashCall(), null);
        assertEquals(ToolCallDecision.Type.MODIFY, d.getType());
        assertEquals("bash", d.getModifiedCall().getName());
        assertTrue(d.getModifiedCall().getArguments().contains("echo safe"));
    }

    @Test
    public void exitZeroWithNoDecisionAllows() {
        FakeRunner runner = new FakeRunner();
        runner.byCommand.put("noop.sh", new HookCommandRunner.HookCommandResult(0, "ok", ""));
        CliHookInterceptor interceptor = new CliHookInterceptor(
                hooks(new CliHookEntry("noop.sh", null)), runner);

        assertEquals(ToolCallDecision.Type.ALLOW, interceptor.beforeToolCall(bashCall(), null).getType());
    }

    @Test
    public void hookThatThrowsBlocksFailClosed() {
        FakeRunner runner = new FakeRunner();
        runner.toThrow = new RuntimeException("command not found");
        CliHookInterceptor interceptor = new CliHookInterceptor(
                hooks(new CliHookEntry("broken.sh", null)), runner);

        ToolCallDecision d = interceptor.beforeToolCall(bashCall(), null);
        assertEquals("a crashed safety hook must fail closed", ToolCallDecision.Type.BLOCK, d.getType());
        assertTrue(d.getReason().contains("command not found"));
    }

    @Test
    public void matchFiltersHooksByToolName() {
        FakeRunner runner = new FakeRunner();
        runner.byCommand.put("guard-bash.sh", new HookCommandRunner.HookCommandResult(2, "", "blocked"));
        CliHookInterceptor interceptor = new CliHookInterceptor(
                hooks(new CliHookEntry("guard-bash.sh", "bash")), runner);

        // matches bash -> block
        assertEquals(ToolCallDecision.Type.BLOCK, interceptor.beforeToolCall(bashCall(), null).getType());
        // does not match another tool -> allow, and the hook is not run
        AgentToolCall readCall = AgentToolCall.builder().name("read_file").callId("c2").arguments("{}").build();
        runner.stdins.clear();
        ToolCallDecision d = interceptor.beforeToolCall(readCall, null);
        assertEquals(ToolCallDecision.Type.ALLOW, d.getType());
        assertEquals("non-matching hook must not run", 0, runner.stdins.size());
    }

    @Test
    public void noHooksAllowsEverything() {
        CliHooksConfig empty = new CliHooksConfig();
        empty.setPreToolUse(Collections.<CliHookEntry>emptyList());
        CliHookInterceptor interceptor = new CliHookInterceptor(empty, new FakeRunner());
        assertEquals(ToolCallDecision.Type.ALLOW, interceptor.beforeToolCall(bashCall(), null).getType());
    }

    @Test
    public void realProcessExit2BlocksAndExit0Allows() throws Exception {
        // one smoke test for ProcessHookCommandRunner itself (cross-platform: sh -c / cmd /c)
        ProcessHookCommandRunner runner = new ProcessHookCommandRunner();
        // exit 2 -> block
        CliHooksConfig blocking = hooks(new CliHookEntry(
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "exit 2" : "exit 2", null));
        assertEquals(ToolCallDecision.Type.BLOCK,
                new CliHookInterceptor(blocking, runner).beforeToolCall(bashCall(), null).getType());
        // exit 0 -> allow
        CliHooksConfig allowing = hooks(new CliHookEntry("exit 0", null));
        assertEquals(ToolCallDecision.Type.ALLOW,
                new CliHookInterceptor(allowing, runner).beforeToolCall(bashCall(), null).getType());
    }
}
