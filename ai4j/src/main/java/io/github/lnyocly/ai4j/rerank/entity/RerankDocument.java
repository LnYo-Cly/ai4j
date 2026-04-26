package io.github.lnyocly.ai4j.rerank.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankDocument {

    private String id;

    private String text;

    private String content;

    private String title;

    private Object image;

    private Map<String, Object> metadata;
}
