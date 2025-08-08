package io.github.lnyocly.ai4j.platform.hunyuan.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.HunyuanConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.hunyuan.HunyuanConstant;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.entity.HunyuanChatCompletion;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.entity.HunyuanChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.utils.BearerTokenUtils;
import io.github.lnyocly.ai4j.utils.JsonObjectUtil;
import io.github.lnyocly.ai4j.utils.ToolUtil;
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
    private final HunyuanConfig hunyuanConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public HunyuanChatService(Configuration configuration) {
        this.hunyuanConfig = configuration.getHunyuanConfig();
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

                ObjectMapper mapper = new ObjectMapper();
                HunyuanChatCompletionResponse hunyuanChatCompletionResponse = null;
                try {
                    hunyuanChatCompletionResponse = mapper.readValue(JsonObjectUtil.toSnakeCaseJson(data), HunyuanChatCompletionResponse.class);
                } catch (JsonProcessingException e) {
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

                eventSourceListener.onEvent(eventSource, id, type, JSON.toJSONString(response));
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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = hunyuanConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = hunyuanConfig.getApiKey();
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
            ObjectMapper mapper = new ObjectMapper();
            String requestString = mapper.writeValueAsString(hunyuanChatCompletion);

            // 整理tools
            JSONObject jsonObject = JSON.parseObject(requestString);
            // 获取 Tools 数组
            JSONArray toolsArray = jsonObject.getJSONArray("tools");
            if(toolsArray!= null && !toolsArray.isEmpty()){
                // 遍历并修改 Tools 中的每个对象
                for (int i = 0; i < toolsArray.size(); i++) {
                    JSONObject tool = toolsArray.getJSONObject(i);

                    // 重新构建 Function 对象
                    JSONObject function = tool.getJSONObject("function");
                    JSONObject newFunction = new JSONObject();

                    newFunction.put("name", function.getString("name"));
                    newFunction.put("description", function.getString("description"));
                    newFunction.put("parameters", function.getJSONObject("parameters").toJSONString());

                    // 替换旧的 Function 对象
                    tool.put("function", newFunction);
                    tool.put("type", "function");
                }
            }

            /**
             * Messages 中 Contents 字段仅 hunyuan-vision 模型支持
             * hunyuan模型，识图多模态类只能放在Contents字段
             */
            if("hunyuan-vision".equals(chatCompletion.getModel())){
                // 获取所有的content字段
                JSONArray messagesArray = jsonObject.getJSONArray("messages");
                if(messagesArray!= null && !messagesArray.isEmpty()){
                    for (int i = 0; i < messagesArray.size(); i++) {
                        JSONObject message = messagesArray.getJSONObject(i);
                        // 获取当前message的content字段
                        String content = message.getString("content");
                        // 将content内容，判断是否可以转换为ChatMessage.MultiModal类型
                        if(content!=null && content.startsWith("[") && content.endsWith("]")) {
                            List<Content.MultiModal> multiModals = JSON.parseArray(content, Content.MultiModal.class);
                            if(multiModals!=null && !multiModals.isEmpty()){
                                // 将当前的content转换为contents
                                message.put("contents", multiModals);
                                // 删除原来的content key
                                message.remove("content");
                            }
                        }
                    }
                }
            }

            // 将修改后的 JSON 对象转为字符串
            requestString = jsonObject.toJSONString();
            requestString = JsonObjectUtil.toCamelCaseWithUppercaseJson(requestString);
            String authorization = BearerTokenUtils.getAuthorization(apiKey,HunyuanConstant.ChatCompletions,requestString);

            Request request = new Request.Builder()
                    .header("Authorization", authorization)
                    .header("X-TC-Action", HunyuanConstant.ChatCompletions)
                    .header("X-TC-Version", HunyuanConstant.Version)
                    .header("X-TC-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .url(baseUrl)
                    .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                    .build();

            Response execute = okHttpClient.newCall(request).execute();
            if (execute.isSuccessful() && execute.body() != null){
                String responseString = execute.body().string();
                responseString = JsonObjectUtil.toSnakeCaseJson(responseString);
                responseString = JSON.parseObject(responseString).get("response").toString();

                HunyuanChatCompletionResponse hunyuanChatCompletionResponse = mapper.readValue(responseString, HunyuanChatCompletionResponse.class);

                Choice choice = hunyuanChatCompletionResponse.getChoices().get(0);
                finishReason = choice.getFinishReason();

                Usage usage = hunyuanChatCompletionResponse.getUsage();
                allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getCompletionTokens());
                allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
                allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getPromptTokens());

                // 判断是否为函数调用返回
                if("tool_calls".equals(finishReason)){
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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = hunyuanConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = hunyuanConfig.getApiKey();
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
            ObjectMapper mapper = new ObjectMapper();
            String requestString = mapper.writeValueAsString(hunyuanChatCompletion);

            // 整理tools
            JSONObject jsonObject = JSON.parseObject(requestString);
            // 获取 Tools 数组
            JSONArray toolsArray = jsonObject.getJSONArray("tools");
            if(toolsArray!= null && !toolsArray.isEmpty()){
                // 遍历并修改 Tools 中的每个对象
                for (int i = 0; i < toolsArray.size(); i++) {
                    JSONObject tool = toolsArray.getJSONObject(i);

                    // 重新构建 Function 对象
                    JSONObject function = tool.getJSONObject("function");
                    JSONObject newFunction = new JSONObject();

                    newFunction.put("name", function.getString("name"));
                    newFunction.put("description", function.getString("description"));
                    newFunction.put("parameters", function.getJSONObject("parameters").toJSONString());

                    // 替换旧的 Function 对象
                    tool.put("function", newFunction);
                    tool.put("type", "function");
                }
            }

            /**
             * Messages 中 Contents 字段仅 hunyuan-vision 模型支持
             * hunyuan模型，识图多模态类只能放在Contents字段
             */
            if("hunyuan-vision".equals(chatCompletion.getModel())){
                // 获取所有的content字段
                JSONArray messagesArray = jsonObject.getJSONArray("messages");
                if(messagesArray!= null && !messagesArray.isEmpty()){
                    for (int i = 0; i < messagesArray.size(); i++) {
                        JSONObject message = messagesArray.getJSONObject(i);
                        // 获取当前message的content字段
                        String content = message.getString("content");
                        // 将content内容，判断是否可以转换为ChatMessage.MultiModal类型
                        if(content!=null && content.startsWith("[") && content.endsWith("]")) {
                            List<Content.MultiModal> multiModals = JSON.parseArray(content, Content.MultiModal.class);
                            if(multiModals!=null && !multiModals.isEmpty()){
                                // 将当前的content转换为contents
                                message.put("contents", multiModals);
                                // 删除原来的content key
                                message.remove("content");
                            }
                        }
                    }
                }
            }

            // 将修改后的 JSON 对象转为字符串
            requestString = jsonObject.toJSONString();
            requestString = JsonObjectUtil.toCamelCaseWithUppercaseJson(requestString);
            String authorization = BearerTokenUtils.getAuthorization(apiKey,HunyuanConstant.ChatCompletions,requestString);

            Request request = new Request.Builder()
                    .header("Authorization", authorization)
                    .header("X-TC-Action", HunyuanConstant.ChatCompletions)
                    .header("X-TC-Version", HunyuanConstant.Version)
                    .header("X-TC-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .header("Accept", Constants.SSE_CONTENT_TYPE)
                    .url(baseUrl)
                    .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), requestString))
                    .build();

            factory.newEventSource(request, convertEventSource(eventSourceListener));
            eventSourceListener.getCountDownLatch().await();

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if("tool_calls".equals(finishReason) && !toolCalls.isEmpty()){
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
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }
}
