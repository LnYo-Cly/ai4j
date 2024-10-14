package io.github.lnyocly.ai4j.platform.zhipu.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
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
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description 智谱chat服务
 * @Date 2024/8/27 17:29
 */
@Slf4j
public class ZhipuChatService implements IChatService, ParameterConvert<ZhipuChatCompletion>, ResultConvert<ZhipuChatCompletionResponse> {

    private final ZhipuConfig zhipuConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public ZhipuChatService(Configuration configuration) {
        this.zhipuConfig = configuration.getZhipuConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }


    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = zhipuConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = zhipuConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);


        // 根据key获取token
        String token = BearerTokenUtils.getToken(apiKey);

        // 转换 请求参数
        ZhipuChatCompletion zhipuChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(zhipuChatCompletion.getFunctions()!=null && !zhipuChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(zhipuChatCompletion.getFunctions());
            zhipuChatCompletion.setTools(tools);
        }

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            // 构造请求
            String requestString = JSON.toJSONString(zhipuChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + token)
                    .url(ValidateUtil.concatUrl(baseUrl, zhipuConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                ZhipuChatCompletionResponse zhipuChatCompletionResponse = JSON.parseObject(execute.body().string(), ZhipuChatCompletionResponse.class);

                Choice choice = zhipuChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = zhipuChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(zhipuChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    zhipuChatCompletion.setMessages(messages);

                }else{
                    // 其他情况直接返回
                    zhipuChatCompletionResponse.setUsage(allUsage);
                    zhipuChatCompletionResponse.setObject("chat.completion");
                    // 恢复原始请求数据
                    chatCompletion.setMessages(zhipuChatCompletion.getMessages());
                    chatCompletion.setTools(zhipuChatCompletion.getTools());

                    return this.convertChatCompletionResponse(zhipuChatCompletionResponse);

                }

            }

        }


        return null;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = zhipuConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = zhipuConfig.getApiKey();
        chatCompletion.setStream(true);


        // 根据key获取token
        String token = BearerTokenUtils.getToken(apiKey);

        // 转换 请求参数
        ZhipuChatCompletion zhipuChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
        if(zhipuChatCompletion.getFunctions()!=null && !zhipuChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(zhipuChatCompletion.getFunctions());
            zhipuChatCompletion.setTools(tools);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(zhipuChatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + token)
                    .url(ValidateUtil.concatUrl(baseUrl, zhipuConfig.getChatCompletionUrl()))
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

                List<ChatMessage> messages = new ArrayList<>(zhipuChatCompletion.getMessages());
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
                zhipuChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(zhipuChatCompletion.getMessages());
        chatCompletion.setTools(zhipuChatCompletion.getTools());
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    @Override
    public ZhipuChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        ZhipuChatCompletion zhipuChatCompletion = new ZhipuChatCompletion();
        zhipuChatCompletion.setModel(chatCompletion.getModel());
        zhipuChatCompletion.setMessages(chatCompletion.getMessages());
        zhipuChatCompletion.setStream(chatCompletion.getStream());
        zhipuChatCompletion.setTemperature(chatCompletion.getTemperature() / 2);
        zhipuChatCompletion.setTopP(chatCompletion.getTopP());
        zhipuChatCompletion.setMaxTokens(chatCompletion.getMaxTokens());
        zhipuChatCompletion.setStop(chatCompletion.getStop());
        zhipuChatCompletion.setTools(chatCompletion.getTools());
        zhipuChatCompletion.setFunctions(chatCompletion.getFunctions());
        zhipuChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        return zhipuChatCompletion;
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

                ZhipuChatCompletionResponse chatCompletionResponse = JSON.parseObject(data, ZhipuChatCompletionResponse.class);
                chatCompletionResponse.setObject("chat.completion.chunk");
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
    public ChatCompletionResponse convertChatCompletionResponse(ZhipuChatCompletionResponse zhipuChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(zhipuChatCompletionResponse.getId());
        chatCompletionResponse.setCreated(zhipuChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(zhipuChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(zhipuChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(zhipuChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }
}
