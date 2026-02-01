package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseItem {

    private String id;

    private String type;

    private String role;

    private String status;

    private Boolean partial;

    private List<ResponseContentPart> content;

    @JsonProperty("call_id")
    private String callId;

    private String name;

    private String arguments;

    private String output;

    @JsonProperty("server_label")
    private String serverLabel;

    private List<ResponseSummary> summary;

    @JsonIgnore
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }
}

