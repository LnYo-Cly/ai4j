package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoFetchResponse;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoLyricsRequest;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoMusicRequest;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoSubmitResponse;

/**
 * Suno music generation service.
 */
public interface IMusicService {

    SunoSubmitResponse submitMusic(String baseUrl, String apiKey, SunoMusicRequest request) throws Exception;

    SunoSubmitResponse submitMusic(SunoMusicRequest request) throws Exception;

    SunoSubmitResponse submitLyrics(String baseUrl, String apiKey, SunoLyricsRequest request) throws Exception;

    SunoSubmitResponse submitLyrics(SunoLyricsRequest request) throws Exception;

    SunoFetchResponse fetch(String baseUrl, String apiKey, String taskId) throws Exception;

    SunoFetchResponse fetch(String taskId) throws Exception;
}
