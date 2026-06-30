package io.github.lnyocly.ai4j.cli.hook;

import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic tests for {@link CliLifecycleHookBridge} — the generic observe bridge that covers
 * stop / preCompact / sessionStart / sessionEnd via the lifecycle hook. Verifies each event maps to
 * the right configured command, unrelated events are skipped, and a crashing command is swallowed.
 */
public class CliLifecycleHookBridgeTest {

    private static CliHooksConfig config(String key, String command) {
        CliHooksConfig c = new CliHooksConfig();
        CliHookEntry entry = new CliHookEntry(command, null);
        switch (key) {
            case "stop": c.setStop(Collections.singletonList(entry)); break;
            case "preCompact": c.setPreCompact(Collections.singletonList(entry)); break;
            case "sessionStart": c.setSessionStart(Collections.singletonList(entry)); break;
            case "sessionEnd": c.setSessionEnd(Collections.singletonList(entry)); break;
            default: throw new IllegalArgumentException(key);
        }
        return c;
    }

    private static AgentLifecycleEvent event(AgentLifecycleEventType type) {
        return AgentLifecycleEvent.builder(type).runtime("test").step(0).build();
    }

    @Test
    public void afterTurnMapsToStopCommand() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("on-stop.sh", new HookCommandRunner.HookCommandResult(0, "ok", ""));
        new CliLifecycleHookBridge(config("stop", "on-stop.sh"), runner).onEvent(event(AgentLifecycleEventType.AFTER_TURN));

        assertEquals(1, runner.stdins.size());
        assertTrue(runner.stdins.get(0).contains("AFTER_TURN"));
    }

    @Test
    public void eachEventMapsToItsOwnCommand() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("compact.sh", new HookCommandRunner.HookCommandResult(0, "", ""));
        runner.byCommand.put("start.sh", new HookCommandRunner.HookCommandResult(0, "", ""));
        CliHooksConfig c = new CliHooksConfig();
        c.setPreCompact(Collections.singletonList(new CliHookEntry("compact.sh", null)));
        c.setSessionStart(Collections.singletonList(new CliHookEntry("start.sh", null)));
        CliLifecycleHookBridge bridge = new CliLifecycleHookBridge(c, runner);

        bridge.onEvent(event(AgentLifecycleEventType.ON_COMPACT));
        bridge.onEvent(event(AgentLifecycleEventType.SESSION_START));
        // SESSION_END has no config -> no command
        bridge.onEvent(event(AgentLifecycleEventType.SESSION_END));

        assertEquals(2, runner.stdins.size());
    }

    @Test
    public void unrelatedEventsAreSkipped() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.byCommand.put("on-stop.sh", new HookCommandRunner.HookCommandResult(0, "", ""));
        new CliLifecycleHookBridge(config("stop", "on-stop.sh"), runner)
                .onEvent(event(AgentLifecycleEventType.BEFORE_MODEL_REQUEST));
        assertEquals("events without configured hooks must not run a command", 0, runner.stdins.size());
    }

    @Test
    public void crashingCommandIsSwallowed() {
        CliHookInterceptorTest.FakeRunner runner = new CliHookInterceptorTest.FakeRunner();
        runner.toThrow = new RuntimeException("boom");
        // must not throw — an observe hook must not break the run
        new CliLifecycleHookBridge(config("stop", "broken.sh"), runner).onEvent(event(AgentLifecycleEventType.AFTER_TURN));
        assertEquals(1, runner.stdins.size());
    }
}
