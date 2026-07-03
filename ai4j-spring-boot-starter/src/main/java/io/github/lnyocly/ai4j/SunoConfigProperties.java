package io.github.lnyocly.ai4j;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ChatFire Suno native API configuration.
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "ai.suno")
public class SunoConfigProperties {
    private String apiHost = "https://api.chatfire.cn/";
    private String apiKey = "";
    private String musicUrl = "suno/submit/music";
    private String lyricsUrl = "suno/submit/lyrics";
    private String fetchUrl = "suno/fetch";
}
