package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessEscalateSpec {

    private String escalationId;
    private String scopeKey;
    private String taskId;
    private String executionId;
    private String targetActorId;
    private String question;
    private String recommend;
    private String reason;
    @Builder.Default
    private boolean createWait = true;

    @Builder.Default
    private List<String> options = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
