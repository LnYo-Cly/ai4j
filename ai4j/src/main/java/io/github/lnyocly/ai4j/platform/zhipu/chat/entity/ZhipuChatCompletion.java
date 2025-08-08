package io.github.lnyocly.ai4j.platform.zhipu.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author cly
 * @Description 智谱对话实体类
 * @Date 2024/8/27 17:39
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZhipuChatCompletion {

    @NonNull
    private String model;
    @NonNull
    private List<ChatMessage> messages;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("do_sample")
    private Boolean doSample = true;
    private Boolean stream = false;
    /**
     * 采样温度，控制输出的随机性，必须为正数。值越大，会使输出更随机
     * [0.0, 1.0]
     */
    private Float temperature = 0.95f;
    /**
     * 核取样
     * [0.0, 1.0]
     */
    @JsonProperty("top_p")
    private Float topP = 0.7f;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<String> stop;


    private List<Tool> tools;

    /**
     * 辅助属性
     */
    @JsonIgnore
    private List<String> functions;

    @JsonProperty("tool_choice")
    private String toolChoice;

    @JsonProperty("user_id")
    private String userId;

    public static class ZhipuChatCompletionBuilder {
        private List<String> functions;

        public ZhipuChatCompletion.ZhipuChatCompletionBuilder functions(String... functions){
            if (this.functions == null) {
                this.functions = new ArrayList<>();
            }
            this.functions.addAll(Arrays.asList(functions));
            return this;
        }

        public ZhipuChatCompletion.ZhipuChatCompletionBuilder functions(List<String> functions){
            if (this.functions == null) {
                this.functions = new ArrayList<>();
            }
            if (functions != null) {
                this.functions.addAll(functions);
            }
            return this;
        }


    }
}
