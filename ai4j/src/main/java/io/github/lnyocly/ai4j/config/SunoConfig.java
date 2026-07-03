package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChatFire Suno native API configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SunoConfig {
    private String apiHost = "https://api.chatfire.cn/";
    private String apiKey = "";
    private String musicUrl = "suno/submit/music";
    private String lyricsUrl = "suno/submit/lyrics";
    private String fetchUrl = "suno/fetch";
}
