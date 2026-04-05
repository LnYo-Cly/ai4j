package io.github.lnyocly.ai4j.listener;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description SseListener
 * @Date 2024/8/13 23:25
 */

@Slf4j
public abstract class SseListener extends AbstractManagedStreamListener {
    /**
     * 异常回调
     */
    protected void error(Throwable t, Response response) {}

    protected abstract void send();
    /**
     * 最终的消息输出
     */
    @Getter
    private final StringBuilder output = new StringBuilder();

    /**
     * 流式输出，当前消息的内容(回答消息、函数参数)
     */
    @Getter
    private String currStr = "";

    /**
     * 流式输出，当前单条SSE消息对象，即ChatCompletionResponse对象
     */
    @Getter
    private String currData = "";

    /**
     * 记录当前所调用函数工具的名称
     */
    @Getter
    private String currToolName = "";

    /**
     * 记录当前是否为思考状态reasoning
     */
    @Getter
    private boolean isReasoning = false;

    /**
     * 思考内容的输出
     */
    @Getter
    private final StringBuilder reasoningOutput = new StringBuilder();

    /**
     * 是否显示每个函数调用输出的参数文本
     */
    @Getter
    @Setter
    private boolean showToolArgs = false;

    /**
     * 花费token
     */
    @Getter
    private final Usage usage = new Usage();

    @Setter
    @Getter
    private List<ToolCall> toolCalls = new ArrayList<>();

    @Setter
    @Getter
    private ToolCall toolCall;

    /**
     * 最终的函数调用参数
     */
    private final StringBuilder argument = new StringBuilder();
    @Getter
    @Setter
    private String finishReason = null;

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        // 封装SSE消息对象
        currData = data;
        markActivity();
        if (getEventSource() == null) {
            attachEventSource(eventSource);
        }

        if ("[DONE]".equalsIgnoreCase(data)) {
            // 整个对话结束，结束前将SSE最后一条“DONE”消息发送出去
            currStr = "";
            this.send();

            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ChatCompletionResponse chatCompletionResponse = null;
        try {
            chatCompletionResponse = objectMapper.readValue(data, ChatCompletionResponse.class);
        } catch (JsonProcessingException e) {
            throw new CommonException("read data error");
        }

        // 统计token，当设置include_usage = true时，最后一条消息会携带usage, 其他消息中usage为null
        Usage currUsage = chatCompletionResponse.getUsage();
        if(currUsage != null){
            usage.setPromptTokens(usage.getPromptTokens() + currUsage.getPromptTokens());
            usage.setCompletionTokens(usage.getCompletionTokens() + currUsage.getCompletionTokens());
            usage.setTotalTokens(usage.getTotalTokens() + currUsage.getTotalTokens());
        }


        List<Choice> choices = chatCompletionResponse.getChoices();

        if((choices == null || choices.isEmpty()) && chatCompletionResponse.getUsage() != null){
            this.currStr = "";
            this.send();
            return;
        }

        if(choices == null || choices.isEmpty()){
            return;
        }
        ChatMessage responseMessage = choices.get(0).getDelta();
        if (responseMessage == null) {
            return;
        }
        List<ToolCall> messageToolCalls = responseMessage.getToolCalls();
        ToolCall firstMessageToolCall = firstToolCall(messageToolCalls);

        finishReason = choices.get(0).getFinishReason();

        // Ollama 在工具调用时返回 stop 而非 tool_calls
        if("stop".equals(finishReason)
                && responseMessage.getContent()!=null
                && "".equals(responseMessage.getContent().getText())
                && !toolCalls.isEmpty()){
            finishReason = "tool_calls";
        }


        // tool_calls回答已经结束
        if("tool_calls".equals(finishReason)){
            if (toolCall != null) {
                consumeFragmentedToolCalls(messageToolCalls);
                finalizeCurrentToolCall();
            } else if (shouldTreatAsCompleteToolCalls(responseMessage, messageToolCalls)) {
                addCompleteToolCalls(messageToolCalls);
            } else {
                consumeFragmentedToolCalls(messageToolCalls);
                finalizeCurrentToolCall();
            }
            return;
        }
        // 消息回答完毕
        if ("stop".equals(finishReason)) {

            // ollama 最后一条消息只到stop
            if(responseMessage.getContent() != null && responseMessage.getContent().getText() != null) {
                currStr = responseMessage.getContent().getText();
                output.append(currStr);
            }else {
                currStr = "";
            }
            this.send();


            return;
        }

        if(ChatMessageType.ASSISTANT.getRole().equals(responseMessage.getRole())
                && (responseMessage.getContent()==null || StringUtils.isEmpty(responseMessage.getContent().getText()))
                && StringUtils.isEmpty(responseMessage.getReasoningContent())
                && isEmpty(messageToolCalls)){
            // 空消息忽略
            return;
        }


        if(isEmpty(messageToolCalls)) {


            // 判断是否为混元的tool最后一条说明性content，用于忽略
            // :{"Role":"assistant","Content":"计划使用get_current_weather工具来获取北京和深圳的当前天气。\n\t\n\t用户想要知道北京和深圳今天的天气情况。用户的请求是关于天气的查询，需要使用天气查询工具来获取信息。"}
            if(toolCall !=null && StringUtils.isNotEmpty(argument)&& "assistant".equals(responseMessage.getRole()) && (responseMessage.getContent()!=null && StringUtils.isNotEmpty(responseMessage.getContent().getText())) ){
                return;
            }


            // 响应回答
            // 包括content和reasoning_content
            if(StringUtils.isNotEmpty(responseMessage.getReasoningContent())){
                isReasoning = true;
                // reasoningOutput 与 output 分离，目前仅用于deepseek
                reasoningOutput.append(responseMessage.getReasoningContent());
                //output.append(responseMessage.getReasoningContent());
                currStr = responseMessage.getReasoningContent();

            }else {
                isReasoning = false;
                if (responseMessage.getContent() == null) {
                    this.send();
                    return;
                }
                output.append(responseMessage.getContent().getText());
                currStr = responseMessage.getContent().getText();
            }

            this.send();


        }else{
            // 函数调用回答
            if (shouldTreatAsCompleteToolCalls(responseMessage, messageToolCalls)) {
                addCompleteToolCalls(messageToolCalls);
            } else {
                consumeFragmentedToolCalls(messageToolCalls);
            }
        }



        //log.info("测试结果：{}", chatCompletionResponse);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        attachEventSource(eventSource);
        finishAttempt();
        clearCancelRequested();

    }

    @Override
    protected void resetRetryState() {
        finishReason = null;
        currData = "";
        currStr = "";
        currToolName = "";
    }

    private ToolCall firstToolCall(List<ToolCall> calls) {
        if (isEmpty(calls)) {
            return null;
        }
        return calls.get(0);
    }

    private String safeToolArguments(ToolCall call) {
        if (call == null || call.getFunction() == null) {
            return "";
        }
        return StrUtil.emptyIfNull(call.getFunction().getArguments());
    }

    private String safeToolName(ToolCall call) {
        if (call == null || call.getFunction() == null) {
            return "";
        }
        return StrUtil.emptyIfNull(call.getFunction().getName());
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private boolean shouldTreatAsCompleteToolCalls(ChatMessage responseMessage, List<ToolCall> messageToolCalls) {
        if (responseMessage == null || responseMessage.getContent() == null) {
            return false;
        }
        if (!"".equals(responseMessage.getContent().getText()) || isEmpty(messageToolCalls)) {
            return false;
        }
        for (ToolCall call : messageToolCalls) {
            if (!hasToolName(call) || !hasStructuredJsonObjectArguments(call)) {
                return false;
            }
        }
        return true;
    }

    private void addCompleteToolCalls(List<ToolCall> completeToolCalls) {
        if (isEmpty(completeToolCalls)) {
            return;
        }
        for (ToolCall completeToolCall : completeToolCalls) {
            if (completeToolCall == null || completeToolCall.getFunction() == null || !hasToolName(completeToolCall)) {
                continue;
            }
            currToolName = safeToolName(completeToolCall);
            toolCalls.add(completeToolCall);
            if (showToolArgs) {
                this.currStr = StrUtil.emptyIfNull(completeToolCall.getFunction().getArguments());
                this.send();
            }
        }
        argument.setLength(0);
        currToolName = "";
    }

    private void consumeFragmentedToolCalls(List<ToolCall> messageToolCalls) {
        if (isEmpty(messageToolCalls)) {
            return;
        }
        for (ToolCall currentToolCall : messageToolCalls) {
            if (currentToolCall == null || currentToolCall.getFunction() == null) {
                continue;
            }
            String argumentsDelta = StrUtil.emptyIfNull(safeToolArguments(currentToolCall));
            if (hasToolIdentity(currentToolCall)) {
                if (toolCall == null) {
                    startToolCall(currentToolCall, argumentsDelta);
                } else if (isSameToolCall(toolCall, currentToolCall)) {
                    mergeToolIdentity(toolCall, currentToolCall);
                    argument.append(argumentsDelta);
                } else {
                    finalizeCurrentToolCall();
                    startToolCall(currentToolCall, argumentsDelta);
                }
                if (showToolArgs) {
                    this.currStr = argumentsDelta;
                    this.send();
                }
                continue;
            }
            if (toolCall != null) {
                argument.append(argumentsDelta);
                if (showToolArgs) {
                    this.currStr = argumentsDelta;
                    this.send();
                }
            }
        }
    }

    private void startToolCall(ToolCall currentToolCall, String argumentsDelta) {
        toolCall = currentToolCall;
        argument.setLength(0);
        argument.append(StrUtil.emptyIfNull(argumentsDelta));
        currToolName = safeToolName(currentToolCall);
    }

    private void finalizeCurrentToolCall() {
        if (toolCall == null) {
            argument.setLength(0);
            currToolName = "";
            return;
        }
        if (toolCall.getFunction() != null) {
            toolCall.getFunction().setArguments(argument.toString());
        }
        toolCalls.add(toolCall);
        toolCall = null;
        argument.setLength(0);
        currToolName = "";
    }

    private void mergeToolIdentity(ToolCall target, ToolCall source) {
        if (target == null || source == null) {
            return;
        }
        if (StrUtil.isBlank(target.getId()) && StrUtil.isNotBlank(source.getId())) {
            target.setId(source.getId());
        }
        if (StrUtil.isBlank(target.getType()) && StrUtil.isNotBlank(source.getType())) {
            target.setType(source.getType());
        }
        if (target.getFunction() == null || source.getFunction() == null) {
            return;
        }
        if (StrUtil.isBlank(target.getFunction().getName()) && StrUtil.isNotBlank(source.getFunction().getName())) {
            target.getFunction().setName(source.getFunction().getName());
        }
    }

    private boolean hasToolIdentity(ToolCall call) {
        return call != null
                && (StrUtil.isNotBlank(call.getId()) || hasToolName(call));
    }

    private boolean hasToolName(ToolCall call) {
        return call != null
                && call.getFunction() != null
                && StrUtil.isNotBlank(call.getFunction().getName());
    }

    private boolean isSameToolCall(ToolCall left, ToolCall right) {
        if (left == null || right == null) {
            return false;
        }
        if (StrUtil.isNotBlank(left.getId()) && StrUtil.isNotBlank(right.getId())) {
            return left.getId().equals(right.getId());
        }
        if (left.getFunction() == null || right.getFunction() == null) {
            return false;
        }
        return StrUtil.isNotBlank(left.getFunction().getName())
                && left.getFunction().getName().equals(right.getFunction().getName());
    }

    private boolean hasStructuredJsonObjectArguments(ToolCall call) {
        String arguments = safeToolArguments(call);
        if (StringUtils.isBlank(arguments)) {
            return false;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(arguments);
            return node != null && node.isObject();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    protected Throwable resolveFailure(@Nullable Throwable t, @Nullable Response response) {
        if (t != null && StringUtils.isNotBlank(t.getMessage())) {
            return t;
        }
        String message = resolveFailureMessage(t, response);
        if (StringUtils.isBlank(message)) {
            return t == null ? new CommonException("stream request failed") : t;
        }
        return new CommonException(message);
    }

    protected String resolveFailureMessage(@Nullable Throwable t, @Nullable Response response) {
        if (t != null && StringUtils.isNotBlank(t.getMessage())) {
            return t.getMessage().trim();
        }
        String responseMessage = responseMessage(response);
        if (StringUtils.isNotBlank(responseMessage)) {
            return responseMessage;
        }
        if (response != null) {
            String statusLine = (response.code() + " " + StrUtil.emptyIfNull(response.message())).trim();
            if (StringUtils.isNotBlank(statusLine)) {
                return statusLine;
            }
        }
        return t == null ? "stream request failed" : t.getClass().getSimpleName();
    }

    private String responseMessage(@Nullable Response response) {
        if (response == null) {
            return null;
        }
        try {
            okhttp3.ResponseBody peekedBody = response.peekBody(8192L);
            if (peekedBody == null) {
                return null;
            }
            String payload = StringUtils.trimToNull(peekedBody.string());
            if (payload == null) {
                return null;
            }
            String extracted = extractStructuredErrorMessage(payload);
            return extracted == null ? StringUtils.abbreviate(payload, 320) : extracted;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractStructuredErrorMessage(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(payload);
            String message = firstJsonText(
                    root.path("error").path("message"),
                    root.path("error"),
                    root.path("message"),
                    root.path("msg"),
                    root.path("detail"),
                    root.path("Response").path("Error").path("Message"),
                    root.path("Response").path("Error"),
                    root.path("error_msg")
            );
            return StringUtils.trimToNull(message);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstJsonText(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isTextual()) {
                String text = StringUtils.trimToNull(node.asText());
                if (text != null) {
                    return text;
                }
                continue;
            }
            if (node.isObject()) {
                String text = firstJsonText(node.path("message"), node.path("Message"), node.path("msg"), node.path("detail"));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

}
