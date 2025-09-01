package io.github.lnyocly.ai4j.platform.dashscope;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.alibaba.dashscope.aigc.generation.*;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResponseFormat;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.tools.*;
import com.alibaba.dashscope.utils.JsonUtils;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DashScopeConfig;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.dashscope.entity.DashScopeResult;
import io.github.lnyocly.ai4j.platform.dashscope.util.MessageUtil;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.*;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author cly
 * @Description OpenAi 聊天服务
 * @Date 2024/8/2 23:16
 */
@Slf4j
public class DashScopeChatService implements IChatService, ParameterConvert<GenerationParam>, ResultConvert<DashScopeResult> {

    private final DashScopeConfig dashScopeConfig;

    // 创建一个空的 EventSource 对象，用于占位
    private final EventSource eventSource = new EventSource() {

        @NotNull
        @Override
        public Request request() {
            return null;
        }

        @Override
        public void cancel() {

        }
    };

    public DashScopeChatService(Configuration configuration) {
        this.dashScopeConfig = configuration.getDashScopeConfig();
    }

    public DashScopeChatService(Configuration configuration, DashScopeConfig dashScopeConfig) {
        this.dashScopeConfig = dashScopeConfig;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if (apiKey == null || "".equals(apiKey)) apiKey = dashScopeConfig.getApiKey();
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

        // 总token消耗
        Usage allUsage = new Usage();
        String finishReason = "first";

        while ("first".equals(finishReason) || "tool_calls".equals(finishReason)) {

            finishReason = null;

            Generation gen = new Generation();
            GenerationParam param = convertChatCompletionObject(chatCompletion);
            param.setApiKey(apiKey);
            GenerationResult result = gen.call(param);

            DashScopeResult dashScopeResult = new DashScopeResult();
            dashScopeResult.setGenerationResult(result);
            dashScopeResult.setObject("chat.completion");
            dashScopeResult.setModel(chatCompletion.getModel());
            dashScopeResult.setCreated(System.currentTimeMillis() / 1000);

            GenerationOutput.Choice choice = result.getOutput().getChoices().get(0);
            finishReason = choice.getFinishReason();

            GenerationUsage usage = result.getUsage();
            allUsage.setCompletionTokens(allUsage.getCompletionTokens() + usage.getOutputTokens());
            allUsage.setTotalTokens(allUsage.getTotalTokens() + usage.getTotalTokens());
            allUsage.setPromptTokens(allUsage.getPromptTokens() + usage.getInputTokens());

            // 判断是否为函数调用返回
            if ("tool_calls".equals(finishReason)) {
                Message message = choice.getMessage();
                List<ToolCallBase> toolCalls = message.getToolCalls();

                List<ChatMessage> messages = new ArrayList<>(chatCompletion.getMessages());
                messages.add(MessageUtil.convert(message));

                // 添加 tool 消息
                for (ToolCallBase toolCall : toolCalls) {
                    if (toolCall.getType().equals("function")) {
                        String functionName = ((ToolCallFunction)toolCall).getFunction().getName();
                        String arguments = ((ToolCallFunction) toolCall).getFunction().getArguments();
                        String functionResponse = ToolUtil.invoke(functionName, arguments);
                        messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
                    }
                }
                chatCompletion.setMessages(messages);

            } else {
                ChatCompletionResponse chatCompletionResponse = convertChatCompletionResponse(dashScopeResult);
                // 其他情况直接返回
                chatCompletionResponse.setUsage(allUsage);

                return chatCompletionResponse;

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
        if (apiKey == null || "".equals(apiKey)) apiKey = dashScopeConfig.getApiKey();
        chatCompletion.setStream(true);
        StreamOptions streamOptions = chatCompletion.getStreamOptions();
        if (streamOptions == null) {
            chatCompletion.setStreamOptions(new StreamOptions(true));
        }

        if ((chatCompletion.getFunctions() != null && !chatCompletion.getFunctions().isEmpty()) || (chatCompletion.getMcpServices() != null && !chatCompletion.getMcpServices().isEmpty())) {
            //List<Tool> tools = ToolUtil.getAllFunctionTools(chatCompletion.getFunctions());
            List<Tool> tools = ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices());


            chatCompletion.setTools(tools);
            if (tools == null) {
                chatCompletion.setParallelToolCalls(null);
            }
        }

        if (chatCompletion.getTools() != null && !chatCompletion.getTools().isEmpty()) {

        } else {
            chatCompletion.setParallelToolCalls(null);
        }

        String finishReason = "first";

        while ("first".equals(finishReason) || "tool_calls".equals(finishReason)) {

            finishReason = null;
            eventSourceListener.setFinishReason(null);
            Generation gen = new Generation();
            GenerationParam param = convertChatCompletionObject(chatCompletion);
            param.setApiKey(apiKey);
            gen.streamCall(param, new ResultCallback<GenerationResult>() {
                @SneakyThrows
                @Override
                public void onEvent(GenerationResult message) {
                    log.info("{}", JSON.toJSONString(message));
                    DashScopeResult dashScopeResult = new DashScopeResult();
                    dashScopeResult.setGenerationResult(message);
                    dashScopeResult.setObject("chat.completion.chunk");
                    dashScopeResult.setCreated(System.currentTimeMillis() / 1000);
                    dashScopeResult.setModel(chatCompletion.getModel());
                    ObjectMapper objectMapper = new ObjectMapper();
                    eventSourceListener.onEvent(eventSource, message.getRequestId(), null, objectMapper.writeValueAsString(convertChatCompletionResponse(dashScopeResult)));
                }

                @Override
                public void onComplete() {
                    eventSourceListener.onClosed(eventSource);
                }

                @Override
                public void onError(Exception e) {
                    eventSourceListener.onFailure(eventSource, e, null);
                }
            });

            eventSourceListener.getCountDownLatch().await();

            finishReason = eventSourceListener.getFinishReason();
            List<ToolCall> toolCalls = eventSourceListener.getToolCalls();

            // 需要调用函数
            if ("tool_calls".equals(finishReason) && !toolCalls.isEmpty()) {
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

    @Override
    public GenerationParam convertChatCompletionObject(ChatCompletion chatCompletion) {
        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder();
        builder.model(chatCompletion.getModel())
                .messages(MessageUtil.convertToMessage(chatCompletion.getMessages()))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .temperature(chatCompletion.getTemperature())
                .topP(Double.valueOf(chatCompletion.getTopP()))
                .maxTokens(chatCompletion.getMaxCompletionTokens())
                .toolChoice(chatCompletion.getToolChoice())
                .parameters(chatCompletion.getParameters());
        if (ObjUtil.isNotNull(chatCompletion.getResponseFormat()) && String.valueOf(chatCompletion.getResponseFormat()).contains("json_object")) {
            builder.responseFormat(ResponseFormat.from(ResponseFormat.JSON_OBJECT));
        }

        if (CollUtil.isNotEmpty(chatCompletion.getTools())) {
            List<ToolBase> toolBaseList = chatCompletion.getTools().stream().map(tool -> {
                        if ("function".equals(tool.getType())) {
                            Tool.Function function = tool.getFunction();
                            return ToolFunction.builder().function(FunctionDefinition.builder().name(function.getName()).description(function.getDescription()).parameters(JsonUtils.parse(JsonUtils.toJson(function.getParameters()))).build()).build();
                        }
                        return null;
                    }
            ).filter(ObjUtil::isNotNull).collect(Collectors.toList());
            builder.tools(toolBaseList);
        }
        return builder.build();
    }

    @Override
    public EventSourceListener convertEventSource(SseListener eventSourceListener) {
        return null;
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(DashScopeResult dashScopeResult) {
        GenerationResult generationResult = dashScopeResult.getGenerationResult();
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(generationResult.getRequestId());
        chatCompletionResponse.setCreated(dashScopeResult.getCreated());
        chatCompletionResponse.setObject(dashScopeResult.getObject());
        GenerationOutput.Choice srcChoice = generationResult.getOutput().getChoices().get(0);

        Choice choice = new Choice();
        ChatMessage chatMessage = MessageUtil.convert(srcChoice.getMessage());
        choice.setMessage(chatMessage);
        choice.setDelta(chatMessage);
        choice.setIndex(srcChoice.getIndex());
        choice.setFinishReason(srcChoice.getFinishReason());
        chatCompletionResponse.setChoices(CollUtil.newArrayList(choice));
        GenerationUsage usage = generationResult.getUsage();
        chatCompletionResponse.setUsage(new Usage(usage.getInputTokens(), usage.getOutputTokens(), usage.getTotalTokens()));
        return chatCompletionResponse;
    }
}