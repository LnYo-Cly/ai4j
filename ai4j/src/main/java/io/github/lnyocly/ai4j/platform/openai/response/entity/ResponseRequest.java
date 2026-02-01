package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.StreamOptions;
import lombok.*;

import java.util.List;
import java.util.Map;


@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseRequest {

    @NonNull
    private String model;

    
    private Object input;

    
    private List<String> include;

    
    private String instructions;

    @JsonProperty("previous_response_id")
    private String previousResponseId;

    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    private Map<String, Object> metadata;

    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    
    private Object reasoning;

    private Boolean store;

    private Boolean stream;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    private Double temperature;

    
    private Object text;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    private List<Object> tools;

    @JsonProperty("top_p")
    private Double topP;

    private String truncation;

    private String user;

    
    @JsonIgnore
    @Singular("extraBody")
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }
}

