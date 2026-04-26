package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.StreamOptions;
import lombok.*;

import java.util.ArrayList;
import java.util.Arrays;
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

    @JsonIgnore
    private List<String> functions;

    @JsonIgnore
    private List<String> mcpServices;

    @JsonProperty("top_p")
    private Double topP;

    private String truncation;

    private String user;

    
    @JsonIgnore
    @Singular("extraBody")
    private Map<String, Object> extraBody;

    @JsonIgnore
    private StreamExecutionOptions streamExecution;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }

    public List<String> getFunctions() {
        return functions;
    }

    public void setFunctions(List<String> functions) {
        this.functions = functions;
    }

    public List<String> getMcpServices() {
        return mcpServices;
    }

    public void setMcpServices(List<String> mcpServices) {
        this.mcpServices = mcpServices;
    }

    public static class ResponseRequestBuilder {
        private List<String> functions;
        private List<String> mcpServices;

        public ResponseRequestBuilder functions(String... functions) {
            if (this.functions == null) {
                this.functions = new ArrayList<String>();
            }
            this.functions.addAll(Arrays.asList(functions));
            return this;
        }

        public ResponseRequestBuilder functions(List<String> functions) {
            if (this.functions == null) {
                this.functions = new ArrayList<String>();
            }
            if (functions != null) {
                this.functions.addAll(functions);
            }
            return this;
        }

        public ResponseRequestBuilder mcpServices(String... mcpServices) {
            if (this.mcpServices == null) {
                this.mcpServices = new ArrayList<String>();
            }
            this.mcpServices.addAll(Arrays.asList(mcpServices));
            return this;
        }

        public ResponseRequestBuilder mcpServices(List<String> mcpServices) {
            if (this.mcpServices == null) {
                this.mcpServices = new ArrayList<String>();
            }
            if (mcpServices != null) {
                this.mcpServices.addAll(mcpServices);
            }
            return this;
        }

        public ResponseRequestBuilder mcpService(String mcpService) {
            if (this.mcpServices == null) {
                this.mcpServices = new ArrayList<String>();
            }
            this.mcpServices.add(mcpService);
            return this;
        }

        public ResponseRequestBuilder toolRegistry(List<String> functions, List<String> mcpServices) {
            functions(functions);
            mcpServices(mcpServices);
            return this;
        }
    }
}

