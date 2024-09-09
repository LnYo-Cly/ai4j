package io.github.lnyocly.ai4j.platform.lingyi.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.DeepSeekConfig;
import io.github.lnyocly.ai4j.config.LingyiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.ParameterConvert;
import io.github.lnyocly.ai4j.convert.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.deepseek.chat.entity.DeepSeekChatCompletion;
import io.github.lnyocly.ai4j.platform.deepseek.chat.entity.DeepSeekChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.lingyi.chat.entity.LingyiChatCompletion;
import io.github.lnyocly.ai4j.platform.lingyi.chat.entity.LingyiChatCompletionResponse;
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
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description 零一万物 chat服务
 * @Date 2024/9/9 23:00
 */
public class LingyiChatService implements IChatService, ParameterConvert<LingyiChatCompletion>, ResultConvert<LingyiChatCompletionResponse> {
    private final LingyiConfig lingyiConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public LingyiChatService(Configuration configuration) {
        this.lingyiConfig = configuration.getLingyiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }



    @Override
    public LingyiChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        LingyiChatCompletion lingyiChatCompletion = new LingyiChatCompletion();
        lingyiChatCompletion.setModel(chatCompletion.getModel());
        lingyiChatCompletion.setMessages(chatCompletion.getMessages());
        lingyiChatCompletion.setTools(chatCompletion.getTools());
        lingyiChatCompletion.setFunctions(chatCompletion.getFunctions());
        lingyiChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        lingyiChatCompletion.setTemperature(chatCompletion.getTemperature());
        lingyiChatCompletion.setTopP(chatCompletion.getTopP());
        lingyiChatCompletion.setStream(chatCompletion.getStream());
        lingyiChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        return lingyiChatCompletion;
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

                LingyiChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, LingyiChatCompletionResponse.class);
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
    public ChatCompletionResponse convertChatCompletionResponse(LingyiChatCompletionResponse lingyiChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(lingyiChatCompletionResponse.getId());
        chatCompletionResponse.setObject(lingyiChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(lingyiChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(lingyiChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(lingyiChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(lingyiChatCompletionResponse.getUsage());

        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = lingyiConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = lingyiConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);

        // 转换 请求参数
        LingyiChatCompletion lingyiChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(lingyiChatCompletion.getFunctions()!=null && !lingyiChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(lingyiChatCompletion.getFunctions());
            lingyiChatCompletion.setTools(tools);
        }

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            String requestString = JSON.toJSONString(lingyiChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(baseUrl.concat(lingyiConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(requestString, MediaType.parse(Constants.JSON_CONTENT_TYPE)))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                LingyiChatCompletionResponse lingyiChatCompletionResponse = JSON.parseObject(execute.body().string(), LingyiChatCompletionResponse.class);

                Choice choice = lingyiChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = lingyiChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(lingyiChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    lingyiChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    lingyiChatCompletionResponse.setUsage(allUsage);
                    //deepSeekChatCompletionResponse.setObject("chat.completion");

                    // 恢复原始请求数据
                    chatCompletion.setMessages(lingyiChatCompletion.getMessages());
                    chatCompletion.setTools(lingyiChatCompletion.getTools());

                    return this.convertChatCompletionResponse(lingyiChatCompletionResponse);

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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = lingyiConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = lingyiConfig.getApiKey();
        chatCompletion.setStream(true);

        // 转换 请求参数
        LingyiChatCompletion lingyiChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(lingyiChatCompletion.getFunctions()!=null && !lingyiChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(lingyiChatCompletion.getFunctions());
            lingyiChatCompletion.setTools(tools);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(lingyiChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(baseUrl.concat(lingyiConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(jsonString, MediaType.parse(Constants.APPLICATION_JSON)))
                    .build();


            factory.newEventSource(request, convertEventSource(eventSourceListener));
            eventSourceListener.getCountDownLatch().await();

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if("tool_calls".equals(finishReason) && !toolCalls.isEmpty()){
                // 创建tool响应消息
                ChatMessage responseMessage = ChatMessage.withAssistant(eventSourceListener.getToolCalls());

                List<ChatMessage> messages = new ArrayList<>(lingyiChatCompletion.getMessages());
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
                lingyiChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(lingyiChatCompletion.getMessages());
        chatCompletion.setTools(lingyiChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
