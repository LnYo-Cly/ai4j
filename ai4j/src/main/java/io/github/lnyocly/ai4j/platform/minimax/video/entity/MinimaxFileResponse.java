package io.github.lnyocly.ai4j.platform.minimax.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET v1/files/retrieve?file_id=xxx 响应，成功任务经此换取 download_url。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinimaxFileResponse {
    private MinimaxFile file;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MinimaxFile {
        /** 轮询响应里是字符串，本接口返回数字，Jackson 均可反序列化为 String。 */
        @JsonProperty("file_id")
        private String fileId;

        @JsonProperty("download_url")
        private String downloadUrl;

        private String filename;
        private Long bytes;
        private String purpose;
    }
}
