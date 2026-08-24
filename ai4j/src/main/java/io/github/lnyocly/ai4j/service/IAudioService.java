package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.platform.openai.audio.entity.*;

import java.io.InputStream;

/**
 * @Author cly
 * @Description 音频audio接口服务
 * @Date 2024/10/10 23:39
 */
public interface IAudioService {

    InputStream textToSpeech(String baseUrl, String apiKey, TextToSpeech textToSpeech);
    InputStream textToSpeech(TextToSpeech textToSpeech);

    /**
     * response_format=url 形态：返回 JSON 中的音频下载 URL 而非音频流。
     * 零样本克隆（prompt_audio_url）走此形态。
     */
    String textToSpeechUrl(String baseUrl, String apiKey, TextToSpeech textToSpeech);
    String textToSpeechUrl(TextToSpeech textToSpeech);

    TranscriptionResponse transcription(String baseUrl, String apiKey, Transcription transcription);
    TranscriptionResponse transcription(Transcription transcription);

    TranslationResponse translation(String baseUrl, String apiKey, Translation translation);
    TranslationResponse translation(Translation translation);
}
