package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGeneration;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGenerationResponse;
import io.github.lnyocly.ai4j.listener.ImageSseListener;

/**
 * @Author cly
 * @Description 图片生成服务接口
 * @Date 2026/1/31
 */
public interface IImageService {

    ImageGenerationResponse generate(String baseUrl, String apiKey, ImageGeneration imageGeneration) throws Exception;

    ImageGenerationResponse generate(ImageGeneration imageGeneration) throws Exception;

    void generateStream(String baseUrl, String apiKey, ImageGeneration imageGeneration, ImageSseListener listener) throws Exception;

    void generateStream(ImageGeneration imageGeneration, ImageSseListener listener) throws Exception;
}
