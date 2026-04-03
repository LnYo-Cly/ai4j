package io.github.lnyocly.ai4j.rerank.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankRequest {

    @NonNull
    private String model;

    @NonNull
    private Object query;

    @Builder.Default
    private List<RerankDocument> documents = java.util.Collections.<RerankDocument>emptyList();

    @JsonProperty("top_n")
    private Integer topN;

    @JsonProperty("return_documents")
    private Boolean returnDocuments;

    private String instruction;

    @JsonIgnore
    @Singular("extraBody")
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }
}
