package io.github.lnyocly.ai4j.platform.moonshot.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.MoonshotConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.moonshot.chat.entity.MoonshotChatCompletion;
import io.github.lnyocly.ai4j.platform.moonshot.chat.entity.MoonshotChatCompletionResponse;
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
 * @Description 月之暗面请求服务
 * @Date 2024/8/29 23:12
 */
public class MoonshotChatService implements IChatService, ParameterConvert<MoonshotChatCompletion>, ResultConvert<MoonshotChatCompletionResponse> {
    private final MoonshotConfig moonshotConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public MoonshotChatService(Configuration configuration) {
        this.moonshotConfig = configuration.getMoonshotConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    @Override
    public MoonshotChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        MoonshotChatCompletion moonshotChatCompletion = new MoonshotChatCompletion();
        moonshotChatCompletion.setModel(chatCompletion.getModel());
        moonshotChatCompletion.setMessages(chatCompletion.getMessages());
        moonshotChatCompletion.setFrequencyPenalty(chatCompletion.getFrequencyPenalty());
        moonshotChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        moonshotChatCompletion.setPresencePenalty(chatCompletion.getPresencePenalty());
        moonshotChatCompletion.setResponseFormat(chatCompletion.getResponseFormat());
        moonshotChatCompletion.setStop(chatCompletion.getStop());
        moonshotChatCompletion.setStream(chatCompletion.getStream());
        moonshotChatCompletion.setTemperature(chatCompletion.getTemperature() / 2);
        moonshotChatCompletion.setTopP(chatCompletion.getTopP());
        moonshotChatCompletion.setTools(chatCompletion.getTools());
        moonshotChatCompletion.setFunctions(chatCompletion.getFunctions());
        moonshotChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        return moonshotChatCompletion;
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
                MoonshotChatCompletionResponse chatCompletionResponse = null;
                String s = null;
                try {
                    chatCompletionResponse = mapper.readValue(data, MoonshotChatCompletionResponse.class);

                    ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);

                    // 适配moonshot的流式 usage格式
                    JSONObject object = (JSONObject)JSONPath.eval(data, "$.choices[0].usage");
                    if(object!=null){
                        Usage usage = object.toJavaObject(Usage.class);
                        response.setUsage(usage);
                    }

                    s = mapper.writeValueAsString(response);
                } catch (JsonProcessingException e) {
                    throw new CommonException("Moonshot Chat 对象JSON序列化出错");
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
    public ChatCompletionResponse convertChatCompletionResponse(MoonshotChatCompletionResponse moonshotChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(moonshotChatCompletionResponse.getId());
        chatCompletionResponse.setObject(moonshotChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(moonshotChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(moonshotChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(moonshotChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(moonshotChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = moonshotConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = moonshotConfig.getApiKey();
        chatCompletion.setStream(false);
        // 转换 请求参数
        MoonshotChatCompletion moonshotChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(moonshotChatCompletion.getFunctions()!=null && !moonshotChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(moonshotChatCompletion.getFunctions());
            moonshotChatCompletion.setTools(tools);
        }

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            String requestString = JSON.toJSONString(moonshotChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, moonshotConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                MoonshotChatCompletionResponse moonshotChatCompletionResponse = JSON.parseObject(execute.body().string(), MoonshotChatCompletionResponse.class);

                Choice choice = moonshotChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = moonshotChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(moonshotChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    moonshotChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    moonshotChatCompletionResponse.setUsage(allUsage);
                    //moonshotChatCompletionResponse.setObject("chat.completion");

                    // 恢复原始请求数据
                    chatCompletion.setMessages(moonshotChatCompletion.getMessages());
                    chatCompletion.setTools(moonshotChatCompletion.getTools());

                    return this.convertChatCompletionResponse(moonshotChatCompletionResponse);

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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = moonshotConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = moonshotConfig.getApiKey();
        chatCompletion.setStream(true);

        // 转换 请求参数
        MoonshotChatCompletion moonshotChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(moonshotChatCompletion.getFunctions()!=null && !moonshotChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(moonshotChatCompletion.getFunctions());
            moonshotChatCompletion.setTools(tools);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(moonshotChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, moonshotConfig.getChatCompletionUrl()))
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

                List<ChatMessage> messages = new ArrayList<>(moonshotChatCompletion.getMessages());
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
                moonshotChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(moonshotChatCompletion.getMessages());
        chatCompletion.setTools(moonshotChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
