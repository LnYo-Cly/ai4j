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

    /**
     * 图片编辑（图生图）：POST /v1/images/edits。请求体复用 ImageGeneration，
     * 参考图通过 image 字段传入（URL 或 base64 字符串数组，平台扩展 JSON 形态）。
     */
    default ImageGenerationResponse edit(String baseUrl, String apiKey, ImageGeneration imageGeneration) throws Exception {
        throw new UnsupportedOperationException("image edit is not supported by this platform service");
    }

    ImageGenerationResponse generate(ImageGeneration imageGeneration) throws Exception;

    void generateStream(String baseUrl, String apiKey, ImageGeneration imageGeneration, ImageSseListener listener) throws Exception;

    void generateStream(ImageGeneration imageGeneration, ImageSseListener listener) throws Exception;
}
