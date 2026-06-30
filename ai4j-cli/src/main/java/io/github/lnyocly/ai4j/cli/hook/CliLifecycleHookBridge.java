package io.github.lnyocly.ai4j.cli.hook;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;

import java.util.List;

/**
 * Bridges observe-only lifecycle events to external shell commands — covers the Claude-Code events
 * that are side-effects, not decisions: {@code stop} (AFTER_TURN), {@code preCompact} (ON_COMPACT),
 * {@code sessionStart}/{@code sessionEnd}. One component, config-driven, so adding observe events
 * is a config-line, not new code.
 *
 * <p>Registered as an {@link AgentLifecycleHook} (now possible directly via
 * {@code AgentBuilder.lifecycleHook(...)}). Side-effect only: commands run with the event JSON on
 * stdin, output is ignored, and errors are swallowed — an observe hook must never break the run.</p>
 *
 * <p>PostToolUse is NOT here: it's an interception event (can block feedback) and lives on
 * {@link CliHookInterceptor#afterToolCall}.</p>
 */
public class CliLifecycleHookBridge implements AgentLifecycleHook {

    private final CliHooksConfig config;
    private final HookCommandRunner runner;

    public CliLifecycleHookBridge(CliHooksConfig config, HookCommandRunner runner) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.config = config;
        this.runner = runner;
    }

    @Override
    public String name() {
        return "cli-lifecycle-hook-bridge";
    }

    @Override
    public void onEvent(AgentLifecycleEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        List<CliHookEntry> hooks = hooksFor(event.getType());
        if (hooks == null || hooks.isEmpty()) {
            return;
        }
        String stdinJson = JSON.toJSONString(event);
        for (CliHookEntry hook : hooks) {
            if (hook == null || hook.getCommand() == null || hook.getCommand().trim().isEmpty()) {
                continue;
            }
            try {
                runner.run(hook.getCommand(), stdinJson);
            } catch (Exception ignored) {
                // observe hooks must not break the run; swallow
            }
        }
    }

    private List<CliHookEntry> hooksFor(AgentLifecycleEventType type) {
        switch (type) {
            case AFTER_TURN:
                return config.getStop();
            case ON_COMPACT:
                return config.getPreCompact();
            case SESSION_START:
                return config.getSessionStart();
            case SESSION_END:
                return config.getSessionEnd();
            default:
                return null;
        }
    }
}
