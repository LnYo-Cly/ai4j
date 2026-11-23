package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight pre-Task capture item in the {@code Todo -> Task -> Work} hierarchy.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TodoRecord {

    private String todoId;
    private String scopeKey;
    private String title;
    private String notes;
    private TodoStatus status;
    private String promotedTaskId;
    private HarnessActor createdBy;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public TodoRecord copy() {
        return HarnessJson.copy(this, TodoRecord.class);
    }
}
