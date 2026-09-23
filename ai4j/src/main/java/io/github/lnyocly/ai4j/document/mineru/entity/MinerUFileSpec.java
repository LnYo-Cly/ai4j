package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * v4 批量接口中的单文件描述。
 *
 * <p>{@code /extract/task/batch} 使用 {@link #url}；
 * {@code /file-urls/batch} 使用 {@link #name}（强烈建议带正确后缀名）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MinerUFileSpec {

    /** 文件名（含后缀），file-urls/batch 必填。 */
    private String name;

    /** 文件 URL，extract/task/batch 必填。 */
    private String url;

    /** 是否启动 OCR，默认 false。 */
    @JsonProperty("is_ocr")
    private Boolean isOcr;

    /** 业务数据 ID 透传，≤128 字符。 */
    @JsonProperty("data_id")
    private String dataId;

    /** 页码范围，如 "2,4-6"。 */
    @JsonProperty("page_ranges")
    private String pageRanges;

    public static MinerUFileSpec ofName(String name) {
        return MinerUFileSpec.builder().name(name).build();
    }

    public static MinerUFileSpec ofUrl(String url) {
        return MinerUFileSpec.builder().url(url).build();
    }
}
