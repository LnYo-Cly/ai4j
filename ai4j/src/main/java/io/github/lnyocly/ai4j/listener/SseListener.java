package io.github.lnyocly.ai4j.listener;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
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
     * 记录当前所调用函数工具的名称
     */
    @Getter
    private String currToolName = "";

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
    private Usage usage = null;

    @Setter
    @Getter
    private List<ToolCall> toolCalls = new ArrayList<>();

    private ToolCall toolCall;

    /**
     * 最终的函数调用参数
     */
    private final StringBuilder argument = new StringBuilder();
    @Getter
    private CountDownLatch countDownLatch = new CountDownLatch(1);


    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        log.error("流式输出异常 onFailure ");
        countDownLatch.countDown();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        if ("[DONE]".equalsIgnoreCase(data)) {
            log.info("模型会话 [DONE]");
            return;
        }

        ChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, ChatCompletionResponse.class);
        ChatMessage responseMessage = chatCompletionResponse.getChoices().get(0).getDelta();

        if(ChatMessageType.ASSISTANT.getRole().equals(responseMessage.getRole())
                && StringUtils.isBlank(responseMessage.getContent())
                && responseMessage.getToolCalls() == null){
            // 第一条消息
            return;
        }


        // tool_calls回答已经结束
        if("tool_calls".equals(chatCompletionResponse.getChoices().get(0).getFinish_reason())){
            toolCall.getFunction().setArguments(argument.toString());
            toolCalls.add(toolCall);
            argument.setLength(0);
            currToolName = "";
            return;
        }
        // 消息回答完毕
        if ("stop".equals(chatCompletionResponse.getChoices().get(0).getFinish_reason())) {

            return;
        }


        if(responseMessage.getToolCalls() == null) {
            // 普通响应回答
            output.append(responseMessage.getContent());
            currStr = responseMessage.getContent();
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



        log.info("测试结果：{}", chatCompletionResponse);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        log.info("调用 onClosed ");
        countDownLatch.countDown();
        countDownLatch = new CountDownLatch(1);

    }
}
