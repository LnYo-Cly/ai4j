package io.github.lnyocly.ai4j.document.mineru.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * MinerU API 统一响应信封：{@code {code, msg, trace_id, data}}。
 * {@code code == 0} 表示成功，{@code data} 的结构随端点不同，由调用方按类型解析。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinerUApiResponse {

    private int code;

    private String msg;

    @JsonProperty("trace_id")
    private String traceId;

    private JsonNode data;
}
