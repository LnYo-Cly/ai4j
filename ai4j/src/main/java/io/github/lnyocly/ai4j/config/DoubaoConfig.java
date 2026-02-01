package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 璞嗗寘(鐏北寮曟搸鏂硅垷) 閰嶇疆鏂囦欢
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoubaoConfig {

    private String apiHost = "https://ark.cn-beijing.volces.com/api/v3/";
    private String apiKey = "";
    private String chatCompletionUrl = "chat/completions";
    private String imageGenerationUrl = "images/generations";
    private String responsesUrl = "responses";
}

