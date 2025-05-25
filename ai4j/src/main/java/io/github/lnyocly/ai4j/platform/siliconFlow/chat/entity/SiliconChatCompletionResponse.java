package io.github.lnyocly.ai4j.platform.siliconFlow.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor()
@AllArgsConstructor()
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiliconChatCompletionResponse {
    // 对话的唯一标识符
    private String id;

    private List<SiliconChoice> choices;

    private SiliconUsage usage;

    private Long created;

    private String model;

    private String object;
}

