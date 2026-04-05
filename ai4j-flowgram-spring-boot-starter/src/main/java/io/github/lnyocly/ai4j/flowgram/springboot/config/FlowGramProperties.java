package io.github.lnyocly.ai4j.flowgram.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai4j.flowgram")
public class FlowGramProperties {

    private boolean enabled = true;
    private String defaultServiceId;
    private boolean streamProgress = false;
    private Duration taskRetention = Duration.ofHours(1);
    private boolean reportNodeDetails = true;
    private boolean traceEnabled = true;
    private final ApiProperties api = new ApiProperties();
    private final TaskStoreProperties taskStore = new TaskStoreProperties();
    private final CorsProperties cors = new CorsProperties();
    private final AuthProperties auth = new AuthProperties();

    @Data
    public static class ApiProperties {
        private String basePath = "/flowgram";
    }

    @Data
    public static class TaskStoreProperties {
        private String type = "memory";
        private String tableName = "ai4j_flowgram_task";
        private boolean initializeSchema = true;
    }

    @Data
    public static class CorsProperties {
        private List<String> allowedOrigins = new ArrayList<String>();
    }

    @Data
    public static class AuthProperties {
        private boolean enabled = false;
        private String headerName = "Authorization";
    }
}
