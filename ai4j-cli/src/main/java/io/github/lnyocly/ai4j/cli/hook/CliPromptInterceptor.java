package io.github.lnyocly.ai4j.cli.hook;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.interceptor.PromptDecision;
import io.github.lnyocly.ai4j.agent.interceptor.PromptInterceptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges the in-process {@link PromptInterceptor} to external shell commands — the Claude-Code
 * "UserPromptSubmit" end-user hook. Mirrors {@link CliHookInterceptor}: for each configured
 * {@code userPromptSubmit} command, runs it with the prompt JSON on stdin and maps the outcome to a
 * {@link PromptDecision}:
 *
 * <ul>
 *   <li><b>exit 2</b> &rarr; {@link PromptDecision#block block} (reason = stderr/stdout).</li>
 *   <li><b>exit 0 + stdout JSON</b> {@code {"decision":"block","reason":"..."}} &rarr; block.</li>
 *   <li><b>exit 0 + stdout JSON</b> {@code {"decision":"modify","input":"..."}} &rarr; modify.</li>
 *   <li><b>exit 0 / other</b> &rarr; continue to the next hook (allow so far).</li>
 *   <li><b>hook crashes</b> &rarr; fail-closed block.</li>
 * </ul>
 *
 * <p>First block wins; else first modify wins; else allow. The {@code match} field on
 * {@link CliHookEntry} is ignored for prompt hooks (all prompts match).</p>
 */
public class CliPromptInterceptor implements PromptInterceptor {

    private final CliHooksConfig config;
    private final HookCommandRunner runner;

    public CliPromptInterceptor(CliHooksConfig config, HookCommandRunner runner) {
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
    public PromptDecision beforePrompt(String input, AgentContext context) {
        List<CliHookEntry> hooks = config.getUserPromptSubmit();
        for (CliHookEntry hook : hooks) {
            if (hook == null) {
                continue;
            }
            PromptDecision decision = runOne(hook, input);
            if (decision != null) {
                return decision;
            }
        }
        return PromptDecision.allow();
    }

    private PromptDecision runOne(CliHookEntry hook, String input) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("input", input == null ? "" : input);
        String stdinJson = JSON.toJSONString(payload);
        HookCommandRunner.HookCommandResult result;
        try {
            result = runner.run(hook.getCommand(), stdinJson);
        } catch (Exception e) {
            return PromptDecision.block("hook '" + hook.getCommand() + "' failed: " + safeMessage(e));
        }
        if (result.exitCode == 2) {
            return PromptDecision.block(reasonFrom(result));
        }
        if (result.exitCode != 0) {
            return null; // soft error: continue
        }
        JSONObject json = parseJson(result.stdout);
        if (json != null) {
            String decision = json.getString("decision");
            if ("block".equalsIgnoreCase(decision)) {
                return PromptDecision.block(json.getString("reason"));
            }
            if ("modify".equalsIgnoreCase(decision)) {
                String modified = json.getString("input");
                if (modified != null) {
                    return PromptDecision.modify(modified);
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
        return reason.isEmpty() ? "blocked by prompt hook" : reason;
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
