package io.github.lnyocly;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.interceptor.AgentHooks;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Real end-to-end agent demo: GLM via Anthropic Messages + a calculate tool + hooks facade.
 * Exercises: real LLM call, tool calling, hooks (preToolUse + stop observe), full agent loop.
 */
public class RealAgentDemo {

    public static void main(String[] args) throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY env var is required (set it to your GLM/coding-plan key)");
        }
        String baseUrl = "https://open.bigmodel.cn/api/anthropic/";
        String model = "glm-5.1";

        // 1. define a "calculate" tool
        Tool.Function fn = new Tool.Function();
        fn.setName("calculate");
        fn.setDescription("Evaluate a math expression and return the result. Input: {\"expression\": \"2+3*4\"}");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        Tool.Function.Property exprProp = new Tool.Function.Property();
        exprProp.setType("string");
        exprProp.setDescription("The math expression to evaluate");
        props.put("expression", exprProp);
        param.setProperties(props);
        param.setRequired(Collections.singletonList("expression"));
        fn.setParameters(param);
        AgentToolRegistry registry = new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));

        // 2. tool executor — actually evaluates the expression (sandbox: only digits and operators)
        ToolExecutor executor = call -> {
            String expr = com.alibaba.fastjson2.JSON.parseObject(call.getArguments()).getString("expression");
            // simple safe evaluator: only allow digits, +, -, *, /, ., space, parentheses
            String safe = expr.replaceAll("[^0-9+\\-*/. ()]", "");
            if (safe.isEmpty() || !safe.equals(expr.trim())) {
                return "Error: expression contains unsafe characters. Only digits and + - * / ( ) allowed.";
            }
            try {
                javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
                javax.script.ScriptEngine engine = mgr.getEngineByName("JavaScript");
                if (engine == null) {
                    // fallback: manual eval for simple cases
                    return "Result: " + simpleEval(safe);
                }
                Object result = engine.eval(safe);
                return "Result: " + result;
            } catch (Exception e) {
                return "Error evaluating: " + e.getMessage();
            }
        };

        // 3. build agent with hooks facade — observe the full lifecycle
        System.out.println("=== Building agent: " + model + " via " + baseUrl + " ===\n");

        Agent agent = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .maxOutputTokens(1024)
                .toolRegistry(registry)
                .toolExecutor(executor)
                .hooks(h -> h
                        .preToolUse((call, ctx) -> {
                            System.out.println("  [HOOK preToolUse] tool=" + call.getName()
                                    + " args=" + call.getArguments());
                            return ToolCallDecision.allow();
                        })
                        .stop(ev -> System.out.println("  [HOOK stop] turn ended, step=" + ev.getStep()))
                        .sessionStart(ev -> System.out.println("  [HOOK sessionStart] session started")))
                .build();

        // 4. run a real task that should trigger tool calling
        String task = "I have a rectangle with width 12.5 and height 8.3. "
                + "Use the calculate tool to compute the area, then tell me the result.";

        System.out.println("=== Task: " + task + " ===\n");
        System.out.println("--- Agent run starting ---");

        long start = System.currentTimeMillis();
        AgentResult result = agent.newSession().run(task);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("\n--- Agent run finished (" + elapsed + "ms) ---\n");

        // 5. print the result
        System.out.println("=== OUTPUT ===");
        System.out.println(result.getOutputText());
        System.out.println("\n=== METRICS ===");
        System.out.println("Steps: " + result.getSteps());
        System.out.println("Tool calls: " + (result.getToolCalls() == null ? 0 : result.getToolCalls().size()));
        System.out.println("Run ID: " + result.getRunId());

        if (result.getInputTokens() != null) {
            System.out.println("Input tokens: " + result.getInputTokens());
        }
        if (result.getOutputTokens() != null) {
            System.out.println("Output tokens: " + result.getOutputTokens());
        }

        System.out.println("\n=== DEMO COMPLETE ===");
    }

    /**
     * Ultra-simple arithmetic evaluator (fallback when no JS engine).
     * Handles +, -, *, / with left-to-right precedence (not mathematically correct
     * for * / but sufficient for a demo).
     */
    private static double simpleEval(String expr) {
        // Just use doubles and basic tokenization
        expr = expr.replaceAll("\\s+", "");
        double result = 0;
        char op = '+';
        int i = 0;
        while (i < expr.length()) {
            if (expr.charAt(i) == '(') {
                int depth = 1; int j = i + 1;
                while (j < expr.length() && depth > 0) {
                    if (expr.charAt(j) == '(') depth++;
                    if (expr.charAt(j) == ')') depth--;
                    j++;
                }
                double sub = simpleEval(expr.substring(i + 1, j - 1));
                result = applyOp(result, sub, op);
                i = j;
            } else if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.') {
                int j = i;
                while (j < expr.length() && (Character.isDigit(expr.charAt(j)) || expr.charAt(j) == '.')) j++;
                double num = Double.parseDouble(expr.substring(i, j));
                result = applyOp(result, num, op);
                i = j;
            } else if (expr.charAt(i) == '+' || expr.charAt(i) == '-' || expr.charAt(i) == '*' || expr.charAt(i) == '/') {
                op = expr.charAt(i);
                i++;
            } else {
                i++;
            }
        }
        return result;
    }

    private static double applyOp(double a, double b, char op) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/': return b == 0 ? 0 : a / b;
            default: return b;
        }
    }
}
