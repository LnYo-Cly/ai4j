package io.github.lnyocly.ai4j.platform.minimax.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.minimax.chat.entity.MinimaxChatCompletion;
import io.github.lnyocly.ai4j.platform.minimax.chat.entity.MinimaxChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author : isxuwl
 * @Date: 2024/10/15 16:24
 * @Model Description:
 * @Description: Minimax
 */
public class MinimaxChatService implements IChatService, ParameterConvert<MinimaxChatCompletion>, ResultConvert<MinimaxChatCompletionResponse> {
    private final MinimaxConfig minimaxConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public MinimaxChatService(Configuration configuration) {
        this.minimaxConfig = configuration.getMinimaxConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }



    @Override
    public MinimaxChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        MinimaxChatCompletion minimaxChatCompletion = new MinimaxChatCompletion();
        minimaxChatCompletion.setModel(chatCompletion.getModel());
        minimaxChatCompletion.setMessages(chatCompletion.getMessages());
        minimaxChatCompletion.setTools(chatCompletion.getTools());
        minimaxChatCompletion.setFunctions(chatCompletion.getFunctions());
        minimaxChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        minimaxChatCompletion.setTemperature(chatCompletion.getTemperature());
        minimaxChatCompletion.setTopP(chatCompletion.getTopP());
        minimaxChatCompletion.setStream(chatCompletion.getStream());
        minimaxChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        return minimaxChatCompletion;
    }

    @Override
    public EventSourceListener convertEventSource(SseListener eventSourceListener) {
        return new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                eventSourceListener.onOpen(eventSource, response);
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                eventSourceListener.onFailure(eventSource, t, response);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                if ("[DONE]".equalsIgnoreCase(data)) {
                    eventSourceListener.onEvent(eventSource, id, type, data);
                    return;
                }

                MinimaxChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, MinimaxChatCompletionResponse.class);
                ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);

                eventSourceListener.onEvent(eventSource, id, type, JSON.toJSONString(response));
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }
        };
    }


    @Override
    public ChatCompletionResponse convertChatCompletionResponse(MinimaxChatCompletionResponse minimaxChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(minimaxChatCompletionResponse.getId());
        chatCompletionResponse.setObject(minimaxChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(minimaxChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(minimaxChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(minimaxChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(minimaxChatCompletionResponse.getUsage());

        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = minimaxConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = minimaxConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);

        // 转换 请求参数
        MinimaxChatCompletion minimaxChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(minimaxChatCompletion.getFunctions()!=null && !minimaxChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(minimaxChatCompletion.getFunctions());
            minimaxChatCompletion.setTools(tools);
        }

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            String requestString = JSON.toJSONString(minimaxChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, minimaxConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                MinimaxChatCompletionResponse minimaxChatCompletionResponse = JSON.parseObject(execute.body().string(), MinimaxChatCompletionResponse.class);

                Choice choice = minimaxChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = minimaxChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(minimaxChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    minimaxChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    minimaxChatCompletionResponse.setUsage(allUsage);
                    //deepSeekChatCompletionResponse.setObject("chat.completion");

                    // 恢复原始请求数据
                    chatCompletion.setMessages(minimaxChatCompletion.getMessages());
                    chatCompletion.setTools(minimaxChatCompletion.getTools());

                    return this.convertChatCompletionResponse(minimaxChatCompletionResponse);

                }

            }

        }


        return null;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return this.chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = minimaxConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = minimaxConfig.getApiKey();
        chatCompletion.setStream(true);

        // 转换 请求参数
        MinimaxChatCompletion minimaxChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(minimaxChatCompletion.getFunctions()!=null && !minimaxChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(minimaxChatCompletion.getFunctions());
            minimaxChatCompletion.setTools(tools);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(minimaxChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, minimaxConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), jsonString))
                    .build();


            factory.newEventSource(request, convertEventSource(eventSourceListener));
            eventSourceListener.getCountDownLatch().await();

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if("tool_calls".equals(finishReason) && !toolCalls.isEmpty()){
                // 创建tool响应消息
                ChatMessage responseMessage = ChatMessage.withAssistant(eventSourceListener.getToolCalls());

                List<ChatMessage> messages = new ArrayList<>(minimaxChatCompletion.getMessages());
                messages.add(responseMessage);

                // 封装tool结果消息
                for (ToolCall toolCall : toolCalls) {
                    String functionName = toolCall.getFunction().getName();
                    String arguments = toolCall.getFunction().getArguments();
                    String functionResponse = ToolUtil.invoke(functionName, arguments);

                    messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                }
                eventSourceListener.setToolCalls(new ArrayList<>());
                eventSourceListener.setToolCall(null);
                minimaxChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(minimaxChatCompletion.getMessages());
        chatCompletion.setTools(minimaxChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
