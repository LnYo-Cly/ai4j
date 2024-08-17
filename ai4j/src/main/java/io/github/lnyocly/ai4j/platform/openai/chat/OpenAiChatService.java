package io.github.lnyocly.ai4j.platform.openai.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description OpenAi 聊天服务
 * @Date 2024/8/2 23:16
 */
@Slf4j
public class OpenAiChatService implements IChatService {

    private final OpenAiConfig openAiConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public OpenAiChatService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }


    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion)  throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = openAiConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = openAiConfig.getApiKey();
        chatCompletion.setStream(false);
        if(chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
            chatCompletion.setTools(tools);
        }

        String jsonString = JSON.toJSONString(chatCompletion);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(baseUrl.concat(openAiConfig.getV1_chat_completions()))
                .post(RequestBody.create(jsonString, MediaType.parse(Constants.JSON_CONTENT_TYPE)))
                .build();
        Response execute = okHttpClient.newCall(request).execute();


        if (execute.isSuccessful() && execute.body() != null) {
            ChatCompletionResponse chatCompletionResponse = JSON.parseObject(execute.body().string(), ChatCompletionResponse.class);

            ChatMessage responseMessage = chatCompletionResponse.getChoices().get(0).getMessage();
            List<ToolCall> toolCalls = responseMessage.getToolCalls();
            if(toolCalls == null || toolCalls.isEmpty()) {
                return chatCompletionResponse;
            }

            List<ChatMessage> messages = new ArrayList<>(chatCompletion.getMessages());
            messages.add(responseMessage);

            for (ToolCall toolCall : toolCalls) {
                String functionName = toolCall.getFunction().getName();
                String arguments = toolCall.getFunction().getArguments();
                String functionResponse = ToolUtil.invoke(functionName, arguments);

                messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
            }
            chatCompletion.setMessages(messages);

            jsonString = JSON.toJSONString(chatCompletion);
            request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(baseUrl.concat(openAiConfig.getV1_chat_completions()))
                    .post(RequestBody.create(jsonString, MediaType.parse(Constants.JSON_CONTENT_TYPE)))
                    .build();
            execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null) {
                return JSON.parseObject(execute.body().string(), ChatCompletionResponse.class);
            }


        }
        log.error("chat请求失败 {}", execute.message());
        return null;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion)  throws Exception {
        return chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = openAiConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = openAiConfig.getApiKey();
        chatCompletion.setStream(true);

        // 获取函数调用
        if(chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
            chatCompletion.setTools(tools);
        }


        String jsonString = JSON.toJSONString(chatCompletion);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(baseUrl.concat(openAiConfig.getV1_chat_completions()))
                .post(RequestBody.create(jsonString, MediaType.parse(Constants.APPLICATION_JSON)))
                .build();

        factory.newEventSource(request, eventSourceListener);

        eventSourceListener.getCountDownLatch().await();

        //log.info("第一批已经结束了");

        //System.out.println(eventSourceListener.getToolCalls());
        List<ToolCall> toolCalls = eventSourceListener.getToolCalls();
        // 判断是否需要调用函数
        if(toolCalls.isEmpty()) return;

        // 创建tool响应消息
        ChatMessage responseMessage = ChatMessage.withAssistant(eventSourceListener.getToolCalls());

        List<ChatMessage> messages = new ArrayList<>(chatCompletion.getMessages());
        messages.add(responseMessage);

        // 封装tool结果消息
        for (ToolCall toolCall : toolCalls) {
            String functionName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();
            String functionResponse = ToolUtil.invoke(functionName, arguments);

            messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
        }
        chatCompletion.setMessages(messages);

        // 二次请求
        jsonString = JSON.toJSONString(chatCompletion);
        request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(baseUrl.concat(openAiConfig.getV1_chat_completions()))
                .post(RequestBody.create(jsonString, MediaType.parse(Constants.JSON_CONTENT_TYPE)))
                .build();
        factory.newEventSource(request, eventSourceListener);

        eventSourceListener.getCountDownLatch().await();

        //log.info("第二批已经结束了");
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
