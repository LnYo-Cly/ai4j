package io.github.lnyocly.ai4j.platform.baichuan.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.BaichuanConfig;
import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.baichuan.chat.entity.BaichuanChatCompletion;
import io.github.lnyocly.ai4j.platform.baichuan.chat.entity.BaichuanChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.platform.zhipu.chat.entity.ZhipuChatCompletion;
import io.github.lnyocly.ai4j.platform.zhipu.chat.entity.ZhipuChatCompletionResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.utils.BearerTokenUtils;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BaichuanChatService implements IChatService, ParameterConvert<BaichuanChatCompletion>, ResultConvert<BaichuanChatCompletionResponse> {

    private final BaichuanConfig baichuanConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public BaichuanChatService(Configuration configuration) {
        this.baichuanConfig = configuration.getBaichuanConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    @Override
    public BaichuanChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        BaichuanChatCompletion baichuanChatCompletion = new BaichuanChatCompletion();
        baichuanChatCompletion.setModel(chatCompletion.getModel());
        baichuanChatCompletion.setMessages(chatCompletion.getMessages());
        baichuanChatCompletion.setStream(chatCompletion.getStream());
        baichuanChatCompletion.setTemperature(chatCompletion.getTemperature());
        baichuanChatCompletion.setTopP(chatCompletion.getTopP());
        baichuanChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        baichuanChatCompletion.setStop(chatCompletion.getStop());
        baichuanChatCompletion.setTools(chatCompletion.getTools());
        baichuanChatCompletion.setFunctions(chatCompletion.getFunctions());
        baichuanChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        return baichuanChatCompletion;
    }

    @Override
    public EventSourceListener convertEventSource(SseListener eventSourceListener) {
        return new EventSourceListener() {
            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                if ("[DONE]".equalsIgnoreCase(data)) {
                    eventSourceListener.onEvent(eventSource, id, type, data);
                    return;
                }

                BaichuanChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, BaichuanChatCompletionResponse.class);
                chatCompletionResponse.setObject("chat.completion.chunk");
                ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);

                eventSourceListener.onEvent(eventSource, id, type, JSON.toJSONString(response));
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                eventSourceListener.onFailure(eventSource, t, response);
            }

            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                eventSourceListener.onOpen(eventSource, response);
            }
        };
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(BaichuanChatCompletionResponse baichuanChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(baichuanChatCompletionResponse.getId());
        chatCompletionResponse.setCreated(baichuanChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(baichuanChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(baichuanChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(baichuanChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = baichuanConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = baichuanConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);


        // 转换 请求参数
        BaichuanChatCompletion baichuanChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(baichuanChatCompletion.getFunctions()!=null && !baichuanChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(baichuanChatCompletion.getFunctions());
            baichuanChatCompletion.setTools(tools);
        }

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            // 构造请求
            String requestString = JSON.toJSONString(baichuanChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, baichuanConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                BaichuanChatCompletionResponse baichuanChatCompletionResponse = JSON.parseObject(execute.body().string(), BaichuanChatCompletionResponse.class);

                Choice choice = baichuanChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = baichuanChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(baichuanChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    baichuanChatCompletion.setMessages(messages);

                }else{
                    // 其他情况直接返回
                    baichuanChatCompletionResponse.setUsage(allUsage);
                    baichuanChatCompletionResponse.setObject("chat.completion");
                    // 恢复原始请求数据
                    chatCompletion.setMessages(baichuanChatCompletion.getMessages());
                    chatCompletion.setTools(baichuanChatCompletion.getTools());

                    return this.convertChatCompletionResponse(baichuanChatCompletionResponse);
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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = baichuanConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = baichuanConfig.getApiKey();
        chatCompletion.setStream(true);


        // 转换 请求参数
        BaichuanChatCompletion baichuanChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(baichuanChatCompletion.getFunctions() != null && !baichuanChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(baichuanChatCompletion.getFunctions());
            baichuanChatCompletion.setTools(tools);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(baichuanChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, baichuanConfig.getChatCompletionUrl()))
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

                List<ChatMessage> messages = new ArrayList<>(baichuanChatCompletion.getMessages());
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
                baichuanChatCompletion.setMessages(messages);
            }
        }

        // 补全原始请求
        chatCompletion.setMessages(baichuanChatCompletion.getMessages());
        chatCompletion.setTools(baichuanChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
