package io.github.lnyocly.ai4j.platform.openai.chat.entity;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import lombok.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @Author cly
 * @Description ChatCompletion 实体类
 * @Date 2024/8/3 18:00
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletion {

    /**
     * 对话模型
     */
    @NonNull
    private String model;

    /**
     * 消息内容
     */
    @NonNull
    @Singular
    private List<ChatMessage> messages;

    /**
     * 流式输出
     */
    @Builder.Default
    private Boolean stream = false;

    @JsonProperty("frequency_penalty")
    private Float frequencyPenalty = 0f;

    private Float temperature = 1f;

    @JsonProperty("top_p")
    private Float topP = 1f;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<Tool> tools;

    /**
     * 辅助属性
     */
    @JsonIgnore
    private List<String> functions;

    @JsonProperty("tool_choice")
    private String toolChoice;

    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls = true;

    @JsonProperty("response_format")
    private Object responseFormat;

    private String user;

    private Integer n = 1;

    private String stop;

    @JsonProperty("logit_bias")
    private Map logitBias;

    private Boolean logprobs = false;

    @JsonProperty("top_logprobs")
    private Integer topLogprobs;


    public static class ChatCompletionBuilder {
        private List<String> functions;

        public ChatCompletion.ChatCompletionBuilder functions(String... functions){
            if (this.functions == null) {
                this.functions = new ArrayList<>();
            }
            this.functions.addAll(Arrays.asList(functions));
            return this;
        }

        public ChatCompletion.ChatCompletionBuilder functions(List<String> functions){
            if (this.functions == null) {
                this.functions = new ArrayList<>();
            }
            this.functions.addAll(functions);
            return this;
        }


    }
}
