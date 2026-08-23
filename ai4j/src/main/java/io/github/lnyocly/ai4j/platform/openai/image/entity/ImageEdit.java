package io.github.lnyocly.ai4j.platform.openai.image.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Singular;

import java.util.List;

/**
 * 图片编辑请求（multipart/form-data 形态，OpenAI 官方标准）。
 *
 * <p>与 JSON 形态（{@link ImageGeneration} + image 字段数组，走网关通用扩展）相对：
 * multipart 直接以文件字节上传参考图，不依赖网关回源拉 URL，是 OpenAI
 * 官方文档的示例形态（官方未声明 Content-Type 限制，社区实测 multipart 稳定可用）。两者共用 {@code POST /v1/images/edits} 端点，
 * 由 {@code Content-Type} 区分。
 *
 * <p>multipart 下多张参考图 = 同名 {@code image} 字段重复。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ImageEdit {

    /** 模型 ID，例如 gpt-image-2。 */
    @NonNull
    private String model;

    /** 提示词（必填）。 */
    @NonNull
    private String prompt;

    /** 输出数量（1~10）。 */
    private Integer n;

    /** 输出尺寸，例如 1024x1024 / 1536x1024 / 1024x1536。 */
    private String size;

    /** 输出质量 low / medium / high。 */
    private String quality;

    /** 输出格式 png（默认）/ jpeg / webp（GPT image 系专属）。 */
    private String outputFormat;

    /** 返回格式 url / b64_json。 */
    private String responseFormat;

    /** 参考图（一张或多张；multipart 下同名 image 字段重复）。 */
    @Singular("image")
    private List<ImagePart> images;

    /** 可选 PNG mask（alpha 透明区 = 编辑处），尺寸须与首张参考图一致。 */
    private ImagePart mask;

    /** multipart 文件部件。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImagePart {
        private byte[] data;
        private String filename;
        private String contentType;

        public static ImagePart of(byte[] data, String filename, String contentType) {
            return new ImagePart(data, filename, contentType);
        }
    }
}
