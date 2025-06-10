package io.github.lnyocly.ai4j.platform.siliconFlow.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.SiliconFlowConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.ollama.chat.entity.OllamaChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity.SiliconChatCompletion;
import io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity.SiliconChoice;
import io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity.SiliconMessage;
import io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity.SiliconChatCompletionResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class SiliconChatService implements IChatService, ParameterConvert<SiliconChatCompletion>, ResultConvert<SiliconChatCompletionResponse> {

    private final SiliconFlowConfig siliconFlowConfig;

    private String apiUrl;
    private String apiKey;
    private String modelName;

    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public SiliconChatService(Configuration configuration){
        this.siliconFlowConfig = configuration.getSiliconFlowConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = siliconFlowConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = siliconFlowConfig.getApiKey();
        chatCompletion.setStream(false);
        chatCompletion.setStreamOptions(null);

        SiliconChatCompletion siliconChatCompletion = convertChatCompletionObject(chatCompletion);

        // TODO 添加FunctionTool的功能

        // 总token消耗
        Usage allUsage = new Usage();
        // 将请求内容转换成Json格式
        String requestBodyString = JSON.toJSONString(siliconChatCompletion);
        // 构造请求体
        Request.Builder builder = new Request.Builder()
                .url(siliconFlowConfig.getApiHost())
                .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestBodyString));

        // 设置Key
        if(StringUtils.isNotBlank(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        // 构造请求
        Request request = builder.build();
//        System.out.println("Silicon的请求体：" + request.toString());
//        System.out.println("Silicon的requestBody" + requestBodyString);
        Response execute = okHttpClient.newCall(request).execute();
        // 处理返回结果
        if (execute.isSuccessful() && execute.body() != null){
            // 将Json字符串转化成siliconChatCompletionResponse
            SiliconChatCompletionResponse siliconChatCompletionResponse = JSON.parseObject(execute.body().string(), SiliconChatCompletionResponse.class);
            System.out.println("SiliconResponse：" + siliconChatCompletionResponse);
            // 统计使用的token
            allUsage.setCompletionTokens(allUsage.getCompletionTokens() + siliconChatCompletionResponse.getUsage().getCompletionTokens());
            allUsage.setTotalTokens(allUsage.getTotalTokens() + siliconChatCompletionResponse.getUsage().getTotalTokens());
            allUsage.setPromptTokens(allUsage.getPromptTokens() + siliconChatCompletionResponse.getUsage().getPromptTokens());

            // 转换成OpenAi的返回格式
            ChatCompletionResponse chatCompletionResponse = convertChatCompletionResponse(siliconChatCompletionResponse);

            return chatCompletionResponse;
        }
        return null;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return chatCompletion(null,null,chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        throw new Exception("暂不支持");
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        throw new Exception("暂不支持");
    }

    @Override
    public SiliconChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        // 将统一的OpenAi格式，转化为硅基流动的请求格式
        SiliconChatCompletion siliconChatCompletion = new SiliconChatCompletion();
        // 设置调用的模型
        siliconChatCompletion.setModel(chatCompletion.getModel());
        // 其他需要同步的参数
        // siliconChatCompletion.setFrequencyPenalty(chatCompletion.getFrequencyPenalty());
        // ...

        // 将OpenAi的消息格式转换成Silicon的格式
        List<SiliconMessage> siliconMessages = new ArrayList<>();
        for (ChatMessage message : chatCompletion.getMessages()){
            SiliconMessage siliconMessage = new SiliconMessage();
            siliconMessage.setRole(message.getRole());
            siliconMessage.setContent(message.getContent().getText());
            siliconMessages.add(siliconMessage);
        }

        siliconChatCompletion.setMessages(siliconMessages);

        return siliconChatCompletion;
    }

    @Override
    public EventSourceListener convertEventSource(SseListener eventSourceListener) {
        // 还没用到
        return null;
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(SiliconChatCompletionResponse siliconChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setModel(siliconChatCompletionResponse.getModel());
        chatCompletionResponse.setId(siliconChatCompletionResponse.getId());
        chatCompletionResponse.setObject(siliconChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(siliconChatCompletionResponse.getCreated());

        List<Choice> choices = new ArrayList<Choice>();
        for (SiliconChoice siliconChoice : siliconChatCompletionResponse.getChoices()){
            // 将siliconChoice转化为Choice
            choices.add(siliconChoice2Choice(siliconChoice));
        }
        return null;
    }
    private Choice siliconChoice2Choice(SiliconChoice siliconChoice){
        Choice choice= new Choice();
        choice.setFinishReason(siliconChoice.getFinish_reason());
        choice.setMessage(siliconMessage2chatMessage(siliconChoice.getSiliconMessage()));
        return null;
    }
    private ChatMessage siliconMessage2chatMessage(SiliconMessage siliconMessage){
        ChatMessage chatMessage = new ChatMessage(siliconMessage.getRole(),siliconMessage.getContent());
        return chatMessage;
    }
}
