package io.github.lnyocly.ai4j.platform.doubao.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.doubao.chat.entity.DoubaoChatCompletion;
import io.github.lnyocly.ai4j.platform.doubao.chat.entity.DoubaoChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
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


public class DoubaoChatService implements IChatService, ParameterConvert<DoubaoChatCompletion>, ResultConvert<DoubaoChatCompletionResponse> {
    private final DoubaoConfig doubaoConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public DoubaoChatService(Configuration configuration) {
        this.doubaoConfig = configuration.getDoubaoConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    public DoubaoChatService(Configuration configuration, DoubaoConfig doubaoConfig) {
        this.doubaoConfig = doubaoConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }


    @Override
    public DoubaoChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        DoubaoChatCompletion doubaoChatCompletion = new DoubaoChatCompletion();
        doubaoChatCompletion.setModel(chatCompletion.getModel());
        doubaoChatCompletion.setMessages(chatCompletion.getMessages());

        doubaoChatCompletion.setFrequencyPenalty(chatCompletion.getFrequencyPenalty());
        doubaoChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        if(chatCompletion.getMaxCompletionTokens() != null){
            doubaoChatCompletion.setMaxTokens(chatCompletion.getMaxCompletionTokens());
        }
        doubaoChatCompletion.setPresencePenalty(chatCompletion.getPresencePenalty());
        doubaoChatCompletion.setResponseFormat(chatCompletion.getResponseFormat());
        doubaoChatCompletion.setStop(chatCompletion.getStop());
        doubaoChatCompletion.setStream(chatCompletion.getStream());
        doubaoChatCompletion.setStreamOptions(chatCompletion.getStreamOptions());
        doubaoChatCompletion.setTemperature(chatCompletion.getTemperature());
        doubaoChatCompletion.setTopP(chatCompletion.getTopP());
        doubaoChatCompletion.setTools(chatCompletion.getTools());
        doubaoChatCompletion.setFunctions(chatCompletion.getFunctions());
        doubaoChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        doubaoChatCompletion.setLogprobs(chatCompletion.getLogprobs());
        doubaoChatCompletion.setTopLogprobs(chatCompletion.getTopLogprobs());
        doubaoChatCompletion.setExtraBody(chatCompletion.getExtraBody());
        return doubaoChatCompletion;
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

                ObjectMapper mapper = new ObjectMapper();
                DoubaoChatCompletionResponse chatCompletionResponse = null;
                String s = null;
                try {
                    chatCompletionResponse = mapper.readValue(data, DoubaoChatCompletionResponse.class);

                    // 处理豆包的 reasoning_content 字段，转换为 ChatMessage 的 reasoningContent
                    if (chatCompletionResponse.getChoices() != null && !chatCompletionResponse.getChoices().isEmpty()) {
                        ChatMessage delta = chatCompletionResponse.getChoices().get(0).getDelta();
                        if (delta != null && delta.getReasoningContent() != null && !delta.getReasoningContent().isEmpty()) {
                            // 豆包返回思考内容时，content为空字符串，需要特殊处理
                            if (delta.getContent() == null || (delta.getContent() != null && delta.getContent().getText() != null && delta.getContent().getText().isEmpty())) {
                                // 保留 reasoning_content，内容由 SseListener 处理
                            }
                        }
                    }

                    ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);
                    s = mapper.writeValueAsString(response);

                } catch (JsonProcessingException e) {
                    throw new CommonException("读取豆包 Chat 对象JSON序列化出错");
                }

                eventSourceListener.onEvent(eventSource, id, type, s);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }
        };
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(DoubaoChatCompletionResponse doubaoChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(doubaoChatCompletionResponse.getId());
        chatCompletionResponse.setObject(doubaoChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(doubaoChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(doubaoChatCompletionResponse.getModel());
        chatCompletionResponse.setSystemFingerprint(doubaoChatCompletionResponse.getSystemFingerprint());
        chatCompletionResponse.setChoices(doubaoChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(doubaoChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = doubaoConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = doubaoConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);

        if((chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()) || (chatCompletion.getMcpServices()!=null && !chatCompletion.getMcpServices().isEmpty())){
            List<Tool> tools = ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices());
            chatCompletion.setTools(tools);
            if(tools == null){
                chatCompletion.setParallelToolCalls(null);
            }
        }
        if (chatCompletion.getTools()!=null && !chatCompletion.getTools().isEmpty()){

        }else{
            chatCompletion.setParallelToolCalls(null);
        }


        // 转换 请求参数
        DoubaoChatCompletion doubaoChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            ObjectMapper mapper = new ObjectMapper();
            String requestString = mapper.writeValueAsString(doubaoChatCompletion);


            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, doubaoConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){

                DoubaoChatCompletionResponse doubaoChatCompletionResponse = mapper.readValue(execute.body().string(), DoubaoChatCompletionResponse.class);

                Choice choice = doubaoChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = doubaoChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(doubaoChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    doubaoChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    doubaoChatCompletionResponse.setUsage(allUsage);

                    // 恢复原始请求数据
                    chatCompletion.setMessages(doubaoChatCompletion.getMessages());
                    chatCompletion.setTools(doubaoChatCompletion.getTools());

                    return this.convertChatCompletionResponse(doubaoChatCompletionResponse);

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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = doubaoConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = doubaoConfig.getApiKey();
        chatCompletion.setStream(true);
        StreamOptions streamOptions = chatCompletion.getStreamOptions();
        if(streamOptions == null){
            chatCompletion.setStreamOptions(new StreamOptions(true));
        }
        if((chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()) || (chatCompletion.getMcpServices()!=null && !chatCompletion.getMcpServices().isEmpty())){
            List<Tool> tools = ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices());
            chatCompletion.setTools(tools);
            if(tools == null){
                chatCompletion.setParallelToolCalls(null);
            }
        }
        if (chatCompletion.getTools()!=null && !chatCompletion.getTools().isEmpty()){

        }else{
            chatCompletion.setParallelToolCalls(null);
        }


        // 转换 请求参数
        DoubaoChatCompletion doubaoChatCompletion = this.convertChatCompletionObject(chatCompletion);

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = mapper.writeValueAsString(doubaoChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, doubaoConfig.getChatCompletionUrl()))
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

                List<ChatMessage> messages = new ArrayList<>(doubaoChatCompletion.getMessages());
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
                doubaoChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(doubaoChatCompletion.getMessages());
        chatCompletion.setTools(doubaoChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
