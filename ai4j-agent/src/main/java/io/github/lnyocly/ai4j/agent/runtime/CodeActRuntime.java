package io.github.lnyocly.ai4j.agent.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.List;

public class CodeActRuntime extends BaseAgentRuntime {

    private static final String CODE_CALL_ID = "code_execution";

    @Override
    protected String runtimeName() {
        return "codeact";
    }

    @Override
    public AgentResult run(AgentContext context, AgentRequest request) throws Exception {
        return runInternal(context, request, null);
    }

    @Override
    public void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        runInternal(context, request, listener);
    }

    protected AgentResult runInternal(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        AgentOptions options = context.getOptions();
        int maxSteps = options == null ? 0 : options.getMaxSteps();
        CodeActOptions codeActOptions = context.getCodeActOptions();
        boolean reAct = codeActOptions != null && codeActOptions.isReAct();

        AgentMemory memory = context.getMemory();
        if (memory == null) {
            throw new IllegalStateException("memory is required");
        }
        if (request != null && request.getInput() != null) {
            memory.addUserInput(request.getInput());
        }

        CodeExecutor codeExecutor = context.getCodeExecutor();
        if (codeExecutor == null) {
            throw new IllegalStateException("codeExecutor is required");
        }

        List<AgentToolCall> toolCalls = new ArrayList<>();
        List<AgentToolResult> toolResults = new ArrayList<>();
        AgentModelResult lastResult = null;
        boolean finalizeRequested = false;

        int step = 0;
        boolean stepLimited = maxSteps > 0;
        while (!stepLimited || step < maxSteps) {
            publish(context, listener, AgentEventType.STEP_START, step, runtimeName(), null);

            AgentPrompt prompt = buildPrompt(context, memory, false);
            AgentModelResult modelResult = executeModel(context, prompt, listener, step, false);
            lastResult = modelResult;

            if (modelResult != null && modelResult.getMemoryItems() != null) {
                memory.addOutputItems(modelResult.getMemoryItems());
            }

            String output = modelResult == null ? null : modelResult.getOutputText();
            CodeActMessage message = parseMessage(output);
            if (reAct && finalizeRequested && message != null && "code".equals(message.type)) {
                memory.addOutputItems(java.util.Collections.singletonList(
                        AgentInputItem.systemMessage("FINALIZE_MODE: Do not output code. Use the latest CODE_RESULT to respond with {\"type\":\"final\",\"output\":\"...\"}.")
                ));
                publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
                step += 1;
                continue;
            }
            if (message == null || "final".equals(message.type)) {
                String answer = message == null ? output : message.output;
                publish(context, listener, AgentEventType.FINAL_OUTPUT, step, answer, modelResult == null ? null : modelResult.getRawResponse());
                publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
                return AgentResult.builder()
                        .outputText(answer == null ? "" : answer)
                        .rawResponse(modelResult == null ? null : modelResult.getRawResponse())
                        .toolCalls(toolCalls)
                        .toolResults(toolResults)
                        .steps(step + 1)
                        .build();
            }

            if (!"code".equals(message.type) || message.code == null) {
                publish(context, listener, AgentEventType.FINAL_OUTPUT, step, output, modelResult == null ? null : modelResult.getRawResponse());
                publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
                return AgentResult.builder()
                        .outputText(output == null ? "" : output)
                        .rawResponse(modelResult == null ? null : modelResult.getRawResponse())
                        .toolCalls(toolCalls)
                        .toolResults(toolResults)
                        .steps(step + 1)
                        .build();
            }

            AgentToolCall toolCall = AgentToolCall.builder()
                    .name("code")
                    .arguments(message.code)
                    .callId(CODE_CALL_ID + "_" + step)
                    .build();
            toolCalls.add(toolCall);
            publish(context, listener, AgentEventType.TOOL_CALL, step, toolCall.getName(), toolCall);

            CodeExecutionResult execResult = codeExecutor.execute(CodeExecutionRequest.builder()
                    .language(message.language)
                    .code(message.code)
                    .toolNames(extractToolNames(context.getToolRegistry() == null ? null : context.getToolRegistry().getTools()))
                    .toolExecutor(context.getToolExecutor())
                    .user(context.getUser())
                    .build());

            String toolOutput = buildToolOutput(execResult);
            toolResults.add(AgentToolResult.builder()
                    .name("code")
                    .callId(toolCall.getCallId())
                    .output(toolOutput)
                    .build());
            String toolMessage = (execResult != null && execResult.isSuccess())
                    ? "CODE_RESULT: " + toolOutput
                    : "CODE_ERROR: " + toolOutput;
            memory.addOutputItems(java.util.Collections.singletonList(AgentInputItem.systemMessage(toolMessage)));
            publish(context, listener, AgentEventType.TOOL_RESULT, step, toolOutput, execResult);
            if (reAct) {
                finalizeRequested = execResult != null && execResult.isSuccess();
            }

            String directOutput = resolveDirectOutput(execResult);
            String fallbackOutput = resolveFallbackOutput(execResult, toolOutput);
            String finalOutput = directOutput == null ? fallbackOutput : directOutput;
            if (!reAct && finalOutput != null) {
                publish(context, listener, AgentEventType.FINAL_OUTPUT, step, finalOutput, modelResult == null ? null : modelResult.getRawResponse());
                publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
                return AgentResult.builder()
                        .outputText(finalOutput)
                        .rawResponse(modelResult == null ? null : modelResult.getRawResponse())
                        .toolCalls(toolCalls)
                        .toolResults(toolResults)
                        .steps(step + 1)
                        .build();
            }

            publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
            step += 1;
        }

        String outputText = lastResult == null ? "" : lastResult.getOutputText();
        return AgentResult.builder()
                .outputText(outputText == null ? "" : outputText)
                .rawResponse(lastResult == null ? null : lastResult.getRawResponse())
                .toolCalls(toolCalls)
                .toolResults(toolResults)
                .steps(step)
                .build();
    }

    @Override
    protected AgentPrompt buildPrompt(AgentContext context, AgentMemory memory, boolean stream) {
        String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions(context));
        AgentPrompt.AgentPromptBuilder builder = AgentPrompt.builder()
                .model(context.getModel())
                .items(memory.getItems())
                .systemPrompt(systemPrompt)
                .instructions(context.getInstructions())
                .temperature(context.getTemperature())
                .topP(context.getTopP())
                .maxOutputTokens(context.getMaxOutputTokens())
                .reasoning(context.getReasoning())
                .store(context.getStore())
                .stream(false)
                .user(context.getUser())
                .extraBody(context.getExtraBody());
        return builder.build();
    }

    private String runtimeInstructions(AgentContext context) {
        StringBuilder builder = new StringBuilder();
        if (context.getCodeExecutor() instanceof NashornCodeExecutor) {
            builder.append("You are a CodeAct agent. Use JavaScript code to call tools when needed. ")
                    .append("Respond with a single JSON object only. ")
                    .append("If you need to run code, respond with {\"type\":\"code\",\"language\":\"js\",\"code\":\"...\"}. ")
                    .append("When you have the final answer, respond with {\"type\":\"final\",\"output\":\"...\"}. ")
                    .append("Do not include any extra text outside the JSON. ")
                    .append("In code, use JavaScript syntax compatible with Nashorn (ES5). ")
                    .append("Do not use Promise, async/await, template literals, let/const, or arrow functions. ")
                    .append("Always return a string or assign the final answer to __codeact_result before code ends. ")
                    .append("If your code returns a value, it will be used as the final answer. ")
                    .append("If you cannot use return, assign the final answer to __codeact_result. ")
                    .append("In code, you may call tools by name (e.g. queryWeather({\"location\":\"Beijing\"})) ")
                    .append("or use callTool(\"toolName\", args). ")
                    .append("If you see a CODE_RESULT message, use it to respond with type=final unless more tools are required. ");
        } else {
            builder.append("You are a CodeAct agent. Use Python code to call tools when needed. ")
                    .append("Respond with a single JSON object only. ")
                    .append("If you need to run code, respond with {\"type\":\"code\",\"language\":\"python\",\"code\":\"...\"}. ")
                    .append("When you have the final answer, respond with {\"type\":\"final\",\"output\":\"...\"}. ")
                    .append("Do not include any extra text outside the JSON. ")
                    .append("In code, use Python syntax only. ")
                    .append("If your code returns a value, it will be used as the final answer. ")
                    .append("If you cannot use return, assign the final answer to __codeact_result. ")
                    .append("In code, you may call tools by name (e.g. queryWeather({\"location\":\"Beijing\"})) ")
                    .append("or use callTool(\"toolName\", args). ")
                    .append("If you see a CODE_RESULT message, use it to respond with type=final unless more tools are required. ");
        }
        List<Object> tools = context.getToolRegistry() == null ? null : context.getToolRegistry().getTools();
        String toolGuide = buildToolGuide(tools);
        if (!toolGuide.isEmpty()) {
            builder.append("Available tools: ").append(toolGuide);
        }
        if (hasTool(tools, "queryWeather")) {
            if (context.getCodeExecutor() instanceof NashornCodeExecutor) {
                builder.append(" queryWeather returns Seniverse JSON (already parsed when possible).")
                        .append(" Use results[0].daily[0] fields: text_day, high, low, date.")
                        .append(" Example: var day = data.results[0].daily[0];");
            } else {
                builder.append(" queryWeather returns Seniverse JSON string.")
                        .append(" Parse first, then read data['results'][0]['daily'][0] fields text_day/high/low/date.");
            }
        }
        return builder.toString();
    }


    private boolean hasTool(List<Object> tools, String name) {
        if (tools == null || name == null) {
            return false;
        }
        for (Object tool : tools) {
            if (tool instanceof Tool) {
                Tool.Function fn = ((Tool) tool).getFunction();
                if (fn != null && name.equals(fn.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
    private String buildToolGuide(List<Object> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Object tool : tools) {
            if (tool instanceof Tool) {
                Tool.Function fn = ((Tool) tool).getFunction();
                if (fn != null && fn.getName() != null) {
                    if (builder.length() > 0) {
                        builder.append("; ");
                    }
                    builder.append(fn.getName());
                    if (fn.getDescription() != null) {
                        builder.append(" - ").append(fn.getDescription());
                    }
                }
            }
        }
        return builder.toString();
    }

    private List<String> extractToolNames(List<Object> tools) {
        List<String> names = new ArrayList<>();
        if (tools == null) {
            return names;
        }
        for (Object tool : tools) {
            if (tool instanceof Tool) {
                Tool.Function fn = ((Tool) tool).getFunction();
                if (fn != null && fn.getName() != null) {
                    names.add(fn.getName());
                }
            }
        }
        return names;
    }

    private CodeActMessage parseMessage(String output) {
        if (output == null || output.trim().isEmpty()) {
            return null;
        }
        String json = extractJson(output);
        if (json == null) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            CodeActMessage message = new CodeActMessage();
            message.type = valueAsString(obj.get("type"));
            message.language = valueAsString(obj.get("language"));
            message.code = valueAsString(obj.get("code"));
            message.output = valueAsString(obj.get("output"));
            return message;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJson(String text) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (start == -1) {
                    start = i;
                }
                depth += 1;
            } else if (c == '}') {
                depth -= 1;
                if (depth == 0 && start != -1) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String buildToolOutput(CodeExecutionResult result) {
        if (result == null) {
            return "";
        }
        JSONObject obj = new JSONObject();
        if (result.getResult() != null) {
            obj.put("result", result.getResult());
        }
        if (result.getStdout() != null && !result.getStdout().isEmpty()) {
            obj.put("stdout", result.getStdout());
        }
        if (result.getError() != null && !result.getError().isEmpty()) {
            obj.put("error", result.getError());
        }
        return obj.toJSONString();
    }

    private String resolveDirectOutput(CodeExecutionResult result) {
        if (result == null || !result.isSuccess()) {
            return null;
        }
        String value = result.getResult();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("undefined".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String resolveFallbackOutput(CodeExecutionResult result, String toolOutput) {
        if (result == null) {
            return toolOutput == null || toolOutput.isEmpty() ? null : toolOutput;
        }
        if (result.getError() != null && !result.getError().isEmpty()) {
            return "CODE_ERROR: " + result.getError();
        }
        if (result.getStdout() != null && !result.getStdout().isEmpty()) {
            return result.getStdout().trim();
        }
        if (toolOutput != null && !toolOutput.isEmpty()) {
            return toolOutput;
        }
        return null;
    }

    private String mergeText(String base, String extra) {
        if (base == null || base.trim().isEmpty()) {
            return extra;
        }
        if (extra == null || extra.trim().isEmpty()) {
            return base;
        }
        return base + "\n" + extra;
    }

    private static class CodeActMessage {
        private String type;
        private String language;
        private String code;
        private String output;
    }
}
