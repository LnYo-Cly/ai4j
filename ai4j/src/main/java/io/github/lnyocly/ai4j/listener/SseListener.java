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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/13 23:25
 */

@Slf4j
public class SseListener extends EventSourceListener {

    @Getter
    private final StringBuilder output = new StringBuilder();

    @Getter
    private Usage usage = null;

    @Getter
    @Setter
    private List<ToolCall> toolCalls = new ArrayList<>();

    private ToolCall toolCall;
    private final StringBuilder argument = new StringBuilder();
    @Getter
    private CountDownLatch countDownLatch = new CountDownLatch(1);


    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        log.error("调用 onFailure ");
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        if ("[DONE]".equalsIgnoreCase(data)) {
            log.info("模型会话 [DONE]");
            return;
        }

        ChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, ChatCompletionResponse.class);
        ChatMessage responseMessage = chatCompletionResponse.getChoices().get(0).getDelta();

        if(ChatMessageType.ASSISTANT.getRole().equals(responseMessage.getRole())){
            // 第一条消息
            return;
        }


        // tool_calls回答已经结束
        if("tool_calls".equals(chatCompletionResponse.getChoices().get(0).getFinish_reason())  ){
            toolCall.getFunction().setArguments(argument.toString());
            toolCalls.add(toolCall);
            argument.setLength(0);
            return;
        }


        if(responseMessage.getToolCalls() == null) {
            // 普通响应回答



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


            }else {
                argument.append(responseMessage.getToolCalls().get(0).getFunction().getArguments());
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
