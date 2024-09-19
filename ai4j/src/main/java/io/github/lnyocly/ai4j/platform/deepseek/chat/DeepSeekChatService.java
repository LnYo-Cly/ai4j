package io.github.lnyocly.ai4j.platform.deepseek.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.DeepSeekConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.ParameterConvert;
import io.github.lnyocly.ai4j.convert.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.deepseek.chat.entity.DeepSeekChatCompletion;
import io.github.lnyocly.ai4j.platform.deepseek.chat.entity.DeepSeekChatCompletionResponse;
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
 * @Author cly
 * @Description DeepSeek Chat服务
 * @Date 2024/8/29 10:26
 */
public class DeepSeekChatService implements IChatService, ParameterConvert<DeepSeekChatCompletion>, ResultConvert<DeepSeekChatCompletionResponse> {
    private final DeepSeekConfig deepSeekConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public DeepSeekChatService(Configuration configuration) {
        this.deepSeekConfig = configuration.getDeepSeekConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }


    @Override
    public DeepSeekChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        DeepSeekChatCompletion deepSeekChatCompletion = new DeepSeekChatCompletion();
        deepSeekChatCompletion.setModel(chatCompletion.getModel());
        deepSeekChatCompletion.setMessages(chatCompletion.getMessages());
        deepSeekChatCompletion.setFrequencyPenalty(chatCompletion.getFrequencyPenalty());
        deepSeekChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        deepSeekChatCompletion.setPresencePenalty(chatCompletion.getPresencePenalty());
        deepSeekChatCompletion.setResponseFormat(chatCompletion.getResponseFormat());
        deepSeekChatCompletion.setStop(chatCompletion.getStop());
        deepSeekChatCompletion.setStream(chatCompletion.getStream());
        deepSeekChatCompletion.setStreamOptions(chatCompletion.getStreamOptions());
        deepSeekChatCompletion.setTemperature(chatCompletion.getTemperature());
        deepSeekChatCompletion.setTopP(chatCompletion.getTopP());
        deepSeekChatCompletion.setTools(chatCompletion.getTools());
        deepSeekChatCompletion.setFunctions(chatCompletion.getFunctions());
        deepSeekChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        deepSeekChatCompletion.setLogprobs(chatCompletion.getLogprobs());
        deepSeekChatCompletion.setTopLogprobs(chatCompletion.getTopLogprobs());
        return deepSeekChatCompletion;
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

                DeepSeekChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, DeepSeekChatCompletionResponse.class);
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
    public ChatCompletionResponse convertChatCompletionResponse(DeepSeekChatCompletionResponse deepSeekChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(deepSeekChatCompletionResponse.getId());
        chatCompletionResponse.setObject(deepSeekChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(deepSeekChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(deepSeekChatCompletionResponse.getModel());
        chatCompletionResponse.setSystemFingerprint(deepSeekChatCompletionResponse.getSystemFingerprint());
        chatCompletionResponse.setChoices(deepSeekChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(deepSeekChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = deepSeekConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = deepSeekConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);

        // 转换 请求参数
        DeepSeekChatCompletion deepSeekChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(deepSeekChatCompletion.getFunctions()!=null && !deepSeekChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(deepSeekChatCompletion.getFunctions());
            deepSeekChatCompletion.setTools(tools);
        }

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            String requestString = JSON.toJSONString(deepSeekChatCompletion);


            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, deepSeekConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                DeepSeekChatCompletionResponse deepSeekChatCompletionResponse = JSON.parseObject(execute.body().string(), DeepSeekChatCompletionResponse.class);

                Choice choice = deepSeekChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = deepSeekChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(deepSeekChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    deepSeekChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    deepSeekChatCompletionResponse.setUsage(allUsage);
                    //deepSeekChatCompletionResponse.setObject("chat.completion");

                    // 恢复原始请求数据
                    chatCompletion.setMessages(deepSeekChatCompletion.getMessages());
                    chatCompletion.setTools(deepSeekChatCompletion.getTools());

                    return this.convertChatCompletionResponse(deepSeekChatCompletionResponse);

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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = deepSeekConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = deepSeekConfig.getApiKey();
        chatCompletion.setStream(true);

        // 转换 请求参数
        DeepSeekChatCompletion deepSeekChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(deepSeekChatCompletion.getFunctions()!=null && !deepSeekChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(deepSeekChatCompletion.getFunctions());
            deepSeekChatCompletion.setTools(tools);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(deepSeekChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, deepSeekConfig.getChatCompletionUrl()))
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

                List<ChatMessage> messages = new ArrayList<>(deepSeekChatCompletion.getMessages());
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
                deepSeekChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(deepSeekChatCompletion.getMessages());
        chatCompletion.setTools(deepSeekChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
