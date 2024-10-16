package io.github.lnyocly.ai4j.platform.openai.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;

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
        chatCompletion.setStreamOptions(null);

        if(chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
            chatCompletion.setTools(tools);
            if(tools == null){
                chatCompletion.setParallelToolCalls(null);
            }
        }else{
            chatCompletion.setParallelToolCalls(null);
        }


        // 总token消耗
        Usage allUsage = new Usage();
        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            String requestString = JSON.toJSONString(chatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, openAiConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                ChatCompletionResponse chatCompletionResponse = JSON.parseObject(execute.body().string(), ChatCompletionResponse.class);

                Choice choice = chatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = chatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(chatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    chatCompletion.setMessages(messages);

                }else{
                    // 其他情况直接返回
                    chatCompletionResponse.setUsage(allUsage);


                    return chatCompletionResponse;

                }

            }else{
                return null;
            }

        }


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
        StreamOptions streamOptions = chatCompletion.getStreamOptions();
        if(streamOptions == null){
            chatCompletion.setStreamOptions(new StreamOptions(true));
        }

        if(chatCompletion.getFunctions()!=null && !chatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
            chatCompletion.setTools(tools);
            if(tools == null){
                chatCompletion.setParallelToolCalls(null);
            }
        }else{
            chatCompletion.setParallelToolCalls(null);
        }

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;
            String jsonString = JSON.toJSONString(chatCompletion);

            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .url(ValidateUtil.concatUrl(baseUrl, openAiConfig.getChatCompletionUrl()))
                    .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), jsonString))
                    .build();


            factory.newEventSource(request, eventSourceListener);
            eventSourceListener.getCountDownLatch().await();

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if("tool_calls".equals(finishReason) && !toolCalls.isEmpty()){
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
                eventSourceListener.setToolCalls(new ArrayList<>());
                eventSourceListener.setToolCall(null);
                chatCompletion.setMessages(messages);
            }

        }

    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
