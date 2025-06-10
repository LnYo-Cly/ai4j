package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaichuanConfig {

    private String apiHost = "https://api.baichuan-ai.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
}
