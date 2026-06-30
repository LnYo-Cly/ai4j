package io.github.lnyocly.ai4j.cli.hook;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.List;

/**
 * Bridges the in-process {@link ToolInterceptor} to external shell commands — the Claude-Code-style
 * end-user hook experience. On each tool call, for every matching {@code preToolUse} hook, it runs
 * the user's command with the tool-call JSON on stdin and maps the outcome to a decision:
 *
 * <ul>
 *   <li><b>exit 2</b> &rarr; {@link ToolCallDecision#block block} (reason = stderr/stdout). This is
 *       Claude Code's PreToolUse exit-code-2 deny.</li>
 *   <li><b>exit 0 + stdout JSON</b> {@code {"decision":"block","reason":"..."}} &rarr; block.</li>
 *   <li><b>exit 0 + stdout JSON</b> {@code {"decision":"modify","name":"...","arguments":"..."}} &rarr; modify.</li>
 *   <li><b>exit 0 / other</b> &rarr; continue to the next hook (allow so far).</li>
 *   <li><b>hook crashes</b> &rarr; fail-closed: block-with-reason (a safety hook that errors must
 *       not let the tool run).</li>
 * </ul>
 *
 * <p>First block wins; else first modify wins; else allow. Process spawning is delegated to a
 * {@link HookCommandRunner} so the decision logic is testable with a fake runner.</p>
 */
public class CliHookInterceptor implements ToolInterceptor {

    private final CliHooksConfig config;
    private final HookCommandRunner runner;

    public CliHookInterceptor(CliHooksConfig config, HookCommandRunner runner) {
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
    public ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context) {
        String toolName = call == null ? null : call.getName();
        List<CliHookEntry> hooks = config.getPreToolUse();
        for (CliHookEntry hook : hooks) {
            if (hook == null || !hook.matches(toolName)) {
                continue;
            }
            ToolCallDecision decision = runOne(hook, call);
            if (decision != null) {
                return decision;
            }
        }
        return ToolCallDecision.allow();
    }

    /** Runs one hook; returns a non-null decision to short-circuit, or null to continue. */
    private ToolCallDecision runOne(CliHookEntry hook, AgentToolCall call) {
        String stdinJson = call == null ? "{}" : JSON.toJSONString(call);
        HookCommandRunner.HookCommandResult result;
        try {
            result = runner.run(hook.getCommand(), stdinJson);
        } catch (Exception e) {
            // fail-closed: a hook that errors blocks the tool
            return ToolCallDecision.block("hook '" + hook.getCommand() + "' failed: " + safeMessage(e));
        }
        if (result.exitCode == 2) {
            return ToolCallDecision.block(reasonFrom(result));
        }
        if (result.exitCode != 0) {
            // soft error (like Claude Code exit 1): continue, don't block
            return null;
        }
        // exit 0: inspect stdout JSON for a structured decision
        JSONObject json = parseJson(result.stdout);
        if (json != null) {
            String decision = json.getString("decision");
            if ("block".equalsIgnoreCase(decision)) {
                return ToolCallDecision.block(json.getString("reason"));
            }
            if ("modify".equalsIgnoreCase(decision)) {
                String name = json.getString("name");
                String args = json.getString("arguments");
                if (name != null) {
                    return ToolCallDecision.modify(AgentToolCall.builder()
                            .name(name)
                            .arguments(args)
                            .callId(call == null ? null : call.getCallId())
                            .build());
                }
            }
        }
        return null;
    }

    private static String reasonFrom(HookCommandRunner.HookCommandResult result) {
        String reason = result.stderr == null ? "" : result.stderr.trim();
        if (reason.isEmpty()) {
            reason = result.stdout == null ? "" : result.stdout.trim();
        }
        return reason.isEmpty() ? "blocked by hook" : reason;
    }

    private static JSONObject parseJson(String stdout) {
        if (stdout == null || stdout.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(stdout);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeMessage(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
