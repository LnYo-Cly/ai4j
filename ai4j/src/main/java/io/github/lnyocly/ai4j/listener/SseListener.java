package io.github.lnyocly.ai4j.listener;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @Author cly
 * @Description SseListener
 * @Date 2024/8/13 23:25
 */

@Slf4j
public abstract class SseListener extends EventSourceListener {
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
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Getter
    private String finishReason = null;

    @Getter
    private EventSource eventSource = null;

    private boolean ollamaToolCall = false;

    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {

        countDownLatch.countDown();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        // 封装SSE消息对象
        currData = data;
        if(this.eventSource == null) {
            this.eventSource = eventSource;
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

        if(choices == null || choices.isEmpty()){
            return;
        }
        ChatMessage responseMessage = choices.get(0).getDelta();

        finishReason = choices.get(0).getFinishReason();

        if("stop".equals(finishReason) && ollamaToolCall == true){
            ollamaToolCall = false;
            finishReason = "tool_calls";
        }


        // tool_calls回答已经结束
        if("tool_calls".equals(finishReason)){
            if(toolCall == null && responseMessage.getToolCalls()!=null) {
                toolCalls = responseMessage.getToolCalls();
                if(showToolArgs){
                    this.currStr = responseMessage.getToolCalls().get(0).getFunction().getArguments();
                    this.send();
                }
                return;
            }

            if(toolCall != null) {
                toolCall.getFunction().setArguments(argument.toString());
                toolCalls.add(toolCall);
            }
            argument.setLength(0);
            currToolName = "";
            return;
        }
        // 消息回答完毕
        if ("stop".equals(finishReason)) {

            // ollama 最后一条消息只到stop
            if(responseMessage.getContent() != null && responseMessage.getContent().getText() != null) {
                currStr = responseMessage.getContent().getText();
            }else {
                currStr = "";
            }
            this.send();

            return;
        }

        if(ChatMessageType.ASSISTANT.getRole().equals(responseMessage.getRole())
                && (responseMessage.getContent()==null || StringUtils.isEmpty(responseMessage.getContent().getText()))
                && responseMessage.getToolCalls() == null){
            // 空消息忽略
            return;
        }


        if(responseMessage.getToolCalls() == null ) {


            // 判断是否为混元的tool最后一条说明性content
            // :{"Role":"assistant","Content":"计划使用get_current_weather工具来获取北京和深圳的当前天气。\n\t\n\t用户想要知道北京和深圳今天的天气情况。用户的请求是关于天气的查询，需要使用天气查询工具来获取信息。"}
            if(toolCall !=null && StringUtils.isNotEmpty(argument)&& "assistant".equals(responseMessage.getRole()) && (responseMessage.getContent()!=null && StringUtils.isNotEmpty(responseMessage.getContent().getText())) ){
                return;
            }


            if(responseMessage.getContent() != null && "<tool_call>".equals(responseMessage.getContent().getText())){
                // ollama的tool_call
                ollamaToolCall = true;
                return;
            }


            if(ollamaToolCall){

                /**
                 * <tool_call>{"name": "queryWeather", "arguments": {"location": "洛阳", "days":1, "type": "daily"}}
                 * </tool_call>
                 */

                if(responseMessage.getContent() != null && "</tool_call>".equals(responseMessage.getContent().getText())){
                    // ollama的tool_call

                    ToolCall.Function function = JSON.parseObject(argument.toString(), ToolCall.Function.class);
                    toolCall = new ToolCall();
                    toolCall.setFunction(function);
                    currToolName = function.getName();
                    argument.setLength(0);
                    argument.append(function.getArguments());
                    return;
                }

                argument.append(responseMessage.getContent().getText());
                if(showToolArgs){
                    this.currStr = responseMessage.getContent().getText();
                    this.send();
                }
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
                    return;
                }
                output.append(responseMessage.getContent().getText());
                currStr = responseMessage.getContent().getText();
            }

            this.send();


        }else{
            // 函数调用回答

            // 第一条ToolCall表示，不含参数信息
            if(responseMessage.getToolCalls().get(0).getId() != null) {
                if( toolCall == null ){
                    // 第一个函数
                    toolCall = responseMessage.getToolCalls().get(0);
                }else {
                    toolCall.getFunction().setArguments(argument.toString());
                    argument.setLength(0);
                    toolCalls.add(toolCall);
                    toolCall = responseMessage.getToolCalls().get(0);
                }

                currToolName = responseMessage.getToolCalls().get(0).getFunction().getName();


            }else {
                argument.append(responseMessage.getToolCalls().get(0).getFunction().getArguments());
                if(showToolArgs){
                    this.currStr = responseMessage.getToolCalls().get(0).getFunction().getArguments();
                    this.send();
                }
            }


        }



        //log.info("测试结果：{}", chatCompletionResponse);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        countDownLatch.countDown();
        countDownLatch = new CountDownLatch(1);

    }
}
