package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;

import java.io.InputStream;

/**
 * OpenAI-compatible video generation service.
 */
public interface IVideoService {

    VideoResponse create(String baseUrl, String apiKey, VideoCreateRequest request) throws Exception;

    VideoResponse create(VideoCreateRequest request) throws Exception;

    VideoResponse retrieve(String baseUrl, String apiKey, String id) throws Exception;

    VideoResponse retrieve(String id) throws Exception;

    InputStream content(String baseUrl, String apiKey, String id) throws Exception;

    InputStream content(String id) throws Exception;

    VideoResponse remix(String baseUrl, String apiKey, String id, String prompt) throws Exception;

    VideoResponse remix(String id, String prompt) throws Exception;
}
