package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiliconFlowConfig {
    private String apiHost = "https://api.siliconflow.cn/v1/chat/completions";
    private String apiKey = "Bearer sk-fpfnhowfnjnpmwjypbqxqtcdbmtogybhfsutoibfuhrgwdrr";
    private String model = "deepseek-ai/DeepSeek-V3";
}
