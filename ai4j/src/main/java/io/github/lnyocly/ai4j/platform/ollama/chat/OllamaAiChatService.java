package io.github.lnyocly.ai4j.platform.ollama.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.ollama.chat.entity.OllamaChatCompletion;
import io.github.lnyocly.ai4j.platform.ollama.chat.entity.OllamaChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.ollama.chat.entity.OllamaMessage;
import io.github.lnyocly.ai4j.platform.ollama.chat.entity.OllamaOptions;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author cly
 * @Description Ollama Ai聊天对话服务
 * @Date 2024/9/20 0:00
 */
public class OllamaAiChatService implements IChatService, ParameterConvert<OllamaChatCompletion>, ResultConvert<OllamaChatCompletionResponse> {
    private final OllamaConfig ollamaConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public OllamaAiChatService(Configuration configuration) {
        this.ollamaConfig = configuration.getOllamaConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    public OllamaAiChatService(Configuration configuration, OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }


    @Override
    public OllamaChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        OllamaChatCompletion ollamaChatCompletion = new OllamaChatCompletion();
        ollamaChatCompletion.setModel(chatCompletion.getModel());
        ollamaChatCompletion.setTools(chatCompletion.getTools());
        ollamaChatCompletion.setFunctions(chatCompletion.getFunctions());
        ollamaChatCompletion.setStream(chatCompletion.getStream());

        OllamaOptions ollamaOptions = new OllamaOptions();
        ollamaOptions.setTemperature(chatCompletion.getTemperature());
        ollamaOptions.setTopP(chatCompletion.getTopP());
        ollamaOptions.setStop(chatCompletion.getStop());
        ollamaChatCompletion.setOptions(ollamaOptions);

        List<OllamaMessage> messages = new ArrayList<>();
        for (ChatMessage chatMessage : chatCompletion.getMessages()) {
            OllamaMessage ollamaMessage = new OllamaMessage();

            ollamaMessage.setRole(chatMessage.getRole());
            String content = chatMessage.getContent().getText();

            if (content != null){
                // 普通消息
                ollamaMessage.setContent(content);
            }else if (chatMessage.getContent().getMultiModals() != null){
                List<Content.MultiModal> multiModals = chatMessage.getContent().getMultiModals();
                if(multiModals!=null && !multiModals.isEmpty()){
                    List<String> images = new ArrayList<>();
                    for (Content.MultiModal multiModal : multiModals) {
                        String text = multiModal.getText();
                        Content.MultiModal.ImageUrl imageUrl = multiModal.getImageUrl();
                        if(imageUrl!=null) images.add(imageUrl.getUrl());
                        if(StringUtils.isNotBlank(text)) ollamaMessage.setContent(text);
                    }
                    ollamaMessage.setImages(images);
                }
            }

            // 设置toolcalls
            ollamaMessage.setToolCalls(chatMessage.getToolCalls());


            messages.add(ollamaMessage);
        }
        ollamaChatCompletion.setMessages(messages);

        return ollamaChatCompletion;
    }

    public List<ChatMessage> ollamaMessagesToChatMessages(List<OllamaMessage> ollamaMessages){
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (OllamaMessage ollamaMessage : ollamaMessages) {
            chatMessages.add(ollamaMessageToChatMessage(ollamaMessage));
        }
        return chatMessages;
    }

    public ChatMessage ollamaMessageToChatMessage(OllamaMessage ollamaMessage){
        String role = ollamaMessage.getRole();
        List<ToolCall> toolCalls = ollamaMessage.getToolCalls();

        if(ChatMessageType.USER.getRole().equals(role)){
            if(ollamaMessage.getImages()!=null && !ollamaMessage.getImages().isEmpty()){
                // 多模态
                return ChatMessage.withUser(ollamaMessage.getContent(), ollamaMessage.getImages().toArray(new String[0]));
            }else{
                return ChatMessage.withUser(ollamaMessage.getContent());
            }
        } else if (ChatMessageType.ASSISTANT.getRole().equals(role)) {
            if(toolCalls!=null && !toolCalls.isEmpty()) {
                // tool调用
                for (ToolCall toolCall : toolCalls) {
                    toolCall.setType("function");
                    toolCall.setId(UUID.randomUUID().toString());
                }
                return ChatMessage.withAssistant(ollamaMessage.getContent(), toolCalls);
            }else{
                return ChatMessage.withAssistant(ollamaMessage.getContent());
            }

        }else{
            // system和tool消息
            return new ChatMessage(role,ollamaMessage.getContent());
        }

    }

    @Override
    public EventSourceListener convertEventSource(SseListener eventSourceListener) {
        final AtomicBoolean isThinking = new AtomicBoolean(false);

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

                OllamaChatCompletionResponse ollamaChatCompletionResponse = JSON.parseObject(data, OllamaChatCompletionResponse.class);
                OllamaMessage message = ollamaChatCompletionResponse.getMessage();
                String content = message != null ? message.getContent() : null;
                String thinking = message != null ? message.getThinking() : null;

                // 1. 处理 thinking 字段（Ollama Qwen 模式）
                // thinking 字段有值时，直接映射到 reasoning_content
                if (StringUtils.isNotEmpty(thinking)) {
                    ChatCompletionResponse response = convertChatCompletionResponse(ollamaChatCompletionResponse);
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        ChatMessage delta = response.getChoices().get(0).getDelta();
                        delta.setReasoningContent(thinking);
                        delta.setContent(null);
                    }
                    sendConvertedResponse(eventSourceListener, eventSource, id, type, response);
                    return;
                }

                // 2. 处理 <think> 标签（Ollama DeepSeek 模式）
                // 检测到 <think> 标签时，标记进入思考模式，不传递标签本身
                if ("<think>".equals(content)) {
                    isThinking.set(true);
                    return;
                }
                // 检测到 </think> 标签时，标记退出思考模式，不传递标签本身
                if ("</think>".equals(content)) {
                    isThinking.set(false);
                    return;
                }

                // 3. 转换为 OpenAI 格式
                ChatCompletionResponse response = convertChatCompletionResponse(ollamaChatCompletionResponse);

                // 4. 如果处于思考模式，将 content 转换为 reasoning_content
                if (isThinking.get() && StringUtils.isNotEmpty(content)) {
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        ChatMessage delta = response.getChoices().get(0).getDelta();
                        delta.setReasoningContent(content);
                        delta.setContent(null);
                    }
                }

                sendConvertedResponse(eventSourceListener, eventSource, id, type, response);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }
        };
    }

    /**
     * 发送转换后的响应给 SseListener
     */
    private void sendConvertedResponse(SseListener listener, EventSource eventSource, String id, String type, ChatCompletionResponse response) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String s = mapper.writeValueAsString(response);
            listener.onEvent(eventSource, id, type, s);
        } catch (JsonProcessingException e) {
            throw new CommonException("Ollama Chat Completion Response convert to JSON error");
        }
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(OllamaChatCompletionResponse ollamaChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setModel(ollamaChatCompletionResponse.getModel());
        chatCompletionResponse.setId(UUID.randomUUID().toString());
        chatCompletionResponse.setObject("chat.completion");
        Instant instant = Instant.parse(ollamaChatCompletionResponse.getCreatedAt());
        long created = instant.getEpochSecond();
        chatCompletionResponse.setCreated(created);

        Usage usage = new Usage();
        usage.setCompletionTokens(ollamaChatCompletionResponse.getEvalCount());
        usage.setPromptTokens(ollamaChatCompletionResponse.getPromptEvalCount());
        usage.setTotalTokens(ollamaChatCompletionResponse.getEvalCount() + ollamaChatCompletionResponse.getPromptEvalCount());
        chatCompletionResponse.setUsage(usage);

        ChatMessage chatMessage = ollamaMessageToChatMessage(ollamaChatCompletionResponse.getMessage());
        List<Choice> choices = new ArrayList<>(1);
        Choice choice = new Choice();
        choice.setFinishReason(ollamaChatCompletionResponse.getDoneReason());
        choice.setIndex(0);
        choice.setMessage(chatMessage);
        choice.setDelta(chatMessage);
        choices.add(choice);
        chatCompletionResponse.setChoices(choices);

        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = ollamaConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = ollamaConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);

        if((chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()) || (chatCompletion.getMcpServices()!=null && !chatCompletion.getMcpServices().isEmpty())){
            //List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
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
        OllamaChatCompletion ollamaChatCompletion = this.convertChatCompletionObject(chatCompletion);

/*        // 如含有function，则添加tool
        if(ollamaChatCompletion.getFunctions()!=null && !ollamaChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(ollamaChatCompletion.getFunctions());
            ollamaChatCompletion.setTools(tools);
        }*/

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            String requestString = JSON.toJSONString(ollamaChatCompletion);

            JSONObject jsonObject = JSON.parseObject(requestString);
            // 遍历jsonObject的messages
            JSONArray jsonArrayMessages = jsonObject.getJSONArray("messages");
            for (Object message : jsonArrayMessages) {
                JSONObject messageObject = (JSONObject) message;
                JSONArray toolCalls = messageObject.getJSONArray("tool_calls");
                if(toolCalls!=null && !toolCalls.isEmpty()){
                    for (Object toolCall : toolCalls) {
                        // 遍历toolCall中的function中的arguments，将arguments（JSON String）转为对象(JSON Object)
                        JSONObject toolCallObject = (JSONObject) toolCall;
                        JSONObject function = toolCallObject.getJSONObject("function");
                        String arguments = function.getString("arguments");
                        JSONObject argumentsObject = JSON.parseObject(arguments);
                        function.remove("arguments");
                        function.put("arguments", argumentsObject);
                    }
                }
            }
            requestString = JSON.toJSONString(jsonObject);


            Request.Builder builder = new Request.Builder()
                    .url(ValidateUtil.concatUrl(baseUrl, ollamaConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString));

            if(StringUtils.isNotBlank(apiKey)) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            Request request = builder.build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                OllamaChatCompletionResponse ollamaChatCompletionResponse = JSON.parseObject(execute.body().string(), OllamaChatCompletionResponse.class);


                finishReason = ollamaChatCompletionResponse.getDoneReason();

                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + ollamaChatCompletionResponse.getEvalCount());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + ollamaChatCompletionResponse.getEvalCount() + ollamaChatCompletionResponse.getPromptEvalCount());
                allUsage.setPromptTokens(allUsage.getPromptTokens() +  ollamaChatCompletionResponse.getPromptEvalCount());

                List<ToolCall> functions = ollamaChatCompletionResponse.getMessage().getToolCalls();
                if(functions!=null && !functions.isEmpty()){
                    finishReason = "tool_calls";
                }

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    OllamaMessage message = ollamaChatCompletionResponse.getMessage();

                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<OllamaMessage> messages = new ArrayList<>(ollamaChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        OllamaMessage ollamaMessage = new OllamaMessage();
                        ollamaMessage.setRole("tool");
                        ollamaMessage.setContent(functionResponse);

                        messages.add(ollamaMessage);
                    }
                    ollamaChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    ollamaChatCompletionResponse.setEvalCount(allUsage.getCompletionTokens());
                    ollamaChatCompletionResponse.setPromptEvalCount(allUsage.getPromptTokens());

                    // 恢复原始请求数据
                    chatCompletion.setMessages(ollamaMessagesToChatMessages(ollamaChatCompletion.getMessages()));
                    chatCompletion.setTools(ollamaChatCompletion.getTools());

                    return this.convertChatCompletionResponse(ollamaChatCompletionResponse);

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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = ollamaConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = ollamaConfig.getApiKey();
        chatCompletion.setStream(true);

        if((chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()) || (chatCompletion.getMcpServices()!=null && !chatCompletion.getMcpServices().isEmpty())){
            //List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
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
        OllamaChatCompletion ollamaChatCompletion = this.convertChatCompletionObject(chatCompletion);

/*        // 如含有function，则添加tool
        if(ollamaChatCompletion.getFunctions()!=null && !ollamaChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(ollamaChatCompletion.getFunctions());
            ollamaChatCompletion.setTools(tools);
        }*/

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            JSON.toJSONString(ollamaChatCompletion);
            ObjectMapper mapper = new ObjectMapper();
            String requestString = mapper.writeValueAsString(ollamaChatCompletion);


            JSONObject jsonObject = JSON.parseObject(requestString);
            // 遍历jsonObject的messages
            JSONArray jsonArrayMessages = jsonObject.getJSONArray("messages");
            for (Object message : jsonArrayMessages) {
                JSONObject messageObject = (JSONObject) message;
                JSONArray toolCalls = messageObject.getJSONArray("tool_calls");
                if(toolCalls!=null && !toolCalls.isEmpty()){
                    for (Object toolCall : toolCalls) {
                        // 遍历toolCall中的function中的arguments，将arguments（JSON String）转为对象(JSON Object)
                        JSONObject toolCallObject = (JSONObject) toolCall;
                        JSONObject function = toolCallObject.getJSONObject("function");
                        String arguments = function.getString("arguments");
                        JSONObject argumentsObject = JSON.parseObject(arguments);
                        function.remove("arguments");
                        function.put("arguments", argumentsObject);
                    }
                }
            }
            requestString = JSON.toJSONString(jsonObject);

            Request.Builder builder = new Request.Builder()
                    .url(ValidateUtil.concatUrl(baseUrl, ollamaConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString));

            if(StringUtils.isNotBlank(apiKey)) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            Request request = builder.build();

            factory.newEventSource(request, convertEventSource(eventSourceListener));
            eventSourceListener.getCountDownLatch().await();

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if("tool_calls".equals(finishReason) && !toolCalls.isEmpty()){
                // 创建tool响应消息
                OllamaMessage responseMessage = new OllamaMessage();
                responseMessage.setRole(ChatMessageType.ASSISTANT.getRole());
                responseMessage.setToolCalls(eventSourceListener.getToolCalls());

                List<OllamaMessage> messages = new ArrayList<>(ollamaChatCompletion.getMessages());
                messages.add(responseMessage);

                // 封装tool结果消息
                for (ToolCall toolCall : toolCalls) {
                    String functionName = toolCall.getFunction().getName();
                    String arguments = toolCall.getFunction().getArguments();
                    String functionResponse = ToolUtil.invoke(functionName, arguments);

                    OllamaMessage ollamaMessage = new OllamaMessage();
                    ollamaMessage.setRole("tool");
                    ollamaMessage.setContent(functionResponse);

                    messages.add(ollamaMessage);
                }
                eventSourceListener.setToolCalls(new ArrayList<>());
                eventSourceListener.setToolCall(null);
                ollamaChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(ollamaMessagesToChatMessages(ollamaChatCompletion.getMessages()));
        chatCompletion.setTools(ollamaChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
