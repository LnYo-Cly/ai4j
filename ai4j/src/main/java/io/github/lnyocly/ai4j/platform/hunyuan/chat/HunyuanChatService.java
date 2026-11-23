package io.github.lnyocly.ai4j.platform.hunyuan.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.HunyuanConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.platform.hunyuan.HunyuanConstant;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.entity.HunyuanChatCompletion;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.entity.HunyuanChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.auth.BearerTokenUtils;
import io.github.lnyocly.ai4j.platform.hunyuan.support.HunyuanJsonUtil;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description 腾讯混元 Chat 服务
 * @Date 2024/8/30 19:24
 */
public class HunyuanChatService implements IChatService, ParameterConvert<HunyuanChatCompletion>, ResultConvert<HunyuanChatCompletionResponse> {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

    private final HunyuanConfig hunyuanConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public HunyuanChatService(Configuration configuration) {
        this.hunyuanConfig = configuration.getHunyuanConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    public HunyuanChatService(Configuration configuration, HunyuanConfig hunyuanConfig) {
        this.hunyuanConfig = hunyuanConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }


    @Override
    public HunyuanChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        HunyuanChatCompletion hunyuanChatCompletion = new HunyuanChatCompletion();
        hunyuanChatCompletion.setModel(chatCompletion.getModel());
        hunyuanChatCompletion.setMessages(chatCompletion.getMessages());
        hunyuanChatCompletion.setStream(chatCompletion.getStream());
        hunyuanChatCompletion.setTemperature(chatCompletion.getTemperature());
        hunyuanChatCompletion.setTopP(chatCompletion.getTopP());
        hunyuanChatCompletion.setTools(chatCompletion.getTools());
        hunyuanChatCompletion.setFunctions(chatCompletion.getFunctions());
        hunyuanChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        hunyuanChatCompletion.setExtraBody(chatCompletion.getExtraBody());
        return hunyuanChatCompletion;
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

                ObjectMapper mapper = JsonCodec.mapper();
                HunyuanChatCompletionResponse hunyuanChatCompletionResponse = null;
                try {
                    hunyuanChatCompletionResponse = mapper.readValue(HunyuanJsonUtil.toSnakeCaseJson(data), HunyuanChatCompletionResponse.class);
                } catch (Exception e) {
                    throw new CommonException("解析混元Hunyuan Chat Completion Response失败");
                }


                ChatCompletionResponse response = convertChatCompletionResponse(hunyuanChatCompletionResponse);
                response.setObject("chat.completion.chunk");

                Choice choice = response.getChoices().get(0);
                if(eventSourceListener.getToolCall()!=null){
                    if(choice.getDelta().getToolCalls()!=null){
                        choice.getDelta().getToolCalls().get(0).setId(null);
                    }
                }

                if(StringUtils.isBlank(choice.getFinishReason())){
                    response.setUsage(null);
                }


                if("tool_calls".equals(choice.getFinishReason())){
                    //eventSourceListener.setToolCall(null);
                    //this.onClosed(eventSource);
                }

                eventSourceListener.onEvent(eventSource, id, type, JsonCodec.toJson(response));
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }
        };
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(HunyuanChatCompletionResponse hunyuanChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(hunyuanChatCompletionResponse.getId());
        chatCompletionResponse.setObject(hunyuanChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(Long.valueOf(hunyuanChatCompletionResponse.getCreated()));
        chatCompletionResponse.setModel(hunyuanChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(hunyuanChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(hunyuanChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        ToolUtil.pushBuiltInToolContext(chatCompletion.getBuiltInToolContext());
        try {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = hunyuanConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = hunyuanConfig.getApiKey();
        boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());
        chatCompletion.setStream(false);


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
        HunyuanChatCompletion hunyuanChatCompletion = this.convertChatCompletionObject(chatCompletion);

        // 如含有function，则添加tool
/*        if(hunyuanChatCompletion.getFunctions()!=null && !hunyuanChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(hunyuanChatCompletion.getFunctions());
            hunyuanChatCompletion.setTools(tools);
        }*/

        // 总token消耗
        Usage allUsage = new Usage();

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            ObjectMapper mapper = JsonCodec.mapper();
            String requestString = buildHunyuanRequestBody(hunyuanChatCompletion, chatCompletion.getModel(), mapper);
            String authorization = BearerTokenUtils.getAuthorization(apiKey,HunyuanConstant.ChatCompletions,requestString);

            Request request = new Request.Builder()
                    .header("Authorization", authorization)
                    .header("X-TC-Action", HunyuanConstant.ChatCompletions)
                    .header("X-TC-Version", HunyuanConstant.Version)
                    .header("X-TC-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .url(baseUrl)
                    .post(RequestBody.create(requestString, JSON_MEDIA_TYPE))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                String responseString = execute.body().string();
                responseString = HunyuanJsonUtil.toSnakeCaseJson(responseString);
                JsonNode responseNode = JsonCodec.readTree(responseString).get("response");
                responseString = responseNode == null ? "{}" : responseNode.toString();

                HunyuanChatCompletionResponse hunyuanChatCompletionResponse = mapper.readValue(responseString, HunyuanChatCompletionResponse.class);

                Choice choice = hunyuanChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = hunyuanChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
                    if (passThroughToolCalls) {
                        hunyuanChatCompletionResponse.setUsage(allUsage);
                        hunyuanChatCompletionResponse.setObject("chat.completion");
                        hunyuanChatCompletionResponse.setModel(hunyuanChatCompletion.getModel());
                        return this.convertChatCompletionResponse(hunyuanChatCompletionResponse);
                    }
                    ChatMessage message = choice.getMessage();
                    List<ToolCall> toolCalls = message.getToolCalls();

                    List<ChatMessage> messages = new ArrayList<>(hunyuanChatCompletion.getMessages());
                    messages.add(message);

                    // 添加 tool 消息
                    for (ToolCall toolCall : toolCalls) {
                        String functionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);

                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                    hunyuanChatCompletion.setMessages(messages);

                }else{// 其他情况直接返回

                    // 设置包含tool的总token数
                    hunyuanChatCompletionResponse.setUsage(allUsage);
                    hunyuanChatCompletionResponse.setObject("chat.completion");
                    hunyuanChatCompletionResponse.setModel(hunyuanChatCompletion.getModel());

                    // 恢复原始请求数据
                    chatCompletion.setMessages(hunyuanChatCompletion.getMessages());
                    chatCompletion.setTools(hunyuanChatCompletion.getTools());

                    return this.convertChatCompletionResponse(hunyuanChatCompletionResponse);

                }

            } else {
                throw HttpErrorDecoder.decode(execute);
            }

        }



        return null;
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return this.chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        ToolUtil.pushBuiltInToolContext(chatCompletion.getBuiltInToolContext());
        try {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = hunyuanConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = hunyuanConfig.getApiKey();
        chatCompletion.setStream(true);
        boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());

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
        HunyuanChatCompletion hunyuanChatCompletion = this.convertChatCompletionObject(chatCompletion);

/*        // 如含有function，则添加tool
        if(hunyuanChatCompletion.getFunctions()!=null && !hunyuanChatCompletion.getFunctions().isEmpty()){
            List<Tool> tools = ToolUtil.getAllFunctionTools(hunyuanChatCompletion.getFunctions());
            hunyuanChatCompletion.setTools(tools);
        }*/

        String finishReason = "first";

        while("first".equals(finishReason) || "tool_calls".equals(finishReason)){

            finishReason = null;

            // 构造请求
            ObjectMapper mapper = JsonCodec.mapper();
            String requestString = buildHunyuanRequestBody(hunyuanChatCompletion, chatCompletion.getModel(), mapper);
            String authorization = BearerTokenUtils.getAuthorization(apiKey,HunyuanConstant.ChatCompletions,requestString);

            Request request = new Request.Builder()
                    .header("Authorization", authorization)
                    .header("X-TC-Action", HunyuanConstant.ChatCompletions)
                    .header("X-TC-Version", HunyuanConstant.Version)
                    .header("X-TC-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .header("Accept", Constants.SSE_CONTENT_TYPE)
                    .url(baseUrl)
                    .post(RequestBody.create(requestString, JSON_MEDIA_TYPE))
                    .build();

            StreamExecutionSupport.execute(
                    eventSourceListener,
                    chatCompletion.getStreamExecution(),
                    () -> factory.newEventSource(request, convertEventSource(eventSourceListener))
            );

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if("tool_calls".equals(finishReason) && !toolCalls.isEmpty()){
                if (passThroughToolCalls) {
                    return;
                }
                // 创建tool响应消息
                ChatMessage responseMessage = ChatMessage.withAssistant(eventSourceListener.getToolCalls());
                responseMessage.setContent(Content.ofText(" "));

                List<ChatMessage> messages = new ArrayList<>(hunyuanChatCompletion.getMessages());
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
                hunyuanChatCompletion.setMessages(messages);
            }

        }

        // 补全原始请求
        chatCompletion.setMessages(hunyuanChatCompletion.getMessages());
        chatCompletion.setTools(hunyuanChatCompletion.getTools());
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    private String buildHunyuanRequestBody(HunyuanChatCompletion hunyuanChatCompletion,
                                          String model,
                                          ObjectMapper mapper) throws Exception {
        String requestString = mapper.writeValueAsString(hunyuanChatCompletion);
        JsonNode rootNode = JsonCodec.readTree(requestString);
        if (!(rootNode instanceof ObjectNode)) {
            return HunyuanJsonUtil.toCamelCaseWithUppercaseJson(requestString);
        }
        ObjectNode jsonObject = (ObjectNode) rootNode;

        JsonNode toolsNode = jsonObject.get("tools");
        if (toolsNode != null && toolsNode.isArray() && !toolsNode.isEmpty()) {
            for (JsonNode toolNode : toolsNode) {
                if (!(toolNode instanceof ObjectNode)) {
                    continue;
                }
                ObjectNode tool = (ObjectNode) toolNode;
                JsonNode functionNode = tool.get("function");
                if (functionNode != null && functionNode.isObject()) {
                    ObjectNode newFunction = JsonCodec.createObjectNode();
                    if (functionNode.has("name")) {
                        newFunction.set("name", functionNode.get("name"));
                    }
                    if (functionNode.has("description")) {
                        newFunction.set("description", functionNode.get("description"));
                    }
                    JsonNode params = functionNode.get("parameters");
                    newFunction.put("parameters", params == null || params.isNull() ? null : params.toString());
                    tool.set("function", newFunction);
                }
                tool.put("type", "function");
            }
        }

        if ("hunyuan-vision".equals(model)) {
            JsonNode messagesNode = jsonObject.get("messages");
            if (messagesNode != null && messagesNode.isArray() && !messagesNode.isEmpty()) {
                for (JsonNode msgNode : messagesNode) {
                    if (!(msgNode instanceof ObjectNode)) {
                        continue;
                    }
                    ObjectNode message = (ObjectNode) msgNode;
                    JsonNode contentNode = message.get("content");
                    List<Content.MultiModal> multiModals = null;
                    if (contentNode != null && contentNode.isArray()) {
                        multiModals = JsonCodec.mapper().convertValue(
                                contentNode,
                                new TypeReference<List<Content.MultiModal>>() {
                                }
                        );
                    } else if (contentNode != null && contentNode.isTextual()) {
                        String content = contentNode.asText();
                        if (content.startsWith("[") && content.endsWith("]")) {
                            multiModals = JsonCodec.fromJson(
                                    content,
                                    new TypeReference<List<Content.MultiModal>>() {
                                    }
                            );
                        }
                    }
                    if (multiModals != null && !multiModals.isEmpty()) {
                        message.set("contents", JsonCodec.valueToTree(multiModals));
                        message.remove("content");
                    }
                }
            }
        }

        return HunyuanJsonUtil.toCamelCaseWithUppercaseJson(jsonObject.toString());
    }
}


